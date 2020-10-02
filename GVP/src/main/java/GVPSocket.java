import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.sql.Array;
import java.sql.Time;
import java.util.*;
import java.util.zip.CRC32;
import java.util.Map;
import java.util.zip.Checksum;

import com.kashipazha.TCP.MySocket;


public class GVPSocket  implements MySocket
{
    private DatagramSocket socket;
    private InetAddress destIP;
    private int destPort;
    private int seqNum;
    private int receiveNum;
    private int lastACKed;
    public static final int MSS = 1024 + GVPHeader.headerSize;
    private static final int timeoutMillis = 5;
    private ArrayList<byte[]> buffer;
    private ArrayList<Integer> ACKbuffer;
    private ReadThread thread;
    private int buffCounter;
    private byte[] sendBuffer;

    public GVPSocket(String IP, int portNumber) throws Exception
    {
        lastACKed = -1;
        receiveNum = -1;
        seqNum = 0;
        buffCounter = 0;
        socket = new DatagramSocket(0);
        destIP = InetAddress.getByName(IP);
        destPort = portNumber;
        buffer = new ArrayList<byte[]>();
        ACKbuffer = new ArrayList<Integer>();
        thread = new ReadThread();
        handshake();
    }

    public GVPSocket(InetAddress IP, int portNumber) throws Exception
    {
        lastACKed = -1;
        receiveNum = -1;
        seqNum = 0;
        buffCounter = 0;
        socket = new DatagramSocket(0);
        destIP = IP;
        destPort = portNumber;
        buffer = new ArrayList<byte[]>();
        ACKbuffer = new ArrayList<Integer>();
        thread = new ReadThread();
    }

    public void startReading(){
        thread.start();
    }

    private void handshake() throws Exception {
        GVPHeader syn = new GVPHeader(socket.getLocalPort(), destPort);
        syn.setSYN(true);
        sendPacket(syn.getArray());
        byte[] array = new byte[1024];
        readPacket(array);
        GVPHeader syn_ack = new GVPHeader(array);
        if (!(syn_ack.getSYN() && syn_ack.getACK())){
            throw new GVPHandshakingException("Bad message received. Excpecting SYN-ACK");
        }
        destPort = syn_ack.getSourcePortNumber();
        GVPHeader ack = new GVPHeader(socket.getLocalPort(), destPort);
        ack.setACK(true);
        sendPacket(ack.getArray());
        startReading();
    }

    public int getLocalPort(){
        return socket.getLocalPort();
    }

    public void send(String pathToFile) throws Exception {
        File file = new File(pathToFile);
        byte[] bytesArray = new byte[(int) file.length()];
        FileInputStream fis = new FileInputStream(file);
        fis.read(bytesArray);
        fis.close();
        int numOfPackets = bytesArray.length / (MSS - GVPHeader.headerSize);
        if (bytesArray.length % (MSS - GVPHeader.headerSize) != 0) numOfPackets++; 
//      System.out.println(bytesArray.length);
        ByteBuffer initialPacket = ByteBuffer.allocate(4);
        initialPacket.putInt(numOfPackets);
        send(initialPacket.array());
        send(bytesArray);
    }

    public void read(String pathToFile) throws Exception
    {
        File file = new File(pathToFile);
        file.createNewFile();
//      System.out.println("Start of writing to file");
        FileOutputStream fos = new FileOutputStream(file);
        while (buffCounter>=buffer.size()) Thread.sleep(1);
        byte[] initialPacketWithHeader = buffer.get(buffCounter);
        buffCounter++;
        byte[] initialPacket = new byte[initialPacketWithHeader.length - GVPHeader.headerSize];
        System.arraycopy(initialPacketWithHeader, GVPHeader.headerSize, initialPacket, 0, initialPacket.length);        
        /*
        System.out.println("LEN: " + initialPacket.length);
        if (initialPacket.length != 4){
            throw new GVPException("Error in writing to file. Initial packet is not correct");
        }
        */
        int numOfPackets = ByteBuffer.wrap(initialPacket).getInt();
        if (numOfPackets < 0){
            fos.close();
            throw new GVPException("Error in writing to file. Initial packet is not correct");
        }
        for (int i=0;i<numOfPackets;i++){
            while (buffCounter>=buffer.size()) Thread.sleep(1);
            byte[] temp = buffer.get(buffCounter);
            buffCounter++;            
            byte[] toWrite = new byte[temp.length - GVPHeader.headerSize];
            System.arraycopy(temp, GVPHeader.headerSize, toWrite, 0, toWrite.length);    
            fos.write(toWrite);
        }
//      System.out.println("End of writing to file");
        fos.close();
    }

