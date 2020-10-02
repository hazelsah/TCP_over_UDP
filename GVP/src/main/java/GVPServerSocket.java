import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.CRC32;

import com.kashipazha.TCP.MyServerSocket;


public class GVPServerSocket extends MyServerSocket
{

    private DatagramSocket serverSocket;
    
    public GVPServerSocket(int portNumber) throws Exception
    {
        super(portNumber);
        serverSocket = new DatagramSocket(portNumber);
    }

    public GVPSocket accept() throws Exception
    {
        byte[] array_syn = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(array_syn, array_syn.length);
        serverSocket.receive(receivePacket);
        GVPHeader syn = new GVPHeader(array_syn);
        if (!(syn.getSYN())){
            throw new GVPHandshakingException("Bad message received. Excpecting SYN");
        }
        InetAddress IPAddress = receivePacket.getAddress();
        int port = receivePacket.getPort();
        GVPSocket newSocket = new GVPSocket(IPAddress, port);
        GVPHeader syn_ack = new GVPHeader(newSocket.getLocalPort(), port);
        syn_ack.setSYN(true);
        syn_ack.setACK(true);
        newSocket.sendPacket(syn_ack.getArray());
        byte[] array_ack = new byte[1024];
        newSocket.readPacket(array_ack);
        GVPHeader ack = new GVPHeader(array_ack);
        if (!(ack.getACK())){
            throw new GVPHandshakingException("Bad message received. Excpecting ACK");
        }
        System.out.println("Connection established");
        newSocket.startReading();
        return newSocket;
    }
}