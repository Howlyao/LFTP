package lftp;

import java.net.DatagramSocket;
import java.net.SocketException;

public class PortManager {
	public static boolean isPortUsing(int port) {
		
		boolean flag = false;
		
		try {
			
			DatagramSocket datagramSocket = new DatagramSocket(port);
			datagramSocket.close();
			
		}catch(SocketException e) {
			flag = true;
		}
		
		
		return flag;
	}
}
