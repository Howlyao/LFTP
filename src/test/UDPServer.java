package test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import lftp.PortManager;

public class UDPServer {
	private final int MAX_LENGTH =20;
	private final int PORT = 8080;
	private String netAddress = "127.0.0.1";
	private byte[] receBuf = new byte[MAX_LENGTH];
	
	private DatagramSocket datagramSocket;
	
	private DatagramPacket datagramPacket;
	
	public UDPServer() {
		
		try {
			datagramSocket = new DatagramSocket(PORT);
			datagramPacket = new DatagramPacket(receBuf,receBuf.length);
	
			datagramSocket.receive(datagramPacket);
			
			String receStr = new String(datagramPacket.getData(),0,datagramPacket.getLength());
			
			//解析数据
			System.out.println("Server Receive: " + receStr);
			System.out.println("Server Port:" + datagramPacket.getPort());
			
			int clientPort = datagramPacket.getPort();
			
			
			String msg = "hello";
			datagramPacket = new DatagramPacket(msg.getBytes(), msg.getBytes().length,InetAddress.getByName(netAddress),clientPort);
			datagramSocket.send(datagramPacket);
			
		} catch(SocketException e) {
			e.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		}finally {
			
			if (datagramSocket != null) {
				datagramSocket.close();
			}
		}
	}
	
	public static void main(String[] args) {
		
		UDPServer udpServer = new UDPServer();
	}
}
