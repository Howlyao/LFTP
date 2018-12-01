package lftp;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Packet {
	
	public static int defaultHeaderSize = 24;
	public static final int MAX_LENGTH = 30024;
	private int checksum;
	private int packetSize;
	private int sequenceNum;
	private int ackNum;
	private int rcvWindow;
	//private int fileSize;
	
	private int dataPort;
	private PacketType packetType;
	private boolean isValidPacket;
	private byte[] dataBytes;
	private byte[] packetBytes;
	
	//报文类型
	public enum PacketType{
		INITIALTRANSFER(0),	//初始传输报文
		DATA(1),			//数据报文
		LSEND(2),			//发送文件命令报文
		LGET(3),			//下载命令报文
		ACK(4),				//ACK报文
		ACKALL(5);			//ACKALL报文
		
		private short value;//枚举值
		
		PacketType(int value) {
			this.value = (short)value;
		}
		
		public static PacketType getMessageType(short value) {

            return PacketType.values()[value];

        }
		
		public short getValue() {
			return value;
		}
		
		
	
	};
	
	//Packet分析
	public Packet(byte[] packetBytes) {
		this.packetBytes = packetBytes;
		decode(packetBytes);
	}
	
	//生成Packet 数据报
	public Packet(byte[] data,PacketType type,int ... options) {
		
		encode(data,type,options);
		decode(packetBytes);
	}
	
	//数据报解析
	public void decode(byte[] packetBytes) {
		//数据报文头的长度
		short headerSize = ByteBuffer.wrap(packetBytes, 22, 2).getShort();
		//数据的长度
		int dataSize = ByteBuffer.wrap(packetBytes, 18, 4).getInt();
		
		//数据报文的总长度
		packetSize = headerSize + dataSize;
		
		
	
		//获取数据包的类型，并分类处理
		packetType = PacketType.getMessageType(ByteBuffer.wrap(packetBytes, 8, 2).getShort());
		if (packetType == PacketType.LSEND) {
			dataBytes = Arrays.copyOfRange(packetBytes, headerSize, packetSize);
			
		} else if (packetType == PacketType.INITIALTRANSFER) {
			//获取数据报文中的RcvWindow信息
			rcvWindow = ByteBuffer.wrap(packetBytes, 14, 4).getInt();
			//获取数据报文中的dataPort信息  数据传输端口
			setDataPort(ByteBuffer.wrap(packetBytes,24,4).getInt());
			
		} else if (packetType == PacketType.DATA) {
			
			//获取数据报文中的SequenceNum,checksum,dataBytes（数据字节流）
			sequenceNum = ByteBuffer.wrap(packetBytes,0,4).getInt();	
			checksum = ByteBuffer.wrap(packetBytes,10,4).getInt();
			dataBytes = Arrays.copyOfRange(packetBytes, headerSize, packetSize);
			
			//数据检验
			setValidPacket((checksum == (calculateChecksum(dataBytes))));
			
			
			
		} else if (packetType == PacketType.ACK) {
			//获取数据报文中的AckNum和RcvWindow数值
			ackNum = ByteBuffer.wrap(packetBytes,4,4).getInt();
			rcvWindow = ByteBuffer.wrap(packetBytes, 14, 4).getInt();
			
		} else if (packetType == PacketType.LGET) {
			
			rcvWindow = ByteBuffer.wrap(packetBytes, 14, 4).getInt();
			dataBytes = Arrays.copyOfRange(packetBytes, headerSize, packetSize);
		}
		
	}
	
	//将传输的数据字节流与相关信息 打包成数据报文
	public void encode(byte[] data,PacketType type,int  ...options) {
		//默认的头信息长度
		
		//sequence(4),ack(4),type(2),checksum(4),rcvWindow(4),dataSize(4),headerSize(2),options();
		//头长度
		short headerSize = 0;
		headerSize += defaultHeaderSize;
		
		
		//传送数据
		byte[] sendData = null;
		
		
		if (data != null) {
			sendData = new byte[data.length];
			System.arraycopy(data,0,sendData,0,data.length);
		}
		
		
		 ByteBuffer byteBuffer;
		 //根据数据报文的不同类型，将不同数据信息加入至数据报文
		 if (type == PacketType.LSEND ) {
			 byteBuffer = putInBuffer(0, 0, type,0,headerSize,sendData);
			 
			 
		 } else if (type == PacketType.INITIALTRANSFER) {
			 headerSize += 4;
			 byteBuffer = putInBuffer( 0, 0, type, options[0], headerSize, sendData, options[1]);
			 
		 } else if(type == PacketType.LGET) {
			 
			 byteBuffer = putInBuffer( 0, 0, type, options[0], headerSize, sendData);
		 }
		 else if (type == PacketType.ACK){
			 byteBuffer = putInBuffer( 0, options[0], type, options[1], headerSize, sendData);
			 
		 } else if (type == PacketType.DATA){
			 byteBuffer = putInBuffer(options[0], 0, type, 0, headerSize, sendData);
			 
	 
		 } else {
			 byteBuffer = putInBuffer(0, 0, type, 0, headerSize, sendData);
		 }
		
		 //数据报文的字节流数组
		 packetBytes = byteBuffer.array();
		 //数据报文的长度
		 packetSize = packetBytes.length;
		
		
		
	}
	
	//将数据信息加入至数据报文
	public ByteBuffer putInBuffer(int sequenceNum,int ackNum,
			PacketType type,int rcvWindow,short headerSize,byte[] data,int ... options) {
		
		int packetLength = headerSize + (data == null ? 0 : data.length);
		ByteBuffer byteBuffer = ByteBuffer.allocate(packetLength);
		
		byteBuffer.putInt(sequenceNum);		
		byteBuffer.putInt(ackNum);			
		byteBuffer.putShort(type.getValue());	//数据包类型
		byteBuffer.putInt(calculateChecksum(data));	//数据checksum
		byteBuffer.putInt(rcvWindow);		
		
		//数据长度
		if (data != null) {
			byteBuffer.putInt(data.length);
		}
		else {
			byteBuffer.putInt(0);
		}
		
		//头长度
		byteBuffer.putShort(headerSize);
		
		//options
		for (int i:options) {
			byteBuffer.putInt(i);
		}
		
		//data
		if(data != null) {
			byteBuffer.put(data);
		}
		
		
		return byteBuffer;
		
	}
	
	//计算checksum
	public int calculateChecksum(byte[] dataBuffer) {
		
		int length = dataBuffer != null ? dataBuffer.length : 0;
		int sum = 0;
		int data;
		int i = 0;
		
		for (i = 0;i < length - 1;i += 2 ) {
			data = ((dataBuffer[i] << 8) & 0xFF00) | ((dataBuffer[i + 1]) & 0xFF);
			sum += data;
			
			if ((sum & 0xFFFF0000) > 0) {
				sum = sum & 0xFFFF;
				sum += 1;
			}
			
		}
		
		if (i < length) {
			data = ((dataBuffer[i] << 8) & 0xFF00);
			sum += data;
			
			if ((sum & 0xFFFF0000) > 0) {
				sum = sum & 0xFFFF;
				sum += 1;
			}
			
		}
		
		sum = ~sum;
		sum = sum & 0xFFFF;
		
		
		return sum;
		
		
		
	}



	public byte[] getPacketBytes() {
		return packetBytes;
	}



	public void setPacketBytes(byte[] packetBytes) {
		this.packetBytes = packetBytes;
	}



	public byte[] getDataBytes() {
		return dataBytes;
	}



	public void setDataBytes(byte[] dataBytes) {
		this.dataBytes = dataBytes;
	}





	public int getAckNum() {
		return ackNum;
	}



	public void setAckNum(int ackNum) {
		this.ackNum = ackNum;
	}



	public int getSequenceNum() {
		return sequenceNum;
	}



	public void setSequenceNum(int sequenceNum) {
		this.sequenceNum = sequenceNum;
	}



	public int getPacketSize() {
		return packetSize;
	}



	public void setPacketSize(int packetSize) {
		this.packetSize = packetSize;
	}

	public PacketType getPacketType() {
		return packetType;
	}

	public int getChecksum() {
		return checksum;
	}



	public void setChecksum(int checksum) {
		this.checksum = checksum;
	}

	
	public int getRcvWindow() {
		return rcvWindow;
	}

	public void setRcvWindow(int rcvWindow) {
		this.rcvWindow = rcvWindow;
	}

	public int getDataPort() {
		return dataPort;
	}

	public void setDataPort(int dataPort) {
		this.dataPort = dataPort;
	}

	public boolean isValidPacket() {
		return isValidPacket;
	}

	public void setValidPacket(boolean isValidPacket) {
		this.isValidPacket = isValidPacket;
	}
}
