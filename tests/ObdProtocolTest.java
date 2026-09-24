import com.diaztradeinc.trxlauncher.ObdProtocol;
public class ObdProtocolTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        int[] rpm=ObdProtocol.bytes("010C\rSEARCHING...\r41 0C 1A F8\r>","0C",2);
        check(rpm!=null&&(rpm[0]*256+rpm[1])/4==1726,"RPM response with echo/status");
        check(ObdProtocol.bytes("7E8 04 41 0C 1A F8\r>","0C",2)!=null,"CAN header");
        check(ObdProtocol.bytes("41 0C 1A\r41 05 80\r>","0C",2)==null,"Must not combine ECU lines");
        check(ObdProtocol.bytes("NO DATA\r>","0C",2)==null,"No data");
        check(!ObdProtocol.complete("SEARCHING..."),"Prompt required");
        check(ObdProtocol.complete("STOPPED\r>"),"Stopped is complete but not telemetry");
        check(ObdProtocol.bytes("STOPPED\r>","0C",2)==null,"Stopped is not data");
        check(ObdProtocol.supported(0x80000001L,1),"First support bit");
        check(ObdProtocol.supported(0x80000001L,32),"Next support page");
        check(!ObdProtocol.supported(0x80000001L,12),"Unsupported PID");
        check(ObdProtocol.mask("410080000001\r>","00")==0x80000001L,"Unsigned bitmap");
        System.out.println("11 OBD protocol checks passed");
    }
}
