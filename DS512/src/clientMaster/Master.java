package clientMaster;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Random;

public class Master {
	
	public static final int MAX_NUMBER_OF_CLIENTS = 48;
	static ServerSocket serverSocket = null;
	static LinkedList<MCPipe> commMC;
	static BufferedReader stdin;
	static Integer awaitedResponses = 0;
	static int testCounter;
	static Random r;
	
	public static void main (String[] args) throws IOException  {
		
		stdin = new BufferedReader(new InputStreamReader(System.in));
		boolean performanceTestsCompleted = false;
		String command;
		testCounter = 1;
		int pipeCounter = 1;
		r = new Random();
		
		try {

			commMC = new LinkedList<MCPipe>();
			int port = 10221;
			
			/*if (args.length == 1) {
				port = Integer.parseInt(args[0]);
			}*/
			if (args.length == 0) {
				stdin = new BufferedReader(new InputStreamReader(System.in));
			} else {
				File file = new File(args[0]);
				stdin = new BufferedReader(new FileReader(file));
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
			shufflePipes();
			String[] filler = {"AllFlightsRoomsAndCars"};
			commMC.get(0).packetToSend("Startup", filler);
			awaitedResponses++;
			
			while(awaitedResponses > 0) {
				try {
					System.out.println("Waiting for " + awaitedResponses + " responses.");
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			while (!performanceTestsCompleted) {
				try{
					// read the next command
					// Commands have the form:
					// ResponseTime [Global/Single] [Delay] [SHORT/AVERAGE/LONG] [Number of clients]
					// Throughput [Global/Single] [SHORT/AVERAGE/LONG] [Number of clients]
					// Done
					command =stdin.readLine();
					String[] commandDetails = command.split(" ");
					if (commandDetails[0].equalsIgnoreCase("ResponseTime")) {
						if (commandDetails.length != 5) {
							System.out.println("Wrong number of arguments.");
						} else {
							awaitedResponses = Integer.parseInt(commandDetails[commandDetails.length-1]);
							System.out.println("Number of responses awaited is " + awaitedResponses);
							for (int i = 0; (i < awaitedResponses && i < MAX_NUMBER_OF_CLIENTS); i++) {
								if (commandDetails[1].equalsIgnoreCase("Global") || commandDetails[1].equalsIgnoreCase("Single")
								&& Integer.parseInt(commandDetails[2]) >= 0 
								&& (commandDetails[3].equalsIgnoreCase("SHORT") || commandDetails[3].equalsIgnoreCase("AVERAGE") || commandDetails[3].equalsIgnoreCase("LONG"))) {
									String[] details = {commandDetails[1], commandDetails[2], commandDetails[3], Integer.toString(testCounter)};
									commMC.get(i).packetToSend(commandDetails[0], details);
								} else {
									System.out.println("Wrong parameters");
								}
							}
						}
					} else if (commandDetails[0].equalsIgnoreCase("Throughput")) {
						if (commandDetails.length != 4) {
							System.out.println("Wrong number of arguments.");
						} else {
							awaitedResponses = Integer.parseInt(commandDetails[commandDetails.length-1]);
							System.out.println("Number of responses awaited is " + awaitedResponses);
							for (int i = 0; (i < awaitedResponses && i < MAX_NUMBER_OF_CLIENTS); i++) {
								if (commandDetails[1].equalsIgnoreCase("Global") || commandDetails[1].equalsIgnoreCase("Single")
								&& (commandDetails[2].equalsIgnoreCase("SHORT") || commandDetails[2].equalsIgnoreCase("AVERAGE") || commandDetails[2].equalsIgnoreCase("LONG"))) {
									String[] details = {commandDetails[1], commandDetails[2], Integer.toString(testCounter)};
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
					System.out.println(awaitedResponses + " responses awaited.");
					if (awaitedResponses > MAX_NUMBER_OF_CLIENTS) {
						awaitedResponses = MAX_NUMBER_OF_CLIENTS;
					}
					while(true) {
						synchronized(awaitedResponses) {
							if (awaitedResponses <= 0) {
								break;
							}
						}
						try {
							System.out.println("Waiting for " + awaitedResponses + " responses.");
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					shufflePipes();
					testCounter++;
				}
				catch (IOException io){
					System.out.println("Unable to read from standard in");
					System.exit(1);
				}
				catch (Exception e) {
					System.out.println(e.getClass().toString());
					System.out.println("Something is wrong with the command.");
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
		LinkedList<LinkedList<TestResult>> resultsPerTest = new LinkedList<LinkedList<TestResult>>();
		for (int i = 0; i < testCounter; i++ ){
			resultsPerTest.addLast(new LinkedList<TestResult>());
		}
		for (MCPipe client: commMC) {
			for (TestResult tr: client.getTestResults()) {
				resultsPerTest.get(tr.getPosition()).add(tr);
			}
		}
		for (LinkedList<TestResult> completeResults: resultsPerTest) {
			if (completeResults.size() <= 0) {
				continue;
			}
			TestResult first = completeResults.getFirst();
			System.out.println("Test number " + first.getPosition() + "\nTest name " + first.getType());
			for (String metric: first.getMetric()) {
				System.out.println("Parameter: " + metric);
			}
			long totalDuration = 0;
			int count = 0;
			for (TestResult r: completeResults) {
				String[] times = r.getResults().split(",");
				for (String t: times) {
					t = t.trim();
					if (t.length() > 0) {
						count++;
						totalDuration += Long.parseLong(t);
					}
				}
			}
			if (first.getType().equalsIgnoreCase("Throughput")) {
				System.out.println("On average, " + (count*1000/totalDuration) + " requests/second were treated.");
			} else if (first.getType().equalsIgnoreCase("ResponseTime")) {
				System.out.println("On average, " + (totalDuration/count) + " milliseconds/request was the response time.");
			}
		}
	}
	
	static void shufflePipes() {
		if (commMC != null && r != null) {
			System.out.println("I am shufflin', shufflin'..");
			LinkedList<MCPipe> pipes = new LinkedList<MCPipe>();
			while (!commMC.isEmpty()) {
				pipes.add(commMC.remove(r.nextInt(commMC.size())));
			}
			commMC.addAll(pipes);
		}
	}
}
