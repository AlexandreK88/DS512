package Middleware;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

import Server.ServerThread;


public class MiddleListener {
	
	
	private static LinkedList<MiddleThread> listOfMids;
	private static int currentThreadID = 1;
	private static final int MAX_THREADS_COUNT = 10;
	private static ServerSocket serverSocket = null;
	
	public static void main(String args[]) {
		
		
		listOfMids = new LinkedList<MiddleThread>();

		// Figure out where server is running
		
		String server1 = "localhost";
		String server2 = "localhost"; 
		String server3 = "localhost";
		String serverMW = "localhost";
		int port1 = 1099;
		int port2 = 1099;
		int port3 = 1099;
		int portMW = 1099;


		if(args.length == 1){
			serverMW = serverMW + ":" + args[0];
			portMW = Integer.parseInt(args[0]);
		} else if(args.length == 7){

			serverMW = serverMW + ":" + args[0];
			portMW = Integer.parseInt(args[0]);

			//First arguments indicate server and port of the Flight RMI
			server1 = args[1];
			port1 = Integer.parseInt(args[2]);

			//Next arguments indicate server and port of the Car RMI
			server2 = args[3];
			port2 = Integer.parseInt(args[4]);

			//Last arguments indicate server and port of the Room RMI
			server3 = args[5];
			port3 = Integer.parseInt(args[6]);
		} else{
			System.err.println ("Wrong usage");
			System.out.println ("Usage: java middleware [port]"
					+ "[(flight)host (flight)port (car)host (car)portCar (room)host (room)port]");       
			System.exit(1);
		}
		MidUI serverInterface = new MidUI(portMW, "localhost");
		
		try {
			serverSocket = new ServerSocket(portMW);
		} catch (IOException e) {
			System.err.println("Could not listen on port: " + portMW + ".");
			System.exit(-1);
		}
		System.out.println("System ready to receive client connections.");
		while (serverInterface.isUp()) {
			int i = 0;
			for (; i < listOfMids.size(); i++) {
				if (!listOfMids.get(i).isAlive()) {
					MiddleThread thread = listOfMids.remove(i);
					thread.close();
					i--;
				}
			}
			if (i < MAX_THREADS_COUNT) {
				Socket s;
				try {
					s = serverSocket.accept();
					if (serverInterface.isUp()) {
						listOfMids.add(new MiddleThread(s,currentThreadID, server1, port1, server2, port2, server3, port3));
						listOfMids.get(i).start();
						currentThreadID++;
					} else {
						try {
							s.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		for (MiddleThread thread: listOfMids) {
			if (thread != null && !thread.isWorking()) {
				thread.close();
			}
		}
		try {
			serverSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.exit(0);
	}
}
