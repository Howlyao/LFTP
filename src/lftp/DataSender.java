package lftp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.Timer;

import lftp.Packet.PacketType;


//文件数据发送类
public class DataSender {
	
	private final int RWND_THRESHOLD = 520;		//sleepTime = RWND_THRESHOLD - cwnd	
	private final int MAX_RWND = 515;				//rwnd最大值
	private final int MIN_SLEEPTIME = 5;			//最小的睡眠时间
	private int threshold;							//拥塞控制变量
	private int rwnd;								//接受窗口大小
	private int cwnd;								//拥塞窗口大小
	private int lastSentIndex;						//最后送的数据包索引
	private int lastAckIndex;						//最后ACK的数据包索引
	
	private int filePacketCount;					//文件数据包的数量
	private int sendTime = 1;
	private LinkedList<Integer> ackedList;			//ACK 链表
	private FilePacketsManager filePacketsManager;	//文件管理对象
	
	private DatagramSocket datagramSocket;			//Socket
	private int receiverPort;							//数据端口
	private InetAddress address;					//服务器IP地址
	private Thread sender;							//发送文件数据包线程
	private Thread receiver;						//接受ACK线程
	
	private boolean error;
	
	//构造函数
	public DataSender(DatagramSocket datagramSocket,InetAddress address,int receiverPort,int rwnd,FilePacketsManager filePacketsManager) {
		initial(address,receiverPort,rwnd,filePacketsManager);
		this.datagramSocket = datagramSocket;
	
	}
	
	
	
	
	public void initial(InetAddress address,int receiverPort,int rwnd,FilePacketsManager filePacketsManager) {
		//变量对象初始化
		
		this.address = address;
		this.receiverPort = receiverPort;
		this.rwnd = rwnd;
		this.cwnd = 1;
		this.filePacketsManager = filePacketsManager;
		this.filePacketCount = filePacketsManager.getPacketCount();
		ackedList = new LinkedList<>();
		this.threshold = 256;
		
		lastSentIndex = -1;
		lastAckIndex = -1;
		
		this.error = false;
	}
	
	public void start(boolean flag) {

		try  {
			//开启发送线程和接受线程
			sender = new Thread(new Sender());
			receiver = new Thread(new Receiver());
			sender.start();
			receiver.start();
			//等待线程结束
			if (flag) {
				sender.join();
				receiver.join();
			}
				
			
		}catch(Exception e) {
			
			e.printStackTrace();
		}
		
	}
	
