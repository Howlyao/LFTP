package lftp;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import lftp.DataSender.Receiver;
import lftp.DataSender.Sender;
import lftp.Packet.PacketType;

//文件数据发送类
public class DataReceiver {
	private int lastRcvIndex;						//最后接受的数据包索引			
	private int lastReadIndex;						//最后读取的数据包索引
	
	private FilePacketsManager filePacketsManager;  //文件数据包管理类
	private String filePath;						//文件存储路径
	
	private DatagramSocket datagramSocket;			//Socket
	private InetAddress address;					//发送方的IP地址
	private int senderPort;							//发送方的端口
	private int rcvWindow;							//接受窗口
	private int rcvBuffer;							//接受缓冲区
	private LinkedList<Integer> rcvPacketList;		//ACK接受存储链表
	private Map<Integer,Packet> waitingList;		//等待被处理的数据包链表
	
	
	//构造函数，针对服务器
	public DataReceiver(String filePath,int dataPort,InetAddress address,int senderPort,int rcvWindow) {
		initial(filePath,address,senderPort,rcvWindow);
		try {
			
			datagramSocket = new DatagramSocket(dataPort);
			
		}catch(Exception e) {
			e.printStackTrace();
		}
			
	}
	
	//构造函数，针对客户端
	public DataReceiver(String filePath,DatagramSocket datagramSocket,InetAddress address,int senderPort,int rcvWindow) {
		initial(filePath, address, senderPort, rcvWindow);
		this.datagramSocket = datagramSocket;
	}
	
	//初始化
	public void initial(String filePath,InetAddress address,int senderPort,int rcvWindow) {

		filePacketsManager = new FilePacketsManager();
		this.filePath = filePath;
		this.address = address;
		this.senderPort = senderPort;
		this.rcvWindow = rcvWindow;
		this.rcvBuffer = rcvWindow;
		lastRcvIndex = -1;					//设为-1
		lastReadIndex = -1;
		rcvPacketList = new LinkedList<>();
		waitingList = new HashMap<>();
		
	}
	
	//开启接受数据包的线程
	public void start(boolean flag) {

		try  {
			//开启接受线程
			Thread thread = new Thread(new Receiver());
			thread.start();
			//等待线程结束
			if (flag) {
				thread.join();
			}
				
			
		}catch(Exception e) {
			
			e.printStackTrace();
		}
		
	
	}
	
	
	public class Receiver implements Runnable {
		
		private byte[] buf =  new byte[Packet.MAX_LENGTH];
		private DatagramPacket rcvDatagramPacket = new DatagramPacket(buf, buf.length);	//接受Packet
		private DatagramPacket sendDatagramPacket;	//发送Packet
		private BufferedOutputStream bos = null;	//文件输出流
		private FileOutputStream fos = null;
		private File file = null;					//文件
		
		
		//构造函数
		public Receiver(){
			
			//初始化文件输出流
			try {	
				file = new File(filePath);
				fos = new FileOutputStream(file);
				bos = new BufferedOutputStream(fos);
				
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		
		//线程运行函数
		public void run() {
			
			//循环接受发送方的数据报
			while (true) {
				
				try {
					//获取数据报
					datagramSocket.receive(rcvDatagramPacket);	
					
					address = rcvDatagramPacket.getAddress();
					senderPort = rcvDatagramPacket.getPort();
					
					//数据报分析
					Packet packet = new Packet(rcvDatagramPacket.getData());
					
					
					//接受ACKALL类型数据包，退出循环
					if (packet.getPacketType() == PacketType.ACKALL) {
						System.out.println("End receiving");
						break;
					}
					
					//获取SequenceNum
					int sequenceNum = packet.getSequenceNum();
					System.out.println("Receive a packet with sequenceNum: " + sequenceNum);
					
					//判断数据报是否出错
					if (packet.isValidPacket()) {
						//数据报处理
						handleRcvPacket(packet); 
					} else {
						
						//输出提示信息
						System.out.println("Packet " + sequenceNum + " corrupt");
					}
				
					
				}catch(Exception e) {
					e.printStackTrace();
					
				}
			}
			
			//关闭流和Socket
			closeStreamAndSocket();
		
			
			
		}
		
		
		//发送ACK数据报，包含RcvWindow信息
		public void sendAck(int sequenceNum) throws IOException{
			//计算rcvWindow
			rcvWindow = rcvBuffer - (lastRcvIndex - lastReadIndex);
			
			Packet packet = new Packet(null,PacketType.ACK,sequenceNum,rcvWindow);
			sendDatagramPacket = new DatagramPacket(packet.getPacketBytes(), packet.getPacketSize(),address,senderPort);
			
			//发送
			datagramSocket.send(sendDatagramPacket);
		}
		
		//处理接受的数据包
		public void handleRcvPacket(Packet packet) throws IOException{
			
			//获取数据包的sequence number
			int sequenceNum = packet.getSequenceNum();
			byte[] data = packet.getDataBytes();
			
			//发送Ack
			System.out.println("Send a ack packet with ackNum: " + sequenceNum);
			sendAck(sequenceNum);
			
			//当没有接受相同索引的包时
			if (!rcvPacketList.contains(sequenceNum)) {
				//将sequenceNum添加至存储链表
				rcvPacketList.add(sequenceNum);
				
				//更新lastRcvIndex
				lastRcvIndex = lastRcvIndex > sequenceNum ? lastRcvIndex : sequenceNum;
				
				//如果接受的是期望数据报，进行文件流输出
				if (sequenceNum - 1 == lastReadIndex) {
					
					bos.write(data);
					
					//
					while(waitingList.containsKey(++sequenceNum)) {
						Packet p = waitingList.get(sequenceNum);
						waitingList.remove(sequenceNum);
						bos.write(p.getDataBytes());
						
					}
					//更新lastReadIndex
					lastReadIndex = sequenceNum - 1;
					
				} else {
					//将数据报添加至等待处理的表中
					waitingList.put(sequenceNum, packet);
					
				}
				
				
				
			} 
			
			
			
		}
	
		//关闭流和Socket
		public void closeStreamAndSocket() {
			
			
			try {
				datagramSocket.close();
				bos.close();
				fos.close();
				
				//Files.write(Paths.get("d:/test1.bmp"), fileBuffer.array());
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
}