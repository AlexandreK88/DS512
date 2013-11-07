package clientMaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

public class Master {
	
	public static final int MAX_NUMBER_OF_CLIENTS = 10;
	static ServerSocket serverSocket = null;
	static LinkedList<MCPipe> commMC;
	static BufferedReader stdin;
	static int awaitedResponses = 0;
	
	public static void main (String[] args) throws IOException  {
		
		stdin = new BufferedReader(new InputStreamReader(System.in));
		boolean performanceTestsCompleted = false;
		String command;
		int testCounter = 1;
		int pipeCounter = 1;
		
		
		try {

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
			while (commMC.size() < MAX_NUMBER_OF_CLIENTS) {
				Socket s = serverSocket.accept();
				MCPipe client = new MCPipe(s,pipeCounter);  
				client.start();
				commMC.add(client);
				pipeCounter++;
			}
			
			String[] filler = {"AllFlightsRoomsAndCars"};
			commMC.get(0).packetToSend("Startup", filler);
			awaitedResponses++;
			
			while(awaitedResponses > 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			while (!performanceTestsCompleted) {
				try{
					//read the next command
					command =stdin.readLine();
					String[] commandDetails = command.split(" ");
					if (commandDetails[0].equalsIgnoreCase("ResponseTime")) {
						if (commandDetails.length != 4) {
							System.out.println("Wrong number of arguments.");
						} else {
							int awaitedResponses = Integer.parseInt(commandDetails[3]);
							for (int i = 0; (i < awaitedResponses || i < MAX_NUMBER_OF_CLIENTS); i++) {
								if (commandDetails[1].equals("Global") || commandDetails[1].equals("Single")
								&& Integer.parseInt(commandDetails[2]) >= 0) {
									String[] details = {commandDetails[1], commandDetails[2], Integer.toString(testCounter)};
									commMC.get(i).packetToSend(commandDetails[0], details);
								} else {
									System.out.println("Wrong parameters");
								}
							}
						}
					} else if (commandDetails[0].equalsIgnoreCase("Throughput")) {
						if (commandDetails.length != 3) {
							System.out.println("Wrong number of arguments.");
						} else {
							awaitedResponses = Integer.parseInt(commandDetails[2]);
							for (int i = 0; (i < awaitedResponses|| i < MAX_NUMBER_OF_CLIENTS); i++) {
								if (commandDetails[1].equals("Global") || commandDetails[1].equals("Single")) {
									String[] details = {commandDetails[1], Integer.toString(testCounter)};
									commMC.get(i).packetToSend(commandDetails[0], details);
								} else {
									System.out.println("Wrong parameters");
								}
							}
						}
					} else if (commandDetails[0].equalsIgnoreCase("Done")) {
						performanceTestsCompleted = true;	
					} else {
						System.out.println("Unknown command");
					}
					if (awaitedResponses > MAX_NUMBER_OF_CLIENTS) {
						awaitedResponses = MAX_NUMBER_OF_CLIENTS;
					}
					while(awaitedResponses > 0) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					testCounter++;
				}
				catch (IOException io){
					System.out.println("Unable to read from standard in");
					System.exit(1);
				}
				catch (Exception e) {
					System.out.println(e.getClass().toString());
					System.out.println("Something wrong the command");
					awaitedResponses = 0;
				}
			}
			// Closing the server and all the threads.
			System.out.println("System is fully ready.");
			System.out.println("Please enter the test you want to pass");
			for (MCPipe pipe: commMC) {
				if (pipe != null && pipe.isAlive()) {
					pipe.close();
				}
			}
			serverSocket.close();
			takeCareOfTestResults();
			System.exit(0);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	static void takeCareOfTestResults() {
		
	}
}
