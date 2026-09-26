package com.diaztradeinc.trxlauncher;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only OBD-II bridge for a paired OBDLink MX+.
 * It only sends adapter setup commands and SAE Mode 01 live-data queries.
 */
public final class ObdBridge {
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static volatile boolean running;
    private static volatile Thread worker;
    private static volatile BluetoothSocket socket;
    private static volatile InputStream input;
    private static volatile OutputStream output;

    public static volatile boolean connected;
    public static volatile boolean ecuConnected;
    public static volatile String status="PAIR OBDLINK MX+";
    public static volatile String lastError="";
    public static volatile int reconnectAttempts;
    public static volatile String deviceName="OBDLink MX+";
    public static volatile String protocol="--";
    public static volatile int livePidCount;
    public static volatile long lastUpdate;
    private static long lastProbe;
    private static long[] supportedMasks={-1,-1,-1};
    private static final java.util.ArrayDeque<String> trace=new java.util.ArrayDeque<>();
    public static synchronized String diagnostics(){return String.join("\n",trace);}
    private static synchronized void record(String command,String response){
        String clean=response.replaceAll("[\r\n]+"," | ").replaceAll("[^A-Za-z0-9 .|>:?_-]","");
        trace.addLast(command+" → "+clean.substring(0,Math.min(180,clean.length())));
        while(trace.size()>24)trace.removeFirst();
    }

    public static volatile float rpm=Float.NaN;
    public static volatile float coolantF=Float.NaN;
    public static volatile float intakeF=Float.NaN;
    public static volatile float engineLoad=Float.NaN;
    public static volatile float batteryV=Float.NaN;
    public static volatile float obdSpeedMph=Float.NaN;
    public static volatile float boostPsi=Float.NaN;
    public static volatile float transmissionF=Float.NaN;
    public static volatile float throttle=Float.NaN;
    public static volatile float fuelLevel=Float.NaN;
    public static volatile float mafGps=Float.NaN;

    private ObdBridge(){}

    public static void reconnect(Context context){connected=false;ecuConnected=false;status="RECONNECTING ADAPTER";closeSocket();start(context);}

    public static synchronized void start(Context context){
        if(running)return;
        final Context app=context.getApplicationContext();
        running=true;
        worker=new Thread(()->runLoop(app),"trx-obdlink");
        worker.start();
    }

    public static synchronized void stop(){
        running=false;
        closeSocket();
        Thread active=worker;
        if(active!=null)active.interrupt();
        worker=null;
        connected=false;
        status="OBD DISCONNECTED";
    }

    private static void runLoop(Context context){
        while(running){
            try{
                if(!context.getSharedPreferences("launcher",Context.MODE_PRIVATE).getBoolean("obd_enabled",true)){status="OBD DISCONNECTED";sleep(1000);continue;}
                if(Build.VERSION.SDK_INT>=31&&context.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
                    connected=false;status="BLUETOOTH PERMISSION REQUIRED";sleep(4000);continue;
                }
                BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
                if(adapter==null){status="BLUETOOTH UNAVAILABLE";sleep(5000);continue;}
                if(!adapter.isEnabled()){status="TURN ON BLUETOOTH";sleep(3500);continue;}

                BluetoothDevice target=null;
                String selected=context.getSharedPreferences("launcher",Context.MODE_PRIVATE).getString("obd_address","");
                if(selected.isEmpty())target=findMx(adapter.getBondedDevices());
                else for(BluetoothDevice device:adapter.getBondedDevices())if(selected.equals(device.getAddress())){target=device;break;}
                if(target==null){status="PAIR OBDLINK MX+";sleep(4000);continue;}
                deviceName=safeName(target);
                status="CONNECTING "+deviceName.toUpperCase(Locale.US)+"…";
                // Only connect to a paired adapter; do not require scan permission.
                BluetoothSocket next=connectSocket(adapter,target);
                input=next.getInputStream();
                output=next.getOutputStream();
                connected=true;
                lastError="";reconnectAttempts=0;
                status="ADAPTER CONNECTED • CHECKING ENGINE ECU";
                initializeAdapter();
                status=ecuConnected ? "OBD LIVE • "+protocol : "ADAPTER LIVE • START ENGINE";
                while(running&&next.isConnected()){
                    pollStandardPids();
                    lastUpdate=SystemClock.elapsedRealtime();
                    sleep(90);
                }
            }catch(SecurityException denied){
                status="BLUETOOTH PERMISSION REQUIRED";
                lastError="Bluetooth permission required";
            }catch(Throwable error){
                reconnectAttempts++;
                lastError=error.getClass().getSimpleName()+": "+(error.getMessage()==null?"No details":error.getMessage());
                status="OBD RECONNECTING • "+lastError.substring(0,Math.min(42,lastError.length()));
            }finally{
                connected=false;
                ecuConnected=false;livePidCount=0;protocol="--";lastUpdate=0;
                rpm=coolantF=intakeF=engineLoad=batteryV=obdSpeedMph=boostPsi=transmissionF=throttle=fuelLevel=mafGps=Float.NaN;
                closeSocket();
            }
            if(running)sleep(Math.min(15000,3000+Math.max(0,reconnectAttempts-2)*1000));
        }
    }

