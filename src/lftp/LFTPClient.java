package lftp;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.NoSuchFileException;
import java.util.LinkedList;
import java.util.Timer;

import lftp.Packet.PacketType;

public class LFTPClient implements Runnable {
	
	
	private BufferedReader br;								//字符缓冲输入流
	private final int CONTROL_PORT = 1025;					//服务器控制端口
	private final int MAX_LENGTH = Packet.MAX_LENGTH;					//接受缓冲区和数据报文的最大长度
	private final int TIMEOUT = 10000;						//请求服务端的超时时间
	
	private int clientPort;									//客户端端口
															
	private DatagramSocket datagramSocket = null;			//Socket
	private DatagramPacket datagramPacket = null;			//Packet
	
	private byte[] rcvBuffer;								//接受缓冲区
	
	String command;
	StringBuilder ins;
	InetAddress address;
	StringBuilder largeFile;
	
	//客户端构造函数
	public LFTPClient(int port) {
		
		//接受键盘输入的字符缓冲输入流
		br = new BufferedReader(new InputStreamReader(System.in)); 
		setClientPort(port);
		//端口与Socket绑定
		try {
			datagramSocket = new DatagramSocket(clientPort);
		} catch(SocketException e) {
			e.printStackTrace();
		}
	}
	
	//LFTP lsend 120.77.206.16 d:/hah.jpg
	//客户端线程运行函数
	public void run() {
		
		while (true) {
			
			try {
				//获取用户指令,lsend or lget
				System.out.println("Client: please input command");
				command = br.readLine();
				ins = new StringBuilder("");
				address = InetAddress.getByName("127.0.0.1");
				largeFile = new StringBuilder("");
				
				if(commandAnalyze()) {
					//向服务器发送文件命令
					if (ins.toString().equals("lsend")) {
						
						//取出发送文件，并对文件进行处理
						FilePacketsManager filePacketsManager = new FilePacketsManager(largeFile.toString());
						
						int fileSize = filePacketsManager.getFileSize();
						String fileName =filePacketsManager.getFileName();
						//输出提示信息
						System.out.println("Client: Ready to send the file:" + largeFile.toString() + " with size : " + fileSize);
						
						
						//关于发送文件命令以及文件名字的数据包
						Packet packet = new Packet(fileName.getBytes(),Packet.PacketType.LSEND);
						byte[] packetBytes = packet.getPacketBytes();
						
						
						//向服务器请求建立数据连接
						datagramPacket = new DatagramPacket(packetBytes, packetBytes.length, address, CONTROL_PORT);
						datagramSocket.send(datagramPacket);
						
						//设置接受的超时时间
						datagramSocket.setSoTimeout(TIMEOUT);
						
						//从服务器得到数据传输的端口号，以及接受窗口大小
						rcvBuffer = new byte[MAX_LENGTH];
						datagramPacket = new DatagramPacket(rcvBuffer, rcvBuffer.length);
						datagramSocket.receive(datagramPacket);
						
						//分析数据报文
						packet = new Packet(datagramPacket.getData());
						int dataPort = packet.getDataPort();
						int rwnd = packet.getRcvWindow();
					
						System.out.println("Client: dataPort: " + dataPort + " rcvWindowSize: " + rwnd);
						
						//构造负责数据传输的处理类
						DataSender dataSender = new DataSender(datagramSocket,address,dataPort, rwnd,filePacketsManager);	
						dataSender.start(true);
						
						
					} else if (ins.toString().equals("lget")) {
						
						String fileName = largeFile.toString();
						//关于接受文件命令以及文件名字的数据包, 设置RcvWindow
						int rcvWindow = 100;
						Packet packet = new Packet(fileName.getBytes(), Packet.PacketType.LGET, rcvWindow);
						byte[] packetBytes = packet.getPacketBytes();
						
						//发送LGET类型数据报
						datagramPacket = new DatagramPacket(packetBytes, packetBytes.length,address,CONTROL_PORT);
						datagramSocket.send(datagramPacket);
						
						//接受数据传输初始数据，以及服务器的数据端口
						rcvBuffer = new byte[MAX_LENGTH];
						datagramPacket = new DatagramPacket(rcvBuffer, rcvBuffer.length);
						datagramSocket.receive(datagramPacket);
						
						
						//解析数据报信息
						packet = new Packet(datagramPacket.getData());
						//收到REJECT数据报，输出提示信息
						if (packet.getPacketType() == PacketType.REJECT) {
							System.out.println("Client: File does not exist");
							continue;
						}
						
						
						int dataPort = packet.getDataPort();
						System.out.println("Client: receive allocated dataPort: " + dataPort);
						
						//设置存储路径
						String filePath = "d:/ClientStorage/" + fileName;
						
						//发送一个数据包缓冲,并将客户端的数据端口与服务器的数据端口地址绑定
						byte[] buf = new byte[4];
						datagramPacket = new DatagramPacket(buf, buf.length,address,dataPort);
						datagramSocket.send(datagramPacket);
						
						
						System.out.println("Client: Send a buffer packet");
						
						System.out.println("Client: Ready to receive a file");
						//构造数据传输接受类
						DataReceiver dataReceiver = new DataReceiver(filePath, datagramSocket, address, dataPort, rcvWindow);
						dataReceiver.start(true);
						
					}
					
					
				} else if (command.equals("exit")){
					//退出客户端
					System.out.println("Thanks for using");
					break;
				} else {

					//输错指令
					continue;
				}
				
			} catch(FileNotFoundException e) {
				System.out.println("Client: File not Found");
				
			} catch(SocketTimeoutException e) {
				System.out.println("Client: Timeout,please try inputing the command again");
				
			}catch(NoSuchFileException e) {
				System.out.println("Client: File not Found");
			}catch(Exception e) {
				e.printStackTrace();
				
			}
			
		}
		
		datagramSocket.close();
		System.exit(0);
		
	}
	public void setClientPort(int port) {
		this.clientPort = port;
	}
	
	//命令分析
	public boolean commandAnalyze() {
		
		if (command.equals("exit")) return false;
		
		//分离参数
		String[] arr = command.split(" ");
		
		//判断是否为规范的命令
		if (arr.length < 4) {
			System.out.println("Client: please input correct format of command\n"
					+ "like LFTP ins myserver largeFile");
			return false;
		}
		
		
		if (!arr[0].equals("LFTP")) {
			System.out.println("Client: please input correct format of command\n"
					+ "like LFTP ins myserver largeFile");
			
			return false;
		}
		
		if (!arr[1].equals("lsend") && !arr[1].equals("lget")) {
			System.out.println("Client: Incorrect instruction\n"
					+ "Correct instructions:lsend or lget");
			return false;
		}
		ins.append(arr[1]);
		
		try{
			
			address = InetAddress.getByName(arr[2]);
			
		} catch(UnknownHostException e) {
			System.out.println("Client: Incorrest address");
			return false;
		}
		
		largeFile.append(arr[3]);
		
		return true;
	}
	
	
	
	



	public static void main(String []args) {
		
		Thread b = new Thread(new LFTPClient(1026));
		b.start();
	}
}
