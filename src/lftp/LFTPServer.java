package lftp;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import lftp.Packet.PacketType;

public class LFTPServer implements Runnable{
	
	private final int MAX_LENGTH = Packet.MAX_LENGTH;				//UDP数据报的长度最值
	private final int CONTROL_PORT = 1025;				//服务器控制端口
	private final int MAX_PORT = 65535;					//数据端口的最大值
	private final int MIN_PORT = 1026;					//数据端口的最小值
																	
	private byte[] recvBuf = new byte[MAX_LENGTH];		//Socket接受缓冲区
	private byte[] sendBuf = null;						//Socket发送的字节流数据报变量
	
	private DatagramSocket datagramSocket;				//Socket
	
	private InetAddress address;						//客户端IP地址
	private int client_port;							//客户端端口
	
	public LFTPServer() {
		
		try {
			//将控制端口与Socket绑定
			datagramSocket = new DatagramSocket(CONTROL_PORT);
			
					
		}catch (SocketException e) {
			
			e.printStackTrace();
		
		}
		
	}
	
	public void run() {
		
		while (true) {
			
			try {
				//等待接受客户端的请求
				System.out.println("Server: Waiting for client request");
				DatagramPacket datagramPacket = new DatagramPacket(recvBuf,recvBuf.length);
				datagramSocket.receive(datagramPacket);
				Packet connectPacket = new Packet(datagramPacket.getData());
				
				
				//如果数据报类型出错  打印提示信息，并重新等待连接
				if (connectPacket.getPacketType() != PacketType.LSEND && 
						connectPacket.getPacketType() != PacketType.LGET) {	
					
					System.out.println("Server: Receive a invalid packet");
					continue;
				}
				
				
				
				//获取客户端的地址和端口信息
				address = datagramPacket.getAddress();
				client_port = datagramPacket.getPort();
				System.out.println("Server: Reveice request from " + address.toString());
				
				//随机分配数据端口给客户端
				int data_port =(int)((MAX_PORT - MIN_PORT + 1) * Math.random() + MIN_PORT);
				while (PortManager.isPortUsing(data_port)) {
					data_port =(int)((MAX_PORT - MIN_PORT + 1) * Math.random() + MIN_PORT);
				}
				System.out.println("Server: allocate the port " + data_port +" to client to transfer file");
				
				if (connectPacket.getPacketType() == PacketType.LSEND ) {
					
					//获取文件名以及设定存储位置
					String fileName = new String(connectPacket.getDataBytes());
					String saveFilePath = "d:/ServerStorage/" + fileName;
		
					
					//发送数据传输的端口信息
					int windowSize = 1000;
					Packet packet = new Packet(null,PacketType.INITIALTRANSFER,windowSize,data_port);
					sendBuf = packet.getPacketBytes();
					DatagramPacket sendPacket = new DatagramPacket(sendBuf,sendBuf.length,address,client_port);
					datagramSocket.send(sendPacket);
					
					//构造数据接受对象
					System.out.println("Server: Ready to receive a file: " + fileName);
					DataReceiver dataReceiver = new DataReceiver(saveFilePath,data_port,address,client_port,windowSize);
					dataReceiver.start();
					
					
				} else if (connectPacket.getPacketType() == PacketType.LGET) {
					//获取文件名
					String fileName = new String(connectPacket.getDataBytes());
					String filePath = "d:/ServerStorage/" + fileName;
					
					//构造文件数据管理对象
					FilePacketsManager filePacketsManager = new FilePacketsManager(filePath);
					
		
					//获取RcvWindow的值
					int rcvWindow = connectPacket.getRcvWindow();
					
					//发送数据传输的端口信息
					Packet packet = new Packet(null,PacketType.INITIALTRANSFER,rcvWindow,data_port);
					sendBuf = packet.getPacketBytes();
					DatagramPacket sendPacket = new DatagramPacket(sendBuf,sendBuf.length,address,client_port);
					datagramSocket.send(sendPacket);
					
					//接受数据报，缓冲
					datagramPacket = new DatagramPacket(recvBuf,recvBuf.length);
					datagramSocket.receive(datagramPacket);
					
					DataSender dataSender = new DataSender(data_port, address, client_port, rcvWindow, filePacketsManager);
					dataSender.start(false);
				}
				
			}catch(InvalidPathException e) {
				System.out.println("File does not exist");
				System.out.println("Send Reject packet to Client");
				
				Packet packet = new Packet(null,PacketType.REJECT);
				sendBuf = packet.getPacketBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendBuf,sendBuf.length,address,client_port);
				try {
					datagramSocket.send(sendPacket);
				}catch(Exception ex) {
					System.out.println("Server: Fail to send packet");
				}
				
				
			}
			catch(IOException e) {
				System.out.println("Server: Fail to read file");
			}
			
		}
		
	}
	
	
	
	
	
	
	public static void main(String []args){
		
		Thread a = new Thread(new LFTPServer());
		a.start();
	}
	
}
