package Client;

import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedList;
import java.util.Vector;

import NetPacket.*;

public class Client {

	private Socket socket;
	private int myID;
	private int packetID;
	private boolean runNetwork = true;
	PrintWriter out = null;
	BufferedReader in = null;
	LinkedList<NetPacket> toSendToServer;
	String command;
	static int Id, Cid;
	static int flightNum;
	static int flightPrice;
	static int flightSeats;
	static boolean Room;
	static boolean Car;
	static int price;
	static int numRooms;
	static int numCars;
	static String location;
	BufferedReader stdin;

	public Client(String host, int port) {
		command = "";
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
		stdin = new BufferedReader(new InputStreamReader(System.in));
		runClient();
	}

	public Client(String host, int port, File script) {
		command = "";
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
		try {
			stdin = new BufferedReader(new FileReader(script));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			closeConnection();
			e.printStackTrace();
		}
		runClient();
	}
	
	public void runClient() {

		try {
			NetPacket answer = NetPacket.fromStringToPacket(in.readLine());
			myID = Integer.parseInt(answer.getContent()[0]);
			System.out.println(answer.getType());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		while (runNetwork) {
			synchronized (toSendToServer) {
				boolean readFromServer = false;
				try{
					//read the next command
					command = stdin.readLine();
				}
				catch (IOException io){
					System.out.println("Unable to read from standard in");
					System.exit(1);
				}
				
				readFromServer = parse(command);
				
				while (!toSendToServer.isEmpty()) {
					NetPacket toSend = toSendToServer.removeFirst();
					if (toSend.getType().equals("close connection")) {
						out.println(toSend.toString());
						closeConnection();
						runNetwork = false;
						return;
					}
					out.println(toSend.fromPacketToString());				
				}
				if (readFromServer) {
					System.out.println("Waiting for answer");
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
	}


	public void decode(NetPacket p) {
		String type = p.getType();

		switch(findChoice(type)){
		case 1: //help section
			break;

		case 2:  //new flight
			if(getBoolean(p.getContent()[0])){
				System.out.println("Flight added");
			}else{
				System.out.println("Flight could not be added");
			}
			break;

		case 3:  //new Car
			if(getBoolean(p.getContent()[0])){
				System.out.println("Cars added");
			}else{
				System.out.println("Cars could not be added");
			}
			break;

		case 4:  //new Room
			if(getBoolean(p.getContent()[0])){
				System.out.println("Rooms added");
			} else{
				System.out.println("Rooms could not be added");
			}
			break;

		case 5:  //new Customer
			System.out.println("Customer id is: "+ p.getContent()[0]);
			break;

		case 6: //delete Flight
			if(getBoolean(p.getContent()[0])){
				System.out.println("Flight Deleted");
			}else{
				System.out.println("Flight could not be deleted");
			}
			break;

		case 7: //delete Car
			if(getBoolean(p.getContent()[0])){
				System.out.println("Cars Deleted");
			}else{
				System.out.println("Cars could not be deleted");
			}
			break;

		case 8: //delete Room
			if(getBoolean(p.getContent()[0])){
				System.out.println("Rooms Deleted");
			}else{
				System.out.println("Rooms could not be deleted");
			}
			break;

		case 9: //delete Customer
			if(getBoolean(p.getContent()[0])){
				System.out.println("Customer Deleted");
			}else{
				System.out.println("Customer could not be deleted");
			}
			break;

		case 10: //querying a flight
			System.out.println("Number of seats available: "+ p.getContent()[0]);
			break;

		case 11: //querying a Car Location			
			System.out.println("Number of cars available at this location: "+ p.getContent()[0]);
			break;

		case 12: //querying a Room location
			System.out.println("Number of rooms available at this location: "+ p.getContent()[0]);
			break;

		case 13: //querying Customer Information
			System.out.println("Customer Information: "+ p.getContent()[0]);
			break;           

		case 14: //querying a flight Price
			if(Integer.parseInt(p.getContent()[0]) == -1){
				System.out.println("There are no flight associated with this flight number.");
			}
			else{
				System.out.println("Price of a seat: "+ p.getContent()[0]);
			}
			break; 

		case 15: //querying a Car Price
			if(Integer.parseInt(p.getContent()[0]) == -1){
				System.out.println("There are no cars at this location.");
			}
			else{
				System.out.println("Price of a car at this location: "+ p.getContent()[0]);
			}
			break; 

		case 16: //querying a Room price
			if(Integer.parseInt(p.getContent()[0]) == 0){
				System.out.println("There are no rooms at this location.");
			}else{
				System.out.println("Price of a room at this location: "+ p.getContent()[0]);
			}		
			break; 


		case 17:  //reserve a flight
			if(getBoolean(p.getContent()[0])){
				System.out.println("Flight reserved");
			}else{
				System.out.println("Flight could not be reserved");
			}
			break;

		case 18:  //reserve a car
			if(getBoolean(p.getContent()[0])){
				System.out.println("Car reserved");
			}else{
				System.out.println("Car could not be reserved");
			}
			break;

		case 19:  //reserve a room
			if(getBoolean(p.getContent()[0])){
				System.out.println("Room reserved");
			}else{
				System.out.println("Room could not be reserved");
			}
			break;

		case 20:  //reserve an Itinerary
			if(getBoolean(p.getContent()[0])){
				System.out.println("Itinerary reserved");
			}else{
				System.out.println("Itinerary could not be reserved");
			}
			break;

		case 21:  //quit the client
			System.out.println("Quitting client.");

		case 22:  //new Customer given id
			if(Integer.parseInt(p.getContent()[0]) == -1){
				System.out.println("Client could not be added");
			}else{
				System.out.println("Client with customer ID " + p.getContent()[0] +" added.");
			}
			break;
			
		default:
			System.out.println("The interface does not support this command.");
			break;
		}//end of switch

	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean parse(String command)
	{
		String[] commandTokens = command.split(NetPacket.COMMAND_SEPARATOR);
		Vector args = new Vector();
		if (commandTokens[0].compareToIgnoreCase("help")!=0) {
			args.add(commandTokens[0].trim());
			String[] arguments = new String[commandTokens.length -1];
			for (int i = 1; i<commandTokens.length; i++) {
				arguments[i-1] = commandTokens[i].trim();
				args.add(commandTokens[i].trim());
			}
			if (commandCheck(args)){
				packetToSend(commandTokens[0], arguments);
				return true;
			}
			else
				return false;
		} else {
			if(commandTokens.length==1)   //command was "help"
				listCommands();
			else if (commandTokens.length==2)  //command was "help <commandname>"
				listSpecific(commandTokens[1].toString().trim());
			else  //wrong use of help command
				System.out.println("Improper use of help command. Type help or help, <commandname>");
			return false;
		}

	}
	
	@SuppressWarnings("rawtypes")
	public boolean commandCheck(Vector args)
	{	
		switch(findChoice(args.elementAt(0).toString())){
		case 2:  //new flight
			if(args.size()!=5){
				wrongNumber();
				return false;
			}
			System.out.println("Adding a new Flight using id: "+args.elementAt(1));
			System.out.println("Flight number: "+args.elementAt(2));
			System.out.println("Add Flight Seats: "+args.elementAt(3));
			System.out.println("Set Flight Price: "+args.elementAt(4));
			
			try{
				getInt(args.elementAt(1));
				flightNum = getInt(args.elementAt(2));
				flightSeats = getInt(args.elementAt(3));
				flightPrice = getInt(args.elementAt(4));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 3:  //new Car
			if(args.size()!=5){
				wrongNumber();
				return false;
			}
			System.out.println("Adding a new Car using id: "+args.elementAt(1));
			System.out.println("Car Location: "+args.elementAt(2));
			System.out.println("Add Number of Cars: "+args.elementAt(3));
			System.out.println("Set Price: "+args.elementAt(4));			
			try{
				getInt(args.elementAt(1));
				getString(args.elementAt(2));
				getInt(args.elementAt(3));
				getInt(args.elementAt(4));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 4:  //new Room
			if(args.size()!=5){
				wrongNumber();
				return false;
			}
			System.out.println("Adding a new Room using id: "+args.elementAt(1));
			System.out.println("Room Location: "+args.elementAt(2));
			System.out.println("Add Number of Rooms: "+args.elementAt(3));
			System.out.println("Set Price: "+args.elementAt(4));
			try{
				getInt(args.elementAt(1));
				getString(args.elementAt(2));
				getInt(args.elementAt(3));
				getInt(args.elementAt(4));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 5:  //new Customer
			if(args.size()!=2){
				wrongNumber();
				return false;
			}
			System.out.println("Adding a new Customer using id: "+args.elementAt(1));
			try{
				getInt(args.elementAt(1));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 6: //delete Flight
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Deleting a flight using id: "+args.elementAt(1));
			System.out.println("Flight Number: "+args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getInt(args.elementAt(2));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 7: //delete Car
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Deleting the cars from a particular location  using id: "+args.elementAt(1));
			System.out.println("Car Location: "+args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getString(args.elementAt(2));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 8: //delete Room
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Deleting all rooms from a particular location  using id: "+args.elementAt(1));
			System.out.println("Room Location: "+args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getString(args.elementAt(2));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 9: //delete Customer
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Deleting a customer from the database using id: "+args.elementAt(1));
			System.out.println("Customer id: "+args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getInt(args.elementAt(2));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 10: //querying a flight
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Querying a flight using id: "+args.elementAt(1));
			System.out.println("Flight number: "+args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getInt(args.elementAt(2));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}				
			break;

		case 11: //querying a Car Location
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Querying a car location using id: "+args.elementAt(1));
			System.out.println("Car location: "+args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getString(args.elementAt(2));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 12: //querying a Room location
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Querying a room location using id: "+args.elementAt(1));
			System.out.println("Room location: "+args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getString(args.elementAt(2));

			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 13: //querying Customer Information
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Querying Customer information using id: "+args.elementAt(1));
			System.out.println("Customer id: "+args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getInt(args.elementAt(2));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;               

		case 14: //querying a flight Price
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Querying a flight Price using id: "+args.elementAt(1));
			System.out.println("Flight number: "+args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getInt(args.elementAt(2));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 15: //querying a Car Price
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Querying a car price using id: "+args.elementAt(1));
			System.out.println("Car location: "+args.elementAt(2));   
			try{
				getInt(args.elementAt(1));
				getString(args.elementAt(2));

			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}    
			break;

		case 16: //querying a Room price
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Querying a room price using id: "+args.elementAt(1));
			System.out.println("Room Location: "+args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getString(args.elementAt(2));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 17:  //reserve a flight
			if(args.size()!=4){
				wrongNumber();
				return false;
			}
			System.out.println("Reserving a seat on a flight using id: "+args.elementAt(1));
			System.out.println("Customer id: "+args.elementAt(2));
			System.out.println("Flight number: "+args.elementAt(3));
			try{
				getInt(args.elementAt(1));
				getInt(args.elementAt(2));
				getInt(args.elementAt(3));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 18:  //reserve a car
			if(args.size()!=4){
				wrongNumber();
				return false;
			}
			System.out.println("Reserving a car at a location using id: "+args.elementAt(1));
			System.out.println("Customer id: "+args.elementAt(2));
			System.out.println("Location: "+args.elementAt(3));
			try{
				getInt(args.elementAt(1));
				getInt(args.elementAt(2));
				getString(args.elementAt(3));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 19:  //reserve a room
			if(args.size()!=4){
				wrongNumber();
				return false;
			}
			System.out.println("Reserving a room at a location using id: "+args.elementAt(1));
			System.out.println("Customer id: "+args.elementAt(2));
			System.out.println("Location: "+args.elementAt(3));
			try{
				getInt(args.elementAt(1));
				getInt(args.elementAt(2));
				getString(args.elementAt(3));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 20:  //reserve an Itinerary
			if(args.size()<7){
				wrongNumber();
				return false;
			}
			System.out.println("Reserving an Itinerary using id:"+args.elementAt(1));
			System.out.println("Customer id:"+args.elementAt(2));
			for(int i=0;i<args.size()-6;i++)
				System.out.println("Flight number: "+args.elementAt(3+i));
			System.out.println("Location for Car/Room booking: "+args.elementAt(args.size()-3));
			System.out.println("Car to book?: "+args.elementAt(args.size()-2));
			System.out.println("Room to book?: "+args.elementAt(args.size()-1));
			try{
				getInt(args.elementAt(1));
				getInt(args.elementAt(2));
				for(int i=0;i<args.size()-6;i++)
					getInt(args.elementAt(3+i));
				getString(args.elementAt(args.size()-3));
				getBoolean(args.elementAt(args.size()-2));
				getBoolean(args.elementAt(args.size()-1));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;

		case 21:  //quit the client
			if(args.size()!=1){
				wrongNumber();
				return false;
			}
			System.out.println("Quitting client.");
			System.exit(1);


		case 22:  //new Customer given id
			if(args.size()!=3){
				wrongNumber();
				return false;
			}
			System.out.println("Adding a new Customer using id: "+ args.elementAt(1) + " and cid " + args.elementAt(2));
			try{
				getInt(args.elementAt(1));
				getInt(args.elementAt(2));
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				return false;
			}
			break;
		default:
			wrongNumber();
			return false;
		}
		
		return true;

	}

	public void listCommands()
	{
		System.out.println("\nWelcome to the client interface provided to test your project.");
		System.out.println("Commands accepted by the interface are:");
		System.out.println("help");
		System.out.println("newflight\nnewcar\nnewroom\nnewcustomer\nnewcusomterid\ndeleteflight\ndeletecar\ndeleteroom");
		System.out.println("deletecustomer\nqueryflight\nquerycar\nqueryroom\nquerycustomer");
		System.out.println("queryflightprice\nquerycarprice\nqueryroomprice");
		System.out.println("reserveflight\nreservecar\nreserveroom\nitinerary");
		System.out.println("quit");
		System.out.println("\ntype help, <commandname> for detailed info(NOTE the use of comma).");
	}

	public int findChoice(String argument)
	{
		//System.out.println(argument);
		if (argument.compareToIgnoreCase("help")==0)
			return 1;
		else if(argument.compareToIgnoreCase("newflight")==0)
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
		else if (argument.compareToIgnoreCase("quit")==0)
			return 21;
		else if (argument.compareToIgnoreCase("newcustomerid")==0)
			return 22;
		else if (argument.compareToIgnoreCase("runscript")==0)
			return 101;
		else
			return 666;
	}

	public void listSpecific(String command)
	{
		System.out.print("Help on: ");
		switch(findChoice(command))
		{
		case 1:
			System.out.println("Help");
			System.out.println("\nTyping help on the prompt gives a list of all the commands available.");
			System.out.println("Typing help, <commandname> gives details on how to use the particular command.");
			break;

		case 2:  //new flight
			System.out.println("Adding a new Flight.");
			System.out.println("Purpose:");
			System.out.println("\tAdd information about a new flight.");
			System.out.println("\nUsage:");
			System.out.println("\tnewflight,<id>,<flightnumber>,<flightSeats>,<flightprice>");
			break;

		case 3:  //new Car
			System.out.println("Adding a new Car.");
			System.out.println("Purpose:");
			System.out.println("\tAdd information about a new car location.");
			System.out.println("\nUsage:");
			System.out.println("\tnewcar,<id>,<location>,<numberofcars>,<pricepercar>");
			break;

		case 4:  //new Room
			System.out.println("Adding a new Room.");
			System.out.println("Purpose:");
			System.out.println("\tAdd information about a new room location.");
			System.out.println("\nUsage:");
			System.out.println("\tnewroom,<id>,<location>,<numberofrooms>,<priceperroom>");
			break;

		case 5:  //new Customer
			System.out.println("Adding a new Customer.");
			System.out.println("Purpose:");
			System.out.println("\tGet the system to provide a new customer id. (same as adding a new customer)");
			System.out.println("\nUsage:");
			System.out.println("\tnewcustomer,<id>");
			break;


		case 6: //delete Flight
			System.out.println("Deleting a flight");
			System.out.println("Purpose:");
			System.out.println("\tDelete a flight's information.");
			System.out.println("\nUsage:");
			System.out.println("\tdeleteflight,<id>,<flightnumber>");
			break;

		case 7: //delete Car
			System.out.println("Deleting a Car");
			System.out.println("Purpose:");
			System.out.println("\tDelete all cars from a location.");
			System.out.println("\nUsage:");
			System.out.println("\tdeletecar,<id>,<location>,<numCars>");
			break;

		case 8: //delete Room
			System.out.println("Deleting a Room");
			System.out.println("\nPurpose:");
			System.out.println("\tDelete all rooms from a location.");
			System.out.println("Usage:");
			System.out.println("\tdeleteroom,<id>,<location>,<numRooms>");
			break;

		case 9: //delete Customer
			System.out.println("Deleting a Customer");
			System.out.println("Purpose:");
			System.out.println("\tRemove a customer from the database.");
			System.out.println("\nUsage:");
			System.out.println("\tdeletecustomer,<id>,<customerid>");
			break;

		case 10: //querying a flight
			System.out.println("Querying flight.");
			System.out.println("Purpose:");
			System.out.println("\tObtain Seat information about a certain flight.");
			System.out.println("\nUsage:");
			System.out.println("\tqueryflight,<id>,<flightnumber>");
			break;

		case 11: //querying a Car Location
			System.out.println("Querying a Car location.");
			System.out.println("Purpose:");
			System.out.println("\tObtain number of cars at a certain car location.");
			System.out.println("\nUsage:");
			System.out.println("\tquerycar,<id>,<location>");        
			break;

		case 12: //querying a Room location
			System.out.println("Querying a Room Location.");
			System.out.println("Purpose:");
			System.out.println("\tObtain number of rooms at a certain room location.");
			System.out.println("\nUsage:");
			System.out.println("\tqueryroom,<id>,<location>");        
			break;

		case 13: //querying Customer Information
			System.out.println("Querying Customer Information.");
			System.out.println("Purpose:");
			System.out.println("\tObtain information about a customer.");
			System.out.println("\nUsage:");
			System.out.println("\tquerycustomer,<id>,<customerid>");
			break;               

		case 14: //querying a flight for price 
			System.out.println("Querying flight.");
			System.out.println("Purpose:");
			System.out.println("\tObtain price information about a certain flight.");
			System.out.println("\nUsage:");
			System.out.println("\tqueryflightprice,<id>,<flightnumber>");
			break;

		case 15: //querying a Car Location for price
			System.out.println("Querying a Car location.");
			System.out.println("Purpose:");
			System.out.println("\tObtain price information about a certain car location.");
			System.out.println("\nUsage:");
			System.out.println("\tquerycarprice,<id>,<location>");        
			break;

		case 16: //querying a Room location for price
			System.out.println("Querying a Room Location.");
			System.out.println("Purpose:");
			System.out.println("\tObtain price information about a certain room location.");
			System.out.println("\nUsage:");
			System.out.println("\tqueryroomprice,<id>,<location>");        
			break;

		case 17:  //reserve a flight
			System.out.println("Reserving a flight.");
			System.out.println("Purpose:");
			System.out.println("\tReserve a flight for a customer.");
			System.out.println("\nUsage:");
			System.out.println("\treserveflight,<id>,<customerid>,<flightnumber>");
			break;

		case 18:  //reserve a car
			System.out.println("Reserving a Car.");
			System.out.println("Purpose:");
			System.out.println("\tReserve a given number of cars for a customer at a particular location.");
			System.out.println("\nUsage:");
			System.out.println("\treservecar,<id>,<customerid>,<location>,<nummberofCars>");
			break;

		case 19:  //reserve a room
			System.out.println("Reserving a Room.");
			System.out.println("Purpose:");
			System.out.println("\tReserve a given number of rooms for a customer at a particular location.");
			System.out.println("\nUsage:");
			System.out.println("\treserveroom,<id>,<customerid>,<location>,<nummberofRooms>");
			break;

		case 20:  //reserve an Itinerary
			System.out.println("Reserving an Itinerary.");
			System.out.println("Purpose:");
			System.out.println("\tBook one or more flights.Also book zero or more cars/rooms at a location.");
			System.out.println("\nUsage:");
			System.out.println("\titinerary,<id>,<customerid>,<flightnumber1>....<flightnumberN>,<LocationToBookCarsOrRooms>,<NumberOfCars>,<NumberOfRoom>");
			break;

		case 21:  //quit the client
			System.out.println("Quitting client.");
			System.out.println("Purpose:");
			System.out.println("\tExit the client application.");
			System.out.println("\nUsage:");
			System.out.println("\tquit");
			break;

		case 22:  //new customer with id
			System.out.println("Create new customer providing an id");
			System.out.println("Purpose:");
			System.out.println("\tCreates a new customer with the id provided");
			System.out.println("\nUsage:");
			System.out.println("\tnewcustomerid, <id>, <customerid>");
			break;

		default:
			System.out.println(command);
			System.out.println("The interface does not support this command.");
			break;
		}
	}

	public int getInt(Object temp) throws Exception {
		try {
			return (new Integer((String)temp)).intValue();
		}
		catch(Exception e) {
			throw e;
		}
	}
	
	public boolean getBool(Object temp) throws Exception {
		try {
			return (new Boolean((String)temp)).booleanValue();
		}
		catch(Exception e) {
			throw e;
		}
	}

	public boolean getBoolean(Object temp){
			return (new Boolean((String)temp)).booleanValue();
	}

	public String getString(Object temp) throws Exception {
		try {    
			return (String)temp;
		}
		catch (Exception e) {
			throw e;
		}
	}
	
	public void wrongNumber() {
		System.out.println("The number of arguments provided in this command are wrong.");
		System.out.println("Type help, <commandname> to check usage of this command.");
	}

	public void closeConnection() {
		try {
			out.close();
			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void packetToSend(NetPacket packet) {
		toSendToServer.addLast(packet);
	}

	public void packetToSend(String type, String[] content) {
		packetToSend(new NetPacket(myID, packetID, type, content));
		packetID++;
	}


}