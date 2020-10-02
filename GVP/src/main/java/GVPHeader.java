import java.util.*;
import java.util.zip.CRC32;
import java.nio.*;

public class GVPHeader
{
    private boolean SYN;
    private boolean ACK;
    private boolean FIN;
    private int sourcePort;
    private int destPort;
    private int seqNumber;
    private int ACKnumber;
    public static final int headerSize = 25;
    private byte[] checksum;

    public GVPHeader(int _sourcePort, int _destPort){
        sourcePort = _sourcePort;
        destPort = _destPort;
        SYN = false;
        ACK = false;
        FIN = false;
        ACKnumber = 0;
        seqNumber = 0;
        checksum = new byte[8];
    }

    public GVPHeader(byte[] array){
        checksum = new byte[8];
        byte[] result = new byte[4];
        result[0] = 0;
        result[1] = 0;
        result[2] = array[0];
        result[3] = array[1];
        sourcePort = ByteBuffer.wrap(result).getInt(); 
        result[0] = 0;
        result[1] = 0;
        result[2] = array[2];
        result[3] = array[3];
        destPort = ByteBuffer.wrap(result).getInt();
        result[0] = array[4];
        result[1] = array[5];
        result[2] = array[6];
        result[3] = array[7];
        seqNumber = ByteBuffer.wrap(result).getInt();
        result[0] = array[8];
        result[1] = array[9];
        result[2] = array[10];
        result[3] = array[11];
        ACKnumber = ByteBuffer.wrap(result).getInt();
        /*
        result[0] = 0;
        result[1] = 0;
        result[2] = array[12];
        result[3] = array[13];
        checksum = ByteBuffer.wrap(result).getInt();
        */
        result[0] = 0;
        result[1] = 0;
        result[2] = 0;
        result[3] = array[14];
        int temp = ByteBuffer.wrap(result).getInt();
        if (temp == 1) ACK = true;
        else ACK = false;
        result[0] = 0;
        result[1] = 0;
        result[2] = 0;
        result[3] = array[15];
        temp = ByteBuffer.wrap(result).getInt();
        if (temp == 1) SYN = true;
        else SYN = false;
        result[0] = 0;
        result[1] = 0;
        result[2] = 0;
        result[3] = array[16];
        temp = ByteBuffer.wrap(result).getInt();
        if (temp == 1) FIN = true;
        else FIN = false;
        for(int i = 0;i<8;i++){
            checksum[i] = array[i+17];
        }
    }

    public void setSYN(boolean SYN){
        this.SYN = SYN;
    }
    public void setACK(boolean ACK){
        this.ACK = ACK;
    }
    public void setFIN(boolean FIN){
        this.FIN = FIN;
    }
    public void setSeqNumber(int seqNumber){
        this.seqNumber = seqNumber;
    }
    public void setACKNumber(int ACKnumber){
        this.ACKnumber = ACKnumber;
    }
    public void setChecksum(long checksumValue){
        ByteBuffer b = ByteBuffer.allocate(8);
        b.putLong(checksumValue);
        checksum = b.array();
    }
    public boolean getSYN(){
        return SYN;
    }
    public boolean getACK(){
        return ACK;
    }
    public boolean getFIN(){
        return FIN;
    }
    public long getChecksum() {
        return ByteBuffer.wrap(checksum).getLong();
    }
    public int getSeqNumber(){
        return seqNumber;
    }
    public int getACKNumber(){
        return ACKnumber;
    }
    public int getSourcePortNumber(){
        return sourcePort;
    } 
    public int getDestPortNumber(){
        return destPort;
    }
    public byte[] getArray(){
        byte[] header = new byte[25];
        byte[] result = new byte[4];
        result = setByte(sourcePort, 2);
        header[0] = result[2];
        header[1] = result[3];
        result = setByte(destPort, 2);
        header[2] = result[2];
        header[3] = result[3];
        result = setByte(seqNumber, 4);
        header[4] = result[0];
        header[5] = result[1];
        header[6] = result[2];
        header[7] = result[3];
        result = setByte(ACKnumber,4);
        header[8] = result[0];
        header[9] = result[1];
        header[10] = result[2];
        header[11] = result[3];
        /*
        byte[] result = ByteBuffer.allocate(2).putLong(checksum.getValue()).array();
        header[12] = result[0];
        header[13] = result[1];
        */
        if(ACK==true) result = setByte(1,1);
        else result = setByte(0,1);
        header[14] = result[3];
        if(SYN==true) result = setByte(1,1);
        else result = setByte(0,1);
        header[15] = result[3];
        if(FIN==true) result = setByte(1,1);
        else result = setByte(0,1);
        header[16] = result[3];
        for (int i =0;i<8;i++){
            header[i+17] = checksum[i];
        }
        return header;
    }

    private byte[] setByte(int x, int size){
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(x);
        return b.array();
    }

}