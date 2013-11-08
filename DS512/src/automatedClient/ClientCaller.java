package automatedClient;

import java.net.Socket;
import java.net.UnknownHostException;
//import java.rmi.RMISecurityManager;

import java.util.*;
import java.io.*;

import NetPacket.NetPacket;


public class ClientCaller extends Thread 
{
	//static String message = "blank";
	//static ResourceManager rm = null;
	//static int Id, Cid;
	static int flightNum;
	//static int flightPrice;
	//static int flightSeats;
	//static boolean Room;
	//static boolean Car;
	//static int price;
	static int customerID;
	static int numRooms;
	static int numCars;
	static String location;
	//static Vector arguments;
	//static BufferedReader stdin;
	//static int transactionID = -1; 
	
	Client obj;
	Socket socket;

	private int packetID;
	PrintWriter out = null;
	BufferedReader in = null;
	LinkedList<NetPacket> toSendToServer;
	
	static LinkedList<String> globalCommands;
	static LinkedList<String> flightCommands;
	static LinkedList<Integer> CustomersID;
	static LinkedList<Integer> flightNumbers;
	static LinkedList<String> locations;
	
	static LinkedList<Integer> dynamicCustomersID;
	static LinkedList<Integer> dynamicFlightNumbers;
	static LinkedList<String> dynamicLocations;
	
	static final int FLIGHT = 1;
	static final int GLOBAL = 2;
	
	static final int SHORT_FLIGHT_TXN = 1;
	static final int SHORT_GLOBAL_TXN = 2;
	static final int AVERAGE_FLIGHT_TXN = 3;
	static final int AVERAGE_GLOBAL_TXN = 4;
	static final int LONG_FLIGHT_TXN = 5;
	static final int LONG_GLOBAL_TXN = 6;
	
	public static final int NUMBER_OF_TRANSACTIONS = 100;
	
	public ClientCaller(String host, int port) {
		super("Sup!");
		String[] args = {"teaching", "10121"};
		obj = new Client(args);
		try {
			socket = new Socket(host, port);
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
		}	catch (UnknownHostException e) {
			System.err.println("Don't know about host: " + host + ".");
			System.exit(1);
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Couldn't get I/O for "
					+ "the connection to: " + host + ".");
			System.exit(1);
		}
		toSendToServer = new LinkedList<NetPacket>();
		packetID = 0;
		
		globalCommands = new LinkedList<String>(Arrays.asList
				("newflight","newcar","newroom","newcusomterid","deleteflight","deletecar",
				"deleteroom","deletecustomer","queryflight","querycar","queryroom","querycustomer","queryflightprice",
				"querycarprice","queryroomprice","reserveflight","reservecar","reserveroom","itinerary"));
		
		flightCommands = new LinkedList<String>(Arrays.asList
				("newflight","deleteflight","queryflight","queryflightprice","reserveflight"));
		
		CustomersID = new LinkedList<Integer>(Arrays.asList(11221, 11332, 33221, 14421, 11551, 88221, 12345, 78985, 78945));
		
		flightNumbers = new LinkedList<Integer>(Arrays.asList(101, 102, 103, 104, 105, 106, 107, 108, 109));
		