    private static BluetoothSocket connectSocket(BluetoothAdapter adapter,BluetoothDevice target) throws Exception {
        // Discovery can delay RFCOMM negotiation. Android 12+ requires a separate scan
        // permission to cancel it; connecting to a bonded device does not.
        try {
            if(Build.VERSION.SDK_INT<31)adapter.cancelDiscovery();
        } catch(SecurityException ignored){}
        Exception secureFailure=null;
        for(int mode=0;mode<2;mode++){
            if(!running)throw new java.io.IOException("Connection stopped");
            String label=mode==0?"secure":"insecure";
            status="CONNECTING "+deviceName.toUpperCase(Locale.US)+" • "+label.toUpperCase(Locale.US);
            BluetoothSocket attempt=null;
            try {
                attempt=mode==0?target.createRfcommSocketToServiceRecord(SPP)
                    :target.createInsecureRfcommSocketToServiceRecord(SPP);
                socket=attempt;
                BluetoothSocket pending=attempt;
                java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean(false);
                Thread watchdog=new Thread(()->{
                    try{Thread.sleep(12000);}catch(InterruptedException ignored){return;}
                    if(!finished.get())try{pending.close();}catch(Exception ignored){}
                },"trx-obd-"+label+"-timeout");
                watchdog.setDaemon(true);watchdog.start();
                try {attempt.connect();} finally {finished.set(true);watchdog.interrupt();}
                if(!running)throw new java.io.IOException("Connection stopped");
                record("RFCOMM",label+" connected");
                return attempt;
            } catch(Exception error){
                if(attempt!=null)try{attempt.close();}catch(Exception ignored){}
                socket=null;
                record("RFCOMM "+label,error.getClass().getSimpleName()+": "+error.getMessage());
                if(mode==0)secureFailure=error;
                else throw new java.io.IOException("Secure: "+brief(secureFailure)+"; insecure: "+brief(error),error);
            }
        }
        throw new java.io.IOException("RFCOMM connection unavailable");
    }

    private static String brief(Exception error){
        if(error==null)return "unknown";
        String detail=error.getMessage();
        return error.getClass().getSimpleName()+(detail==null?"":": "+detail);
    }

    private static BluetoothDevice findMx(Set<BluetoothDevice> bonded){
        if(bonded==null)return null;
        BluetoothDevice fallback=null;
        for(BluetoothDevice device:bonded){
            String name=safeName(device).toUpperCase(Locale.US);
            if(name.contains("OBDLINK")&&(name.contains("MX")||name.contains("STN")))return device;
            if(name.contains("MX+"))return device;
        }
        return null;
    }

    private static String safeName(BluetoothDevice device){
        try{
            String name=device.getName();
            return name==null||name.trim().isEmpty()?"OBDLink MX+":name.trim();
        }catch(SecurityException denied){return "OBDLink MX+";}
    }

    private static void initializeAdapter() throws Exception{
        command("ATZ",2600);
        command("ATE0",1000);
        command("ATL0",1000);
        command("ATS0",1000);
        command("ATH0",1000);
        command("ATAT2",1000);
        command("ATST64",1000);
        command("ATCAF1",1000);
        command("ATSP0",2200);
        String supported=command("0100",20000);
        if(!hasModeOneReply(supported)){
            command("ATSP6",1800);command("ATSH7DF",1000);supported=command("0100",5000);
        }
        ecuConnected=hasModeOneReply(supported);protocol=cleanProtocol(command("ATDP",1500));
        supportedMasks=new long[]{ObdProtocol.mask(supported,"00"),-1,-1};
        if(ecuConnected){
            if(ObdProtocol.supported(supportedMasks[0],32))supportedMasks[1]=ObdProtocol.mask(command("0120",2000),"20");
            else supportedMasks[1]=0;
            if(ObdProtocol.supported(supportedMasks[1],32))supportedMasks[2]=ObdProtocol.mask(command("0140",2000),"40");
            else supportedMasks[2]=0;
        }
        lastProbe=SystemClock.elapsedRealtime();
    }