    public void send(byte[] array) throws Exception
    {
        if (array.length > MSS - GVPHeader.headerSize){
            byte[] temp = new byte[MSS - GVPHeader.headerSize];
            for (int i = 0;i<array.length;i++){
                temp[i%(MSS - GVPHeader.headerSize)] = array[i];
                if ((i+1)%(MSS - GVPHeader.headerSize) == 0 ){
                    send(temp);
                }
                else if (i == array.length-1){
                    byte[] temp2 = new byte[array.length - ((MSS - GVPHeader.headerSize)*(array.length/(MSS - GVPHeader.headerSize)))];
                    for (int j = ((MSS - GVPHeader.headerSize)*(array.length/(MSS - GVPHeader.headerSize)));j<array.length;j++) temp2[j%(MSS - GVPHeader.headerSize)] = array[j];
                    send(temp2);
                    break;
                }
            }
//          System.out.println("big packet");
            return;
        }
//      Making Header
        GVPHeader packetHeader = new GVPHeader(socket.getLocalPort(),destPort);
        packetHeader.setSeqNumber(seqNum);
        Checksum checksum = new CRC32();
        checksum.update(array,0,array.length);
        long checksumValue = checksum.getValue();
        packetHeader.setChecksum(checksumValue);
//      Sending packet
        sendBuffer = concat(packetHeader.getArray(),array);
        sendPacket(sendBuffer);
//      Start timer
        TimeoutThread timeoutthread = new TimeoutThread(seqNum);
        timeoutthread.start();
        System.out.println("packet with number "+ seqNum + " sent");
        while (lastACKed != seqNum) Thread.sleep(1);
        seqNum++;
    }

    void sendPacket(byte[] array) throws Exception
    {
        DatagramPacket packet = new DatagramPacket(array, array.length, destIP, destPort);
        socket.send(packet);
    }

    public void read(byte[] array) throws Exception
    {
        byte[] withHeader = new byte[MSS];
//      System.out.println("buffer.size: "+ buffer.size()+ " buff counter: "+ buffCounter);
        while (buffCounter>=buffer.size()) Thread.sleep(1);
        withHeader = buffer.get(buffCounter);
        buffCounter++;
        System.arraycopy(withHeader, GVPHeader.headerSize, array, 0, Math.min(array.length, withHeader.length - GVPHeader.headerSize));
    }

    void readPacket(byte[] array) throws Exception 
    {
        DatagramPacket receivePacket = new DatagramPacket(array, array.length);
        socket.receive(receivePacket);
//      System.out.println("packet size: " + receivePacket.getLength());
    }

    public void close() throws Exception {
        GVPHeader fin = new GVPHeader(socket.getLocalPort(), destPort);
        fin.setFIN(true);
        sendPacket(fin.getArray());
        socket.close();
    }

    public void sendAck(int seqNum) throws Exception{
        GVPHeader ack = new GVPHeader(socket.getLocalPort(),destPort);
        ack.setACK(true);
        ack.setACKNumber(seqNum);
        sendPacket(ack.getArray());
    }

    private byte[] concat(byte[] array1, byte[] array2) {
        int aLen = array1.length;
        int bLen = array2.length;
        byte[] result = new byte[aLen + bLen];

        System.arraycopy(array1, 0, result, 0, aLen);
        System.arraycopy(array2, 0, result, aLen, bLen);
        return result;
    }

    private boolean errorDetection(long headerChecksum, long dataChecksum){
        return headerChecksum != dataChecksum;
    }