		locations = new LinkedList<String>(Arrays.asList
				("miami","paris","sydney","beijing","moscou"));
		
	}
	
	public void run() {

		synchronized (toSendToServer) {
			boolean readFromServer = true;
			
			while (!toSendToServer.isEmpty()) {
				NetPacket toSend = toSendToServer.removeFirst();
				out.println(toSend.fromPacketToString());				
			}
			if (readFromServer) {
				try {
					String line = in.readLine();
					NetPacket answer = NetPacket.fromStringToPacket(line);
					decode(answer);
				} catch (IOException e) {
					// 	TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}
	
	public void decode(NetPacket p) {
		
		if (p.getType().equalsIgnoreCase("Throughput")) {
			String length = p.getContent()[1];
			int baseValue;
			if (length.equalsIgnoreCase("SHORT")) {
				baseValue = ClientCaller.SHORT_FLIGHT_TXN;
			} else if (length.equalsIgnoreCase("AVERAGE")) {
				baseValue = ClientCaller.AVERAGE_FLIGHT_TXN;
			} else {
				baseValue = ClientCaller.LONG_FLIGHT_TXN;
			}
			if (p.getContent()[0].equalsIgnoreCase("Global")) {
				newTest(baseValue+1, 0);
			} else if (p.getContent()[0].equalsIgnoreCase("Single")) {
				newTest(baseValue, 0);
			}
		} else if (p.getType().equalsIgnoreCase("ResponseTime")) {
			String length = p.getContent()[2];
			int baseValue;
			if (length.equalsIgnoreCase("SHORT")) {
				baseValue = ClientCaller.SHORT_FLIGHT_TXN;
			} else if (length.equalsIgnoreCase("AVERAGE")) {
				baseValue = ClientCaller.AVERAGE_FLIGHT_TXN;
			} else {
				baseValue = ClientCaller.LONG_FLIGHT_TXN;
			}
			if (p.getContent()[0].equalsIgnoreCase("Global")) {
				newTest(baseValue+1, Long.parseLong(p.getContent()[1]));
			} else if (p.getContent()[0].equalsIgnoreCase("Single")) {
				newTest(baseValue, Long.parseLong(p.getContent()[1]));
			}			
		} else if (p.getType().equalsIgnoreCase("Startup")) {
			startup();
		}
		
	}
	
	
	public void startup() {
		obj.readCommand("start");
		for (int cID: CustomersID) {
			obj.readCommand("newcustomerid, " + cID);
		}

		for (int fID: flightNumbers) {
			obj.readCommand("newflight, " + fID + ", 100, 1000");
		}

		for (String loc: locations) {
			obj.readCommand("newcar, " + loc + ", 100, 200");
			obj.readCommand("newroom, " + loc + ", 100, 200");
		}
		obj.readCommand("commit");
		String[] args = {"Done"};
		packetToSend("Startup", args);
		
	}
	
	
	public void newTest(int testType, long delay)
	{
		switch(testType){
		case SHORT_FLIGHT_TXN:
			obj.readCommand("start");
			
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		case SHORT_GLOBAL_TXN:
		case AVERAGE_FLIGHT_TXN:
		case AVERAGE_GLOBAL_TXN:
		case LONG_FLIGHT_TXN:
		case LONG_GLOBAL_TXN:
		default:
		}
	}
	
	private static String commandGenerator(int commandType){		
		int rng = 0;
		Random r = new Random();
		String command = "";
		String completeCommand = "";
		boolean dynamic = false;
		
		switch(commandType){
		case FLIGHT:
			rng = r.nextInt(flightCommands.size());
			command = flightCommands.get(rng);
		case GLOBAL:
			rng = r.nextInt(globalCommands.size());
			command = globalCommands.get(rng);
		default:
		}
		
		switch(findChoice(command)){
		case 2: //new flight
			flightNum = r.nextInt(100);
			completeCommand = command + "," + flightNum+ "," + r.nextInt(100) + "," + r.nextInt(100);
			dynamicFlightNumbers.add(flightNum);
		case 3: //new car
			location = "miami"+r.nextInt(100);
			completeCommand = command + "," + location + "," + r.nextInt(100) + "," + r.nextInt(100);
			dynamicLocations.add(location);
		case 4: //new room
			location = "miami"+r.nextInt(100);
			completeCommand = command + "," + location + "," + r.nextInt(100) + "," + r.nextInt(100);
			dynamicLocations.add(location);
		/*case 5: //new customer*/
		case 6: //delete flight
			flightNum = dynamicFlightNumbers.get(r.nextInt(dynamicFlightNumbers.size()));
			completeCommand = command + "," + flightNum;
			dynamicFlightNumbers.remove(flightNum);
		case 7: //delete car
			location = dynamicLocations.get(r.nextInt(dynamicLocations.size()));
			numCars = r.nextInt(100);
			completeCommand = command + "," + location + "," + numCars;
			dynamicLocations.remove(location);
		case 8: //delete room
			location = dynamicLocations.get(r.nextInt(dynamicLocations.size()));
			numRooms = r.nextInt(100);
			completeCommand = command + "," + location + "," + numRooms;
			dynamicLocations.remove(location);
		case 9: //delete customer
			customerID = dynamicCustomersID.get(r.nextInt(dynamicCustomersID.size()));
			completeCommand = command + "," + customerID;
			dynamicFlightNumbers.remove(customerID);
		case 10: //query flight
			flightNum = flightNumbers.get(r.nextInt(flightNumbers.size()));
			completeCommand = command + "," + flightNum;
		case 11: //query car
			location = locations.get(r.nextInt(locations.size()));
			completeCommand = command + "," + location;
		case 12: //query room
			location = locations.get(r.nextInt(locations.size()));
			completeCommand = command + "," + location;
		case 13: //query customer
			customerID = CustomersID.get(r.nextInt(CustomersID.size()));
			completeCommand = command + "," + customerID;
		case 14: //query flight price
			flightNum = flightNumbers.get(r.nextInt(flightNumbers.size()));
			completeCommand = command + "," + flightNum;
		case 15: //query car price
			location = locations.get(r.nextInt(locations.size()));
			completeCommand = command + "," + location;
		case 16: //query room price
			location = locations.get(r.nextInt(locations.size()));
			completeCommand = command + "," + location;
		case 17: //reserve flight
			dynamic = r.nextBoolean();
			if(dynamic){
				customerID = dynamicCustomersID.get(r.nextInt(dynamicCustomersID.size()));
			}else{
				customerID = CustomersID.get(r.nextInt(CustomersID.size()));
			}
			flightNum = flightNumbers.get(r.nextInt(flightNumbers.size()));			
			completeCommand = command + "," + customerID + "," + flightNum;
		case 18: //reserve car
			dynamic = r.nextBoolean();
			if(dynamic){
				customerID = dynamicCustomersID.get(r.nextInt(dynamicCustomersID.size()));
			}else{
				customerID = CustomersID.get(r.nextInt(CustomersID.size()));
			}
			location = locations.get(r.nextInt(locations.size()));
			numCars = r.nextInt(5);
			completeCommand = command + "," + customerID + "," + location + "," + numCars;
		case 19: //reserve room
			dynamic = r.nextBoolean();
			if(dynamic){
				customerID = dynamicCustomersID.get(r.nextInt(dynamicCustomersID.size()));
			}else{
				customerID = CustomersID.get(r.nextInt(CustomersID.size()));
			}
			location = locations.get(r.nextInt(locations.size()));
			numRooms = r.nextInt(5);
			completeCommand = command + "," + customerID + "," + location + "," + numRooms;
		case 20: //itinerary
			int n = 0;
			boolean Car = false;
			boolean Room = false;		
			n = flightNumbers.get(r.nextInt(flightNumbers.size()));			
			dynamic = r.nextBoolean();
			if(dynamic){
				customerID = dynamicCustomersID.get(r.nextInt(dynamicCustomersID.size()));
			}else{
				customerID = CustomersID.get(r.nextInt(CustomersID.size()));
			}
			completeCommand = command + "," + customerID;
			for (int i = 0; i < n; i++){
				completeCommand += "," + flightNumbers.get(r.nextInt(flightNumbers.size()));
			}
			location = locations.get(r.nextInt(locations.size()));
			Car = r.nextBoolean();
			Room = r.nextBoolean();
			completeCommand += location + "," + Car + "," + Room;
		case 21: //new customer id
			customerID = r.nextInt(10000);
			completeCommand = command + "," + customerID;
			dynamicCustomersID.add(customerID);
		default:
		}
		
		return completeCommand;
		
	}
	
	public static int findChoice(String argument)
	{
		/*if (argument.compareToIgnoreCase("help")==0)
			return 1;*/
		if(argument.compareToIgnoreCase("newflight")==0)
			return 2;
		else if(argument.compareToIgnoreCase("newcar")==0)
			return 3;
		else if(argument.compareToIgnoreCase("newroom")==0)
			return 4;
		else if(argument.compareToIgnoreCase("newcustomer")==0)
			return 5;
		else if(argument.compareToIgnoreCase("deleteflight")==0)
			return 6;
		else if(argument.compareToIgnoreCase("deletecar")==0)
			return 7;
		else if(argument.compareToIgnoreCase("deleteroom")==0)
			return 8;
		else if(argument.compareToIgnoreCase("deletecustomer")==0)
			return 9;
		else if(argument.compareToIgnoreCase("queryflight")==0)
			return 10;
		else if(argument.compareToIgnoreCase("querycar")==0)
			return 11;
		else if(argument.compareToIgnoreCase("queryroom")==0)
			return 12;
		else if(argument.compareToIgnoreCase("querycustomer")==0)
			return 13;
		else if(argument.compareToIgnoreCase("queryflightprice")==0)
			return 14;
		else if(argument.compareToIgnoreCase("querycarprice")==0)
			return 15;
		else if(argument.compareToIgnoreCase("queryroomprice")==0)
			return 16;
		else if(argument.compareToIgnoreCase("reserveflight")==0)
			return 17;
		else if(argument.compareToIgnoreCase("reservecar")==0)
			return 18;
		else if(argument.compareToIgnoreCase("reserveroom")==0)
			return 19;
		else if(argument.compareToIgnoreCase("itinerary")==0)
			return 20;
		else if (argument.compareToIgnoreCase("newcustomerid")==0)
			return 21;
		/*else if (argument.compareToIgnoreCase("quit")==0)
			return 22;
		else if (argument.compareToIgnoreCase("start")==0)
			return 23;
		else if (argument.compareToIgnoreCase("commit")==0)
			return 24;
		else if (argument.compareToIgnoreCase("abort")==0)
			return 25;	*/	
		else
			return 666;

	}
	
	public void closeConnection() {
		try {
			out.close();
			in.close();
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void packetToSend(NetPacket packet) {
		toSendToServer.addLast(packet);
	}

	public void packetToSend(String type, String[] content) {
		packetToSend(new NetPacket(1, packetID, type, content));
		packetID++;
	}
	
}