    private static void pollStandardPids() throws Exception{
        float[] data;
        livePidCount=0;
        if(!ecuConnected && SystemClock.elapsedRealtime()-lastProbe>15000){
            status="SEARCHING FOR ENGINE ECU";initializeAdapter();
        }

        data=pid("0C",2);
        rpm=data==null?Float.NaN:(data[0]*256f+data[1])/4f;if(data!=null)livePidCount++;

        data=pid("05",1);
        coolantF=data==null?Float.NaN:toF(data[0]-40f);if(data!=null)livePidCount++;

        data=pid("04",1);
        engineLoad=data==null?Float.NaN:data[0]*100f/255f;if(data!=null)livePidCount++;

        data=pid("0F",1);
        intakeF=data==null?Float.NaN:toF(data[0]-40f);if(data!=null)livePidCount++;

        data=pid("0D",1);
        obdSpeedMph=data==null?Float.NaN:data[0]*0.621371f;if(data!=null)livePidCount++;

        data=pid("11",1);throttle=data==null?Float.NaN:data[0]*100f/255f;if(data!=null)livePidCount++;
        data=pid("2F",1);fuelLevel=data==null?Float.NaN:data[0]*100f/255f;if(data!=null)livePidCount++;
        data=pid("10",2);mafGps=data==null?Float.NaN:(data[0]*256f+data[1])/100f;if(data!=null)livePidCount++;

        data=pid("42",2);
        if(data!=null){batteryV=(data[0]*256f+data[1])/1000f;livePidCount++;}
        else{
            batteryV=Float.NaN;
            String voltage=command("ATRV",1000).replaceAll("[^0-9.]","");
            try{if(!voltage.isEmpty())batteryV=Float.parseFloat(voltage);}catch(Throwable ignored){}
        }

        float map=Float.NaN,baro=Float.NaN;
        data=pid("0B",1);if(data!=null)map=data[0];
        data=pid("33",1);if(data!=null)baro=data[0];
        boostPsi=Float.NaN;
        if(!Float.isNaN(map)&&!Float.isNaN(baro)&&baro>0){
            boostPsi=Math.max(0,(map-baro)*0.1450377f);
            livePidCount++;
        }
        // Transmission temperature is manufacturer-specific on this vehicle.
        // Leave it unsupported until a verified read-only RAM PID is available.
        transmissionF=Float.NaN;
        ecuConnected=livePidCount>0;
        status=ecuConnected ? "OBD LIVE • "+livePidCount+" PIDS • "+protocol : "ADAPTER LIVE • ECU NO DATA";
    }

    private static boolean hasModeOneReply(String value){return ObdProtocol.bytes(value,"00",4)!=null;}
    private static String cleanProtocol(String value){if(value==null)return "CAN";String clean=value.replace(">","").replace("ATDP","").replaceAll("[\\r\\n]+"," ").trim();return clean.isEmpty()?"CAN":clean.toUpperCase(Locale.US);}

    private static float[] pid(String code,int count) throws Exception{
        int id=Integer.parseInt(code,16);int group=(id-1)/32;
        if(group<supportedMasks.length&&!ObdProtocol.supported(supportedMasks[group],id-group*32))return null;
        String response=command("01"+code,2500);
        int[] bytes=ObdProtocol.bytes(response,code,count);if(bytes==null)return null;
        float[] values=new float[count];for(int i=0;i<count;i++)values[i]=bytes[i];return values;
    }

    private static String command(String value,long timeout) throws Exception{
        InputStream in=input;OutputStream out=output;
        if(in==null||out==null)throw new IllegalStateException("OBD socket closed");
        while(in.available()>0)in.read();
        out.write((value+"\r").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        StringBuilder result=new StringBuilder();
        long deadline=SystemClock.elapsedRealtime()+timeout;
        byte[] buffer=new byte[256];
        while(running&&SystemClock.elapsedRealtime()<deadline){
            int available=in.available();
            if(available>0){
                int read=in.read(buffer,0,Math.min(buffer.length,available));
                if(read>0){
                    result.append(new String(buffer,0,read,StandardCharsets.US_ASCII));
                    if(result.indexOf(">")>=0)break;
                }
            }else sleep(12);
        }
        String response=result.toString();record(value,response);
        // A partial SEARCHING reply is not completion. Reconnect rather than sending
        // another command into an active protocol search (which produces STOPPED).
        if(!ObdProtocol.complete(response))throw new java.io.IOException("OBD response incomplete: "+value);
        return response;
    }

    private static float toF(float celsius){return celsius*9f/5f+32f;}

    private static void closeSocket(){
        try{if(input!=null)input.close();}catch(Throwable ignored){}
        try{if(output!=null)output.close();}catch(Throwable ignored){}
        try{if(socket!=null)socket.close();}catch(Throwable ignored){}
        input=null;output=null;socket=null;
    }

    private static void sleep(long millis){
        try{Thread.sleep(millis);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
    }
}