	//发送对应SequenceNum的文件数据包
	void sendPacket(int sequenceNum){
		//从文件管理对象获取对应SequenceNum的数据
		byte[] data = filePacketsManager.getFilePacket(sequenceNum);
		Packet packet = new Packet(data,PacketType.DATA,sequenceNum);
		
		//DatagramPacket
		DatagramPacket datagramPacket = new DatagramPacket(packet.getPacketBytes(), packet.getPacketSize(),address,receiverPort);
//		System.out.println(address.toString() + " " + receiverPort);
		try {
			
			
			//setTimeout  设置超时机制
			Timer timer = new Timer();
			
			//超时后的处理
			timer.schedule(new PacketTimerTask(sequenceNum) {
				
				public void run() {
					int sequenceNum1 = this.getSequenceNum();
					System.out.println("Packet with sequenceNum1 " +sequenceNum1 +  " packet timeout");
					try{
						sender.sleep(1);
						sendPacket(sequenceNum1);
						cwnd -= 50;
						if (cwnd <= 0 ) cwnd = 1;
									
					}catch(Exception e) {
						e.printStackTrace();
					}
				}
			}, 1500);
			
			//将Timer和SequenceNum映射，添加至哈希表
			filePacketsManager.timerMap.remove(sequenceNum);
			filePacketsManager.timerMap.put(sequenceNum, timer);
			//System.out.println("set Timeout with sequenceNum " + sequenceNum);
			
			//sender.sleep(1);
			System.out.println("Send packet with sequenceNum: " + sequenceNum);
			datagramSocket.send(datagramPacket);
			
			
			
			
			
		} catch(IOException e) {
			e.printStackTrace();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	//发送线程类
	class Sender implements Runnable {
		
		
		public void run() {
			//lastAckIndex < filePacketCount - 1 &&
			
			System.out.println("Start to send file");
			//循环发送文件数据报文
			while (lastSentIndex < filePacketCount - 1 && !error) {
				
				try {
					//流控制
					
					//lastSentIndex - lastAckIndex == 接受方
					if (lastSentIndex - lastAckIndex <= rwnd) {

						//SequenceNum++,发送对应的文件数据包
						lastSentIndex++;
						sendPacket(lastSentIndex);
						
						//拥塞控制
						sendTime = RWND_THRESHOLD - cwnd;
						sendTime = sendTime <= MIN_SLEEPTIME ? MIN_SLEEPTIME: sendTime;
						
						//当cwnd >= threshold,cwnd开始线性增长
						if (cwnd >= threshold) cwnd += 5;
						else cwnd *= 2;
						
						cwnd = (cwnd >= MAX_RWND ? MAX_RWND : cwnd);
						
						Thread.sleep(sendTime);
						
					} else {
						Thread.sleep(1);
						
					}
				}catch(Exception e) {
					e.printStackTrace();
				}
				
			}
			
			
		
		}

	}
	//LFTP lget 120.77.206.16 hah.jpg
	//LFTP lsend 120.77.206.16 d:/hah.jpg
	//LFTP lsend 120.77.206.16 d:/truthDare.mkv
	//LFTP lsend 120.77.206.16 d:/1_1.bmp
	//LFTP lsend 120.77.206.16 d:/3.bmp
//	LFTP lsend 120.77.206.16 d:/OverWatch.exe
	//接受线程类 ACK接受
	class Receiver implements Runnable {
		
		private byte[] rcvBuf = new byte[64];
		private DatagramPacket datagramPacket;
		
		//
		public void run() {
			
			
			//LFTP lget 127.0.0.1 xx.bmp
			datagramPacket = new DatagramPacket(rcvBuf, rcvBuf.length);
			try {
			
				//循环->lastAckIndex 等于 文件最后 一个数据包的索引
				while (lastAckIndex < filePacketCount - 1) {
					
					//接受ACK数据报    Socket获取Packet
					datagramSocket.receive(datagramPacket);
						
					
					//Packet分析
					Packet packet = new Packet(datagramPacket.getData());
					//RcvWindow 接受窗口大小更新
					int windowSize = packet.getRcvWindow();
					rwnd = windowSize;
					//数据报AckNum
					int ackNum = packet.getAckNum();
					System.out.println("Receive ACK packet: " + ackNum);
					
					//如果该AckNum对应的Timer不为空时，取消Timer
					if (filePacketsManager.timerMap.get(ackNum) != null) {
						
						filePacketsManager.timerMap.get(ackNum).cancel(); 
						filePacketsManager.timerMap.remove(ackNum);
					} 
					
					
					
					//接受冗余AckNum报文
					if (ackedList.contains(ackNum)) {
						//do nothing
					//ackNum大于期望ackNum，添加至链表
					} else if (ackNum > lastAckIndex + 1) {				
						ackedList.add(ackNum);
						
					//ackNum等于期望ackNum,更新lastAckIndex
					} else if (ackNum == lastAckIndex + 1) {
						
						ackedList.add(ackNum);
						while (ackedList.contains(ackNum)) {
							ackNum++;
						}
						
						lastAckIndex = ackNum - 1;
						
					}
						
		
				}
				
			
				//完成文件发送
				System.out.println("Finish sending file");
				//发送ACKALL类型报文，提示服务器停止接受
				Packet endPacket = new Packet(null,PacketType.ACKALL);
				datagramPacket = new DatagramPacket(endPacket.getPacketBytes(), endPacket.getPacketSize(),address,receiverPort);
				datagramSocket.send(datagramPacket);
				
				
				} catch(IOException e) {
					
					try {
						System.out.println("Error");
						
						error = true;
						sender.stop();
						for (int i = 0;i <= lastSentIndex;i ++) {
							if (filePacketsManager.timerMap.get(i) != null) {
								
								filePacketsManager.timerMap.get(i).cancel(); 
								filePacketsManager.timerMap.remove(i);
							} 
						}
						
						//sender.resume();
						
					}catch(Exception ex) {
						
					}
					
				} 
				
			}
			
		}
	}