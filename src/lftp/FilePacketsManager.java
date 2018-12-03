package lftp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

//文件数据管理类
public class FilePacketsManager {
	
	public static int packetSize = Packet.MAX_LENGTH;	
	public static int packetHeaderSize = Packet.defaultHeaderSize;//UDP数据包的头长度
	
	private int fileSize;					//文件大小
	private int dataSize;					//数据报文的数据长度
	private int packetCount;				//文件数据报的数量
	
	private String fileName;
	public Map<Integer,Timer> timerMap;		//数据报索引与定时器的哈希表
	private byte[] fileBytes;				//文件的字节流数组
	public FilePacketsManager(String largeFilePath) throws InvalidPathException,IOException{
		
		//获取文件的名字
		int last = largeFilePath.lastIndexOf("/");
		setFileName(largeFilePath.substring(last + 1));
		
		
		//得到文件后，讲文件转换至字节流数组
		
		Path path = Paths.get(largeFilePath);
		fileBytes = Files.readAllBytes(path);
		

		//文件字节大小
		fileSize = fileBytes.length;
		System.out.println("fileSize: " + fileSize);
		//UDP包中的实际数据长度
		dataSize = packetSize - packetHeaderSize;
		
		//总共要发的数据包数量
		packetCount = fileSize / dataSize + (fileSize % dataSize == 0 ? 0 : 1);
		
		
		
		System.out.println("Packetcount: " + packetCount);
		timerMap = new HashMap<>();
		
		
		

	}
	
	
	public FilePacketsManager() {
		
	
	}
	
	//返回文件大小
	public int getFileSize() {
		return fileSize;
	}
	
	//返回文件数据报的数量
	public int getPacketCount() {
		return packetCount;
	}
	
	//返回对应索引的文件数据报
	public byte[] getFilePacket(int i) {
		
		if (i < packetCount - 1) {
			return Arrays.copyOfRange(fileBytes,i * dataSize, (i + 1) * dataSize);
		}
		else {
			return Arrays.copyOfRange(fileBytes,i * dataSize, fileSize);
		}
		
		
	}


	public String getFileName() {
		return fileName;
	}


	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	
//	public byte[] getFilePacket1(int i) {
//		return filePacketMap.get(i);
//				
//	}
//	
//	public void putFilePacket(int index,byte[] data) {
//		filePacketMap.put(index, data);
//		
//	}
	
}
