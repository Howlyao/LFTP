package lftp;

import java.util.TimerTask;


//定时器类
public class PacketTimerTask extends TimerTask{
	private int sequenceNum;
	
	public PacketTimerTask(int sequenceNum) {
		this.sequenceNum = sequenceNum;
		
	}
	
	public int getSequenceNum() {
		return sequenceNum;
	}
	
	@Override
	public void run(){
		
	}
}
