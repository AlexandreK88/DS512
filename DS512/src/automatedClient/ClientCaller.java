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
	static Random r = new Random();
	//static Vector arguments;
	//static BufferedReader stdin;
	//static int transactionID = -1; 

	static Client obj;
	Socket socket;

	private int packetID;
	PrintWriter out = null;
	BufferedReader in = null;
	LinkedList<NetPacket> toSendToServer;

	static LinkedList<String> globalCommands;
	static LinkedList<String> flightCommands;
	static LinkedList<Integer> customersID;
	static LinkedList<Integer> flightNumbers;
	static LinkedList<String> locations;

	static LinkedList<Integer> dynamicCustomersID;
	static LinkedList<Integer> dynamicFlightNumbers;
	static LinkedList<String> dynamicCarLocations;
	static LinkedList<String> dynamicRoomLocations;

	static final int FLIGHT = 1;
	static final int GLOBAL = 2;

	static final int SHORT_FLIGHT_TXN = 1;
	static final int SHORT_GLOBAL_TXN = 2;
	static final int AVERAGE_FLIGHT_TXN = 3;
	static final int AVERAGE_GLOBAL_TXN = 4;
	static final int LONG_FLIGHT_TXN = 5;
	static final int LONG_GLOBAL_TXN = 6;

	public static final int NUMBER_OF_TRANSACTIONS = 100;

	public static void initCaller() {
		globalCommands = new LinkedList<String>(Arrays.asList
				("newflight","newcar","newroom","newcustomerid","deleteflight","deletecar",
						"deleteroom","deletecustomer","queryflight","querycar","queryroom","querycustomer","queryflightprice",
						"querycarprice","queryroomprice","reserveflight","reservecar","reserveroom","itinerary"));

		flightCommands = new LinkedList<String>(Arrays.asList
				("newflight","deleteflight","queryflight","queryflightprice","reserveflight"));

		customersID = new LinkedList<Integer>();
		for (int i = 19376; i < 101210; i+=163) {
			customersID.add(i);
		}
		
		flightNumbers = new LinkedList<Integer>();
		for (int i = 101; i < 200; i++) {
			flightNumbers.add(i);
		}

		locations = new LinkedList<String>(Arrays.asList
				("miami","paris","sydney","beijing","moscow","sanfrancisco","montreal","london","tokyo",
				"melbourne","madrid","stpetersburg","toronto","chicago","losangeles","berlin","stockholm",
				"helsinki","lisbonne","rome","athenes","marseilles","toulouse","lyon","versaille","capetown",
				"marakesh","caire","alger","kiev", "vancouver","seattle","lasvegas","boston","newyork"));

		dynamicCustomersID = new LinkedList<Integer>() ;
		dynamicFlightNumbers = new  LinkedList<Integer>();
		dynamicCarLocations = new LinkedList<String>();
		dynamicRoomLocations = new LinkedList<String>();
	}

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

	}

	public void run() {
		while (true) {
			boolean readFromServer = true;
			synchronized (toSendToServer) {


				while (!toSendToServer.isEmpty()) {
					NetPacket toSend = toSendToServer.removeFirst();
					System.out.println("Sending to server that " + toSend.getType());
					out.println(toSend.fromPacketToString());
					readFromServer = true;
				}
			}
			if (readFromServer) {
				try {
					String line;
					if (in.ready()) {
						line = in.readLine();
					} else { 
						continue;
					}
					NetPacket answer = NetPacket.fromStringToPacket(line);

					if (answer == null) {closeConnection();}

					decode(answer);
					readFromServer = false;
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

		for (int cID: customersID) {
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
		System.out.println("Sending a response that the startup is done.");
		packetToSend("Startup", args);
		System.out.println("Response sent.");

	}

	public LinkedList<Long> newTest(int testType, long delay)
	{
		LinkedList<Long> timeResults = new LinkedList<Long>();
		for(int i = 0; i < NUMBER_OF_TRANSACTIONS; i++){
			obj.readCommand("start");
			Date start = new Date();
			switch(testType){
			case SHORT_FLIGHT_TXN:			
				for(int j = 0; j < 3; j++){
					obj.readCommand(commandGenerator(1));
				}
				break;
			case SHORT_GLOBAL_TXN:
				for(int j = 0; j < 3; j++){
					obj.readCommand(commandGenerator(2));
				}
				break;
			case AVERAGE_FLIGHT_TXN:
				for(int j = 0; j < 8; j++){
					obj.readCommand(commandGenerator(1));
				}
				break;
			case AVERAGE_GLOBAL_TXN:
				for(int j = 0; j < 8; j++){
					obj.readCommand(commandGenerator(2));
				}
				break;
			case LONG_FLIGHT_TXN:
				for(int j = 0; j < 15; j++){
					obj.readCommand(commandGenerator(1));
				}
				break;
			case LONG_GLOBAL_TXN:
				for(int j = 0; j < 15; j++){
					obj.readCommand(commandGenerator(2));
				}
				break;
			default:
			}
			obj.readCommand("commit");
			Date end = new Date();
			timeResults.add(end.getTime()-start.getTime());
			if(delay - (end.getTime()-start.getTime()) > 0){
				try {
					Thread.sleep(delay - (end.getTime()-start.getTime()) );
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return timeResults;
	}

	public static String commandGenerator(int commandType){		
		int rng = 0;
		String command = "";
		String completeCommand = "";
		boolean dynamic = false;

		switch(commandType){
		case FLIGHT:
			rng = r.nextInt(flightCommands.size());
			command = flightCommands.get(rng);
			break;
		case GLOBAL:
			rng = r.nextInt(globalCommands.size());
			command = globalCommands.get(rng);
			break;
		default:

		}
		switch(findChoice(command)){
		case 2: //new flight
			flightNum = r.nextInt(1000);
			completeCommand = command + "," + flightNum + "," + r.nextInt(100) + "," + r.nextInt(100);
			dynamicFlightNumbers.add(flightNum);
			break;
		case 3: //new car
			location = "miami"+r.nextInt(100);
			completeCommand = command + "," + location + "," + r.nextInt(100) + "," + r.nextInt(100);
			dynamicCarLocations.add(location);
			break;
		case 4: //new room
			location = "miami"+r.nextInt(100);
			completeCommand = command + "," + location + "," + r.nextInt(100) + "," + r.nextInt(100);
			dynamicRoomLocations.add(location);
			break;
			/*case 5: //new customer*/
		case 6: //delete flight
			if (!dynamicFlightNumbers.isEmpty()) {
				flightNum = dynamicFlightNumbers.get(r.nextInt(dynamicFlightNumbers.size()));
				completeCommand = command + "," + flightNum;
				dynamicFlightNumbers.remove((Integer)flightNum);
			} else {
				flightNum = r.nextInt(100);
				completeCommand = "newflight," + flightNum+ "," + r.nextInt(100) + "," + r.nextInt(100);
				dynamicFlightNumbers.add(flightNum);
			}
			break;
		case 7: //delete car
			if (!dynamicCarLocations.isEmpty()) {
				location = dynamicCarLocations.get(r.nextInt(dynamicCarLocations.size()));
				numCars = r.nextInt(100);
				completeCommand = command + "," + location + "," + numCars;
				dynamicCarLocations.remove(location);
			} else {
				location = "miami"+r.nextInt(100);
				completeCommand = "newcar," + location + "," + r.nextInt(100) + "," + r.nextInt(100);
				dynamicCarLocations.add(location);
			}
			break;
		case 8: //delete room
			if(!dynamicRoomLocations.isEmpty()){
				location = dynamicRoomLocations.get(r.nextInt(dynamicRoomLocations.size()));
				numRooms = r.nextInt(100);
				completeCommand = command + "," + location + "," + numRooms;
				dynamicRoomLocations.remove(location);
			}
			else{
				location = "miami"+r.nextInt(100);
				completeCommand = "newroom," + location + "," + r.nextInt(100) + "," + r.nextInt(100);
				dynamicRoomLocations.add(location);
			}
			break;
		case 9: //delete customer
			if(!dynamicCustomersID.isEmpty()){
				customerID = dynamicCustomersID.get(r.nextInt(dynamicCustomersID.size()));
				completeCommand = command + "," + customerID;
				dynamicFlightNumbers.remove((Integer)customerID);
			}
			else{
				customerID = r.nextInt(10000);
				completeCommand = "newcustomerid," + customerID;
				dynamicCustomersID.add(customerID);
			}
			break;
		case 10: //query flight
			flightNum = flightNumbers.get(r.nextInt(flightNumbers.size()));
			completeCommand = command + "," + flightNum;
			break;
		case 11: //query car
			location = locations.get(r.nextInt(locations.size()));
			completeCommand = command + "," + location;
			break;
		case 12: //query room
			location = locations.get(r.nextInt(locations.size()));
			completeCommand = command + "," + location;
			break;
		case 13: //query customer
			customerID = customersID.get(r.nextInt(customersID.size()));
			completeCommand = command + "," + customerID;
			break;
		case 14: //query flight price
			flightNum = flightNumbers.get(r.nextInt(flightNumbers.size()));
			completeCommand = command + "," + flightNum;
			break;
		case 15: //query car price
			location = locations.get(r.nextInt(locations.size()));
			completeCommand = command + "," + location;
		case 16: //query room price
			location = locations.get(r.nextInt(locations.size()));
			completeCommand = command + "," + location;
			break;
		case 17: //reserve flight
			dynamic = r.nextBoolean();
			if(dynamic && !dynamicCustomersID.isEmpty()){
				customerID = dynamicCustomersID.get(r.nextInt(dynamicCustomersID.size()));
			}else{
				customerID = customersID.get(r.nextInt(customersID.size()));
			}
			flightNum = flightNumbers.get(r.nextInt(flightNumbers.size()));			
			completeCommand = command + "," + customerID + "," + flightNum;
			break;
		case 18: //reserve car
			dynamic = r.nextBoolean();
			if(dynamic && !dynamicCustomersID.isEmpty()){
				customerID = dynamicCustomersID.get(r.nextInt(dynamicCustomersID.size()));
			}else{
				customerID = customersID.get(r.nextInt(customersID.size()));
			}
			location = locations.get(r.nextInt(locations.size()));
			numCars = r.nextInt(5);
			completeCommand = command + "," + customerID + "," + location + "," + numCars;
			break;
		case 19: //reserve room
			dynamic = r.nextBoolean();
			if(dynamic && !dynamicCustomersID.isEmpty()){
				customerID = dynamicCustomersID.get(r.nextInt(dynamicCustomersID.size()));
			}else{
				customerID = customersID.get(r.nextInt(customersID.size()));
			}
			location = locations.get(r.nextInt(locations.size()));
			numRooms = r.nextInt(5);
			completeCommand = command + "," + customerID + "," + location + "," + numRooms;
			break;
		case 20: //itinerary
			int n = 0;
			boolean Car = false;
			boolean Room = false;		
			n = r.nextInt(flightNumbers.size());			
			dynamic = r.nextBoolean();
			if(dynamic && !dynamicCustomersID.isEmpty()){
				customerID = dynamicCustomersID.get(r.nextInt(dynamicCustomersID.size()));
			}else{
				customerID = customersID.get(r.nextInt(customersID.size()));
			}
			completeCommand = command + "," + customerID;
			for (int i = 0; i < n; i++){
				completeCommand += "," + flightNumbers.get(r.nextInt(flightNumbers.size()));
			}
			location = locations.get(r.nextInt(locations.size()));
			Car = r.nextBoolean();
			Room = r.nextBoolean();
			completeCommand += "," + location + "," + Car + "," + Room;
			break;
		case 21: //new customer id
			customerID = r.nextInt(10000);
			completeCommand = command + "," + customerID;
			dynamicCustomersID.add(customerID);
			break;
		default:
			completeCommand = "Unknown command : " + command;
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
