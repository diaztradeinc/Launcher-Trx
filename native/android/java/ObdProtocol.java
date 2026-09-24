package com.diaztradeinc.trxlauncher;

/** Pure ELM response parsing; never join bytes from different ECU response lines. */
public final class ObdProtocol {
    private ObdProtocol() {}
    public static int[] bytes(String response,String pid,int count){
        if(response==null)return null;
        String marker="41"+pid.toUpperCase(java.util.Locale.US);
        for(String raw:response.toUpperCase(java.util.Locale.US).split("[\\r\\n>]+")){
            String line=raw.replaceAll("\\s+","");
            if(!line.matches("[0-9A-F]+"))continue;
            int start=line.indexOf(marker);
            if(start<0 || line.length()<start+4+count*2)continue;
            // Headerless (ATH0), or a CAN header and length before the service byte.
            if(start!=0 && start!=5 && start!=10)continue;
            int[] out=new int[count];
            try{for(int i=0;i<count;i++)out[i]=Integer.parseInt(line.substring(start+4+i*2,start+6+i*2),16);return out;}
            catch(NumberFormatException ignored){}
        }
        return null;
    }
    public static long mask(String response,String base){int[] b=bytes(response,base,4);return b==null?-1:((long)b[0]<<24)|((long)b[1]<<16)|((long)b[2]<<8)|b[3];}
    public static boolean supported(long mask,int offset){return mask<0 || (offset>=1&&offset<=32&&(mask & (1L<<(32-offset)))!=0);}
    public static boolean complete(String response){return response!=null&&response.indexOf('>')>=0;}
}