    private class ReadThread extends Thread
    {
        @Override
        public void run(){
            while (true){
//              System.out.println("reading new packet");
                byte[] array = new byte[MSS];
                DatagramPacket receivePacket = new DatagramPacket(array, array.length);
                try {
                    socket.receive(receivePacket);
//                  System.out.println("packet received");
                } catch (IOException e) {
                    System.out.println("Thread is not receiving any packets");
                    break;
                }
                byte[] header = new byte[GVPHeader.headerSize];
                for (int i=0;i<GVPHeader.headerSize;i++) header[i] = array[i];
                GVPHeader head = new GVPHeader(header);
                if (head.getACK()){
//                  System.out.println("packet is ack with number: "+head.getACKNumber());
                    ACKbuffer.add(head.getACKNumber());
//                  System.out.println("ack packet added to ackbuffer and size is now:"+ACKbuffer.size());
                }
                else if (head.getFIN()){
                    System.out.println("Connection closed");
                    socket.close();
                    break;
                }
                else {
//                  System.out.println("Packet has data and is not ack."+" Packet number is: "+head.getSeqNumber());
//                  System.arraycopy(header, GVPHeader.headerSize, array, 0, Math.min(array.length, header.length) - GVPHeader.headerSize); 
                    Checksum checksum = new CRC32();
                    checksum.update(array, GVPHeader.headerSize, receivePacket.getLength() - GVPHeader.headerSize);
                    long checksumValue = checksum.getValue();
                    if (errorDetection(head.getChecksum(), checksumValue)){
                        System.out.println("Error in packet detected");
                        continue;
                    }
                    try {
                        sendAck(head.getSeqNumber());
//                      System.out.println("ACK sent and ackNum is:"+ head.getSeqNumber());
                    } catch (Exception e) {
                        System.out.println("Exception occured in sending ACK");
                    }
                    if (head.getSeqNumber() == receiveNum+1){
                        receiveNum++;
                    }
                    else continue;

                    byte[] temp = new byte[receivePacket.getLength()];
                    for (int i = 0;i<receivePacket.getLength();i++) temp[i] = array[i];
                    buffer.add(temp);
                }
            }
        }
    }

    private void resend() throws Exception
    {
        sendPacket(sendBuffer);
    }

    private class TimeoutThread extends Thread{
        private int seqNum;
        Timer timer;

        public TimeoutThread(int _seqNum){
            super();
            seqNum = _seqNum;
            timer = new Timer();
            timer.schedule(new Timeout(), new Date(), timeoutMillis);
//          System.out.println("received seq number is: "+seqNum);
        }

        private class Timeout extends TimerTask
        {
            public void run(){
                try {
//                  System.out.println("time limit exceeded");
                    boolean flag = false;
//                  System.out.println("ack buffer size is: "+ACKbuffer.size());
/*
                    if (seqNum % 10 == 0){
                        ArrayList<Integer> expired = new ArrayList<Integer>();
                        for (int i=0;i<ACKbuffer.size();i++){
                            if (ACKbuffer.get(i) < seqNum) expired.add(i);
                        }
                        for (int i = expired.size()-1;i>=0;i--){
                            ACKbuffer.remove(expired.get(i));
                        }
                    }
*/
                    ArrayList<Integer> expired = new ArrayList<Integer>();
                    for(int i=0;i<ACKbuffer.size();i++){
                        int ackNumber = ACKbuffer.get(i);
//                      System.out.println("searching for ack "+ seqNum +" and ack buffer size: "+ACKbuffer.size());
                        if (ackNumber < seqNum) expired.add(i);
                        if (ackNumber == seqNum){
                            ACKbuffer.remove(i);
//                          System.out.println("ack found in ackbuffer ack number "+i+" removed");
                            timer.cancel();
//                          System.out.println("timer canceled");
                            flag = true;
                            lastACKed = seqNum;
                            break;
                        }
                    }
                    for (int i = expired.size()-1;i>=0;i--){
                        ACKbuffer.remove(expired.get(i));
                    }
                    if (!flag){
//                      System.out.println("ack not found => resending " + seqNum);
                        resend();
                    }
                } catch (Exception e) {
                    System.out.println("Exception occured in Timeout");
                }
            }
        }
    }

    public Map<String,String> getHeaders() throws Exception {
        Map<String, String> lastPacket = new HashMap<String, String>();
        if (sendBuffer != null){
            byte[] temp = new byte[sendBuffer.length];
            for (int i=0;i<sendBuffer.length;i++) temp[i] = sendBuffer[i];
            byte[] headerTemp = new byte[GVPHeader.headerSize];
            for (int i=0;i<GVPHeader.headerSize;i++) headerTemp[i] = sendBuffer[i];
            String tempString = new String(temp, "UTF-8");
            String headerTempString = new String(headerTemp, "UTF-8");
            lastPacket.put(headerTempString, tempString);
        }
        if (buffer.size() != 0){
            byte[] lastReceived = buffer.get(buffer.size()-1);
            byte[] temp = new byte[lastReceived.length];
            for (int i=0;i<lastReceived.length;i++) temp[i] = lastReceived[i];
            byte[] headerTemp = new byte[GVPHeader.headerSize];
            for (int i=0;i<GVPHeader.headerSize;i++) headerTemp[i] = lastReceived[i];
            String tempString = new String(temp, "UTF-8");
            String headerTempString = new String(headerTemp, "UTF-8");
            lastPacket.put(headerTempString, tempString);
        }
        return lastPacket;
    }

}