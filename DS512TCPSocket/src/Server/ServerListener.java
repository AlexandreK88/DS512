package Server;
import java.net.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Scanner;
import java.io.*;

public class ServerListener {

	private static final int MAX_THREADS_COUNT = 10;
	private static ServerSocket serverSocket = null;
	private static int currentThreadID = 1;
	private static LinkedList<ServerThread> listOfServers;

	public static void main(String[] args) throws IOException {
		// The resource manager.
		ResourceManagerImpl rm;
		try {
			rm = new ResourceManagerImpl();
			// The different threads that will each process their own client/middlewareThread requests.
			listOfServers = new LinkedList<ServerThread>();
			String server = "localhost";
			int port = 10221;
			
			if (args.length == 1) {
				port = Integer.parseInt(args[0]);
			}
			// Allows a basic server control (exit/quit command implemented).
			ServerUI serverInterface = new ServerUI(port, server);
			try {
				serverSocket = new ServerSocket(port);
			} catch (IOException e) {
				System.err.println("Could not listen on port: " + port + ".");
				System.exit(-1);
			}
			System.out.println("System ready to receive client connections.");
			// While the server was not requested to close.
			while (serverInterface.isUp()) {
				int i = 0;
				for (; i < listOfServers.size(); i++) {
					// Frees some threads.
					if (!listOfServers.get(i).isWorking()) {
						ServerThread thread = listOfServers.remove(i);
						thread.close();
						i--;
					}
				}
				if (i < MAX_THREADS_COUNT) {
					// If a client/middleThread tries to connect, a serverthread is provided to treat it.
					Socket s = serverSocket.accept();
					if (serverInterface.isUp()) {
						listOfServers.add(new ServerThread(s,currentThreadID,rm));
						listOfServers.get(i).start();
						currentThreadID++;
					} else {
						s.close();
					}
				}
			}
			// Closing the server and all the threads.
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


