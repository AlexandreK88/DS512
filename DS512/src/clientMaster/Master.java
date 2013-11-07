package clientMaster;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

public class Master {
	
	static ServerSocket serverSocket;
	static LinkedList<MCPipe> commMC;
	
	public static void main (String[] args) {
		
		try {
			// The different threads that will each process their own client/middlewareThread requests.
			//listOfServers = new LinkedList<ServerThread>();
			String server = "localhost";
			commMC = new LinkedList<MCPipe>();
			int port = 10221;
			
			if (args.length == 1) {
				port = Integer.parseInt(args[0]);
			}
			try {
				serverSocket = new ServerSocket(port);
			} catch (IOException e) {
				System.err.println("Could not listen on port: " + port + ".");
				System.exit(-1);
			}
			System.out.println("System ready to receive client connections.");
			// While the server was not requested to close.
			while (commMC.size() < 40) {
				Socket s = serverSocket.accept();
				commMC.add(new MCPipe(s));
			}
			// Closing the server and all the threads.
			System.out.println("System is fully ready.");
			System.out.println("Please enter the test you want to pass");
			for (ServerThread thread: listOfServers) {
				if (thread != null && thread.isAlive()) {
					thread.close();
				}
			}
			serverSocket.close();
			System.exit(0);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
}
