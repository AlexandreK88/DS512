package automatedClient;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.util.*;
import java.io.*;

import server.resInterface.*;

import lockManager.DeadlockException;



public class Client
{
	String message = "blank";
	ResourceManager rm = null;
	int Id, Cid;
	int flightNum;
	int flightPrice;
	int flightSeats;
	boolean Room;
	boolean Car;
	int price;
	int numRooms;
	int numCars;
	String location;
	Vector arguments;
	BufferedReader stdin;
	int transactionID = -1; 
	int transactionIDbackup = -1; 

	public Client(String args[])
	{
		String server = "teaching";
		int port = 10121;
		if (args.length == 1)
		{
			server = args[0];
			stdin = new BufferedReader(new InputStreamReader(System.in));
		} else 	if (args.length == 2)
		{
			server = args[0];
			port = Integer.parseInt(args[1]);
			stdin = new BufferedReader(new InputStreamReader(System.in));
		} else	if (args.length == 4)
		{
			server = args[0];
			port = Integer.parseInt(args[1]);
			if (args[2].equals("-RS")) {
				File file = new File (args[3]);
				try {
					stdin = new BufferedReader(new FileReader(file));
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		} else {
			System.out.println ("Usage: java client [rmihost [rmiport]] [-RS ScriptPath/ScriptName]");
			System.exit(1);
		}

		try 
		{
			// get a reference to the rmiregistry
			Registry registry = LocateRegistry.getRegistry(server, port);
			// get the proxy and the remote reference by rmiregistry lookup
			rm = (ResourceManager) registry.lookup("Resort21ResourceManager");
			if(rm!=null)
			{
				System.out.println("Successful");
				System.out.println("Connected to RM");
			}
			else
			{
				System.out.println("Unsuccessful");
			}
			// make call on remote method
		} 
		catch (Exception e) 
		{    
			System.err.println("Client exception: " + e.toString());
			e.printStackTrace();
		}

		if (System.getSecurityManager() == null) {
			//System.setSecurityManager(new RMISecurityManager());
		}

		System.out.println("\n\n\tClient Interface");
		System.out.println("Type \"help\" for list of supported commands");

	}
	

	public boolean readCommand(String command) {

		//remove heading and trailing white space
		command = command.trim();
		arguments = parse(command);
		
		if(transactionID < 0 && (findChoice(arguments.elementAt(0).toString()) != 23 
				&& (findChoice(arguments.elementAt(0).toString()) != 1))
				&& (findChoice(arguments.elementAt(0).toString()) != 21)){
			System.out.println("There is no ongoing transaction.");
			System.out.println("Type start for a new one");
			return false;
		}

		//decide which of the commands this was
		switch(findChoice(arguments.elementAt(0).toString())){
		case 1: //help section
			if(arguments.size()==1)   //command was "help"
				listCommands();
			else if (arguments.size()==2)  //command was "help <commandname>"
				listSpecific(arguments.elementAt(1).toString());
			else  //wrong use of help command
				System.out.println("Improper use of help command. Type help or help, <commandname>");
			break;

		case 2:  //new flight
			if(arguments.size()!=4){
				wrongNumber(command);
				break;
			}
			//System.out.println("Adding a new Flight using id: "+arguments.elementAt(1));
			System.out.println("Flight number: "+arguments.elementAt(1));
			System.out.println("Add Flight Seats: "+arguments.elementAt(2));
			System.out.println("Set Flight Price: "+arguments.elementAt(3));

			try{
				//Id = getInt(arguments.elementAt(1));
				flightNum = getInt(arguments.elementAt(1));
				flightSeats = getInt(arguments.elementAt(2));
				flightPrice = getInt(arguments.elementAt(3));
				if(rm.addFlight(transactionID,flightNum,flightSeats,flightPrice)){
					System.out.println("Flight added");
				}
				else{
					System.out.println("Flight could not be added");
				}		
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;
		case 3:  //new Car
			if(arguments.size()!=4){
				wrongNumber(command);
				break;
			}
			//System.out.println("Adding a new Car using id: "+arguments.elementAt(1));
			System.out.println("Car Location: "+arguments.elementAt(1));
			System.out.println("Add Number of Cars: "+arguments.elementAt(2));
			System.out.println("Set Price: "+arguments.elementAt(3));
			try{
				//Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(1));
				numCars = getInt(arguments.elementAt(2));
				price = getInt(arguments.elementAt(3));
				if(rm.addCars(transactionID,location,numCars,price)){
					System.out.println("Cars added");
				}
				else{
					System.out.println("Cars could not be added");
				}
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 4:  //new Room
			if(arguments.size()!=4){
				wrongNumber(command);
				break;
			}
			//System.out.println("Adding a new Room using id: "+arguments.elementAt(1));			
			System.out.println("Room Location: "+arguments.elementAt(1));
			System.out.println("Add Number of Rooms: "+arguments.elementAt(2));
			System.out.println("Set Price: "+arguments.elementAt(3));
			try{
				//Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(1));
				numRooms = getInt(arguments.elementAt(2));
				price = getInt(arguments.elementAt(3));
				if(rm.addRooms(transactionID,location,numRooms,price)){
					System.out.println("Rooms added");
				}					
				else{
					System.out.println("Rooms could not be added");
				}		
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 5:  //new Customer
			if(arguments.size()!=1){
				wrongNumber(command);
				break;
			}
			//System.out.println("Adding a new Customer using id:"+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				int customer=rm.newCustomer(transactionID);
				System.out.println("New customer ID: " + customer);
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 6: //delete Flight
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			//System.out.println("Deleting a flight using id: "+arguments.elementAt(1));
			System.out.println("Flight Number: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				flightNum = getInt(arguments.elementAt(1));
				if(rm.deleteFlight(transactionID,flightNum))
					System.out.println("Flight Deleted");
				else
					System.out.println("Flight could not be deleted");
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 7: //delete Car
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			//System.out.println("Deleting the cars from a particular location  using id: "+arguments.elementAt(1));
			System.out.println("Car Location: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(1));

				if(rm.deleteCars(transactionID,location))
					System.out.println("Cars Deleted");
				else
					System.out.println("Cars could not be deleted");
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 8: //delete Room
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			//System.out.println("Deleting all rooms from a particular location  using id: "+arguments.elementAt(1));
			System.out.println("Room Location: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(1));
				if(rm.deleteRooms(transactionID,location))
					System.out.println("Rooms Deleted");
				else
					System.out.println("Rooms could not be deleted");
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 9: //delete Customer
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			//System.out.println("Deleting a customer from the database using id: "+arguments.elementAt(1));
			System.out.println("Customer id: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(1));
				if(rm.deleteCustomer(transactionID,customer))
					System.out.println("Customer Deleted");
				else
					System.out.println("Customer could not be deleted");
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 10: //querying a flight
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			//System.out.println("Querying a flight using id: "+arguments.elementAt(1));
			System.out.println("Flight number: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				flightNum = getInt(arguments.elementAt(1));
				int seats=rm.queryFlight(transactionID,flightNum);
				if(seats < 0){
					System.out.println("There are no existing flights with this flight number.");
				}else{
					System.out.println("Number of seats available: " + seats);
				}	
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 11: //querying a Car Location
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			//System.out.println("Querying a car location using id: "+arguments.elementAt(1));
			System.out.println("Car location: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(1));
				numCars=rm.queryCars(transactionID, location);
				if(numCars < 0){
					System.out.println("There are no car rentals at this location.");
				}else{
					System.out.println("Number of cars at this location: " + numCars);
				}
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 12: //querying a Room location
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			//System.out.println("Querying a room location using id: "+arguments.elementAt(1));
			System.out.println("Room location: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(1));
				numRooms=rm.queryRooms(transactionID,location);
				if(numRooms < 0){
					System.out.println("There are no car rentals at this location.");
				}else{
					System.out.println("Number of rooms at this location: " + numRooms);
				}				
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 13: //querying Customer Information
			if(arguments.size()!=2){
				wrongNumber(command);
		   		break;
			}
			//System.out.println("Querying Customer information using id: "+arguments.elementAt(1));
			System.out.println("Customer id: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(1));
				String bill=rm.queryCustomerInfo(transactionID,customer);
				if(bill.equals("")){
					System.out.println("This customer does not exist.");
				}else{
					System.out.println("Customer info: " + bill);
				}
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;               

		case 14: //querying a flight Price
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			//System.out.println("Querying a flight Price using id: "+arguments.elementAt(1));
			System.out.println("Flight number: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				flightNum = getInt(arguments.elementAt(1));
				price=rm.queryFlightPrice(transactionID,flightNum);
				if(price < 0){
					System.out.println("There are no existing flights with this flight number");
				}else{
					System.out.println("Price of a seat: " + price);
				}			
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 15: //querying a Car Price
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			//System.out.println("Querying a car price using id: "+arguments.elementAt(1));
			System.out.println("Car location: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(1));
				price=rm.queryCarsPrice(transactionID,location);
				if(price < 0){
					System.out.println("There are no car rentals at this location.");
				}else{
					System.out.println("Price of a car at this location: " + price);
				}				
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}                
			break;

		case 16: //querying a Room price
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			//System.out.println("Querying a room price using id: "+arguments.elementAt(1));
			System.out.println("Room Location: "+arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(1));
				price=rm.queryRoomsPrice(transactionID,location);
				if(price < 0){
					System.out.println("There are no hotels at this location.");
				}else{
					System.out.println("Price of Rooms at this location: " + price);
				}			
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 17:  //reserve a flight
			if(arguments.size()!=3){
				wrongNumber(command);
				break;
			}
			//System.out.println("Reserving a seat on a flight using id: "+arguments.elementAt(1));
			System.out.println("Customer id: "+arguments.elementAt(1));
			System.out.println("Flight number: "+arguments.elementAt(2));
			try{
				//Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(1));
				flightNum = getInt(arguments.elementAt(2));
				if(rm.reserveFlight(transactionID,customer,flightNum))
					System.out.println("Flight Reserved");
				else
					System.out.println("Flight could not be reserved.");
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 18:  //reserve a car
			if(arguments.size()!=3){
				wrongNumber(command);
				break;
			}
			//System.out.println("Reserving a car at a location using id: "+arguments.elementAt(1));
			System.out.println("Customer id: "+arguments.elementAt(1));
			System.out.println("Location: "+arguments.elementAt(2));

			try{
				//Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(2));
				if(rm.reserveCar(transactionID,customer,location))
					System.out.println("Car Reserved");
				else
					System.out.println("Car could not be reserved.");
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 19:  //reserve a room
			if(arguments.size()!=3){
				wrongNumber(command);
				break;
			}
			//System.out.println("Reserving a room at a location using id: "+arguments.elementAt(1));
			System.out.println("Customer id: "+arguments.elementAt(1));
			System.out.println("Location: "+arguments.elementAt(2));
			try{
				//Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(2));
				if(rm.reserveRoom(transactionID,customer,location))
					System.out.println("Room Reserved");
				else
					System.out.println("Room could not be reserved.");
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 20:  //reserve an Itinerary
			if(arguments.size()<6){
				wrongNumber(command);
				break;
			}
			//System.out.println("Reserving an Itinerary using id:"+arguments.elementAt(1));
			System.out.println("Customer id: "+arguments.elementAt(1));
			for(int i=0;i<arguments.size()-5;i++)
				System.out.println("Flight number: "+arguments.elementAt(2+i));
			System.out.println("Location for Car/Room booking: "+arguments.elementAt(arguments.size()-3));
			System.out.println("Car to book?: "+arguments.elementAt(arguments.size()-2));
			System.out.println("Room to book?: "+arguments.elementAt(arguments.size()-1));
			try{
				//Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(2));
				Vector<Integer> flightNumbers = new Vector<Integer>();
				for(int i=0;i<arguments.size()-5;i++)
					flightNumbers.addElement(Integer.parseInt(arguments.elementAt(2+i).toString()));
				location = getString(arguments.elementAt(arguments.size()-3));
				Car = getBoolean(arguments.elementAt(arguments.size()-2));
				Room = getBoolean(arguments.elementAt(arguments.size()-1));
				if(rm.itinerary(transactionID,customer,flightNumbers,location,Car,Room))
					System.out.println("Itinerary Reserved");
				else
					System.out.println("Itinerary could not be reserved.");
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 21:  //quit the client
			if(arguments.size()!=1){
				wrongNumber(command);
				break;
			}
			System.out.println("Quitting client.");
			System.exit(1);

		case 22:  //new Customer given id
			if(arguments.size()!=2){
				wrongNumber(command);
				break;
			}
			System.out.println("Adding a new Customer using customer ID " +arguments.elementAt(1));
			try{
				//Id = getInt(arguments.elementAt(1));
				Cid = getInt(arguments.elementAt(1));
				boolean customer=rm.newCustomer(transactionID,Cid);
				System.out.println("New customer id: "+ customer);
			}catch(DeadlockException e){
				try {
					System.out.println(e.getMessage());
					//rm.abort(transactionID);
					transactionID = -1;
					System.out.println("Transaction aborted.");
				} catch (Exception e1) {
					System.out.println(e1.getMessage());
				}
			}catch(InvalidTransactionException e){
				transactionID = -1; 
				System.out.println(e.getMessage());
			}catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;
		case 23: //start
			if(arguments.size()!=1){
				wrongNumber(command);
				break;
			}
			try{
				//Id = getInt(arguments.elementAt(1));
				if(transactionID >= 0){
					ongoingTxn();
					break;
				}
				transactionID = rm.start();
				transactionIDbackup = transactionID;
				System.out.println("A transaction with id " + transactionID + " is now started.");
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;
		case 24: //commit
			if(arguments.size()!=1){
				wrongNumber(command);
				break;
			}
			try{
				if (rm.doCommit(transactionID)) {
					System.out.println("Transaction with id " + transactionID + " committed successfully.");
					transactionID = -1;
					return true;
				} else {
					System.out.println("Transaction with id " + transactionID + " commit failed.");
					transactionID = -1;
					return false;
				}
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				transactionID = -1;
				return false ;
			}
		case 25: //abort
			if(arguments.size()!=1){
				wrongNumber(command);
				break;
			}
			try{
				rm.abort(transactionID);
				transactionID = -1;
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;
		default:
			System.out.println("The interface does not support this command.");
			break;
		}//end of switch
		return true;
	}

	public Vector parse(String command)
	{
		Vector arguments = new Vector();
		StringTokenizer tokenizer = new StringTokenizer(command,",");
		String argument ="";
		while (tokenizer.hasMoreTokens())
		{
			argument = tokenizer.nextToken();
			argument = argument.trim();
			arguments.add(argument);
		}
		return arguments;
	}
	public int findChoice(String argument)
	{
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
		else if (argument.compareToIgnoreCase("start")==0)
			return 23;
		else if (argument.compareToIgnoreCase("commit")==0)
			return 24;
		else if (argument.compareToIgnoreCase("abort")==0)
			return 25;		
		else
			return 666;

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
		System.out.println("start\ncommit\nabort");
		System.out.println("\nquit");
		System.out.println("\ntype help, <commandname> for detailed info(NOTE the use of comma).");
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
			System.out.println("\tnewflight,<flightnumber>,<flightSeats>,<flightprice>");
			break;

		case 3:  //new Car
			System.out.println("Adding a new Car.");
			System.out.println("Purpose:");
			System.out.println("\tAdd information about a new car location.");
			System.out.println("\nUsage:");
			System.out.println("\tnewcar<location>,<numberofcars>,<pricepercar>");
			break;

		case 4:  //new Room
			System.out.println("Adding a new Room.");
			System.out.println("Purpose:");
			System.out.println("\tAdd information about a new room location.");
			System.out.println("\nUsage:");
			System.out.println("\tnewroom,<location>,<numberofrooms>,<priceperroom>");
			break;

		case 5:  //new Customer
			System.out.println("Adding a new Customer.");
			System.out.println("Purpose:");
			System.out.println("\tGet the system to provide a new customer id. (same as adding a new customer)");
			System.out.println("\nUsage:");
			System.out.println("\tnewcustomer");
			break;


		case 6: //delete Flight
			System.out.println("Deleting a flight");
			System.out.println("Purpose:");
			System.out.println("\tDelete a flight's information.");
			System.out.println("\nUsage:");
			System.out.println("\tdeleteflight,<flightnumber>");
			break;

		case 7: //delete Car
			System.out.println("Deleting a Car");
			System.out.println("Purpose:");
			System.out.println("\tDelete all cars from a location.");
			System.out.println("\nUsage:");
			System.out.println("\tdeletecar,<location>,<numCars>");
			break;

		case 8: //delete Room
			System.out.println("Deleting a Room");
			System.out.println("\nPurpose:");
			System.out.println("\tDelete all rooms from a location.");
			System.out.println("Usage:");
			System.out.println("\tdeleteroom,<location>,<numRooms>");
			break;

		case 9: //delete Customer
			System.out.println("Deleting a Customer");
			System.out.println("Purpose:");
			System.out.println("\tRemove a customer from the database.");
			System.out.println("\nUsage:");
			System.out.println("\tdeletecustomer,<customerid>");
			break;

		case 10: //querying a flight
			System.out.println("Querying flight.");
			System.out.println("Purpose:");
			System.out.println("\tObtain Seat information about a certain flight.");
			System.out.println("\nUsage:");
			System.out.println("\tqueryflight,<flightnumber>");
			break;

		case 11: //querying a Car Location
			System.out.println("Querying a Car location.");
			System.out.println("Purpose:");
			System.out.println("\tObtain number of cars at a certain car location.");
			System.out.println("\nUsage:");
			System.out.println("\tquerycar,<location>");        
			break;

		case 12: //querying a Room location
			System.out.println("Querying a Room Location.");
			System.out.println("Purpose:");
			System.out.println("\tObtain number of rooms at a certain room location.");
			System.out.println("\nUsage:");
			System.out.println("\tqueryroom,<location>");        
			break;

		case 13: //querying Customer Information
			System.out.println("Querying Customer Information.");
			System.out.println("Purpose:");
			System.out.println("\tObtain information about a customer.");
			System.out.println("\nUsage:");
			System.out.println("\tquerycustomer,<customerid>");
			break;               

		case 14: //querying a flight for price 
			System.out.println("Querying flight.");
			System.out.println("Purpose:");
			System.out.println("\tObtain price information about a certain flight.");
			System.out.println("\nUsage:");
			System.out.println("\tqueryflightprice,<flightnumber>");
			break;

		case 15: //querying a Car Location for price
			System.out.println("Querying a Car location.");
			System.out.println("Purpose:");
			System.out.println("\tObtain price information about a certain car location.");
			System.out.println("\nUsage:");
			System.out.println("\tquerycarprice,<location>");        
			break;

		case 16: //querying a Room location for price
			System.out.println("Querying a Room Location.");
			System.out.println("Purpose:");
			System.out.println("\tObtain price information about a certain room location.");
			System.out.println("\nUsage:");
			System.out.println("\tqueryroomprice,<location>");        
			break;

		case 17:  //reserve a flight
			System.out.println("Reserving a flight.");
			System.out.println("Purpose:");
			System.out.println("\tReserve a flight for a customer.");
			System.out.println("\nUsage:");
			System.out.println("\treserveflight,<customerid>,<flightnumber>");
			break;

		case 18:  //reserve a car
			System.out.println("Reserving a Car.");
			System.out.println("Purpose:");
			System.out.println("\tReserve a car for a customer at a particular location.");
			System.out.println("\nUsage:");
			System.out.println("\treservecar,<customerid>,<location>");
			break;

		case 19:  //reserve a room
			System.out.println("Reserving a Room.");
			System.out.println("Purpose:");
			System.out.println("\tReserve a room for a customer at a particular location.");
			System.out.println("\nUsage:");
			System.out.println("\treserveroom,<customerid>,<location>");
			break;

		case 20:  //reserve an Itinerary
			System.out.println("Reserving an Itinerary.");
			System.out.println("Purpose:");
			System.out.println("\tBook one or more flights.Also book zero or more cars/rooms at a location.");
			System.out.println("\nUsage:");
			System.out.println("\titinerary,<customerid>,<flightnumber1>....<flightnumberN>,<LocationToBookCarsOrRooms>,<NumberOfCars>,<NumberOfRoom>");
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
			System.out.println("\tnewcustomerid,<customerid>");
			break;

		case 23:  //start
			System.out.println("Start a new transaction");
			System.out.println("\nUsage:");
			System.out.println("\tstart");
			break;

		case 24:  //commit
			System.out.println("Commit a transaction");
			System.out.println("\nUsage:");
			System.out.println("\tcommit");
			break;

		case 25:  //abort
			System.out.println("Abort a transaction");
			System.out.println("\nUsage:");
			System.out.println("\tabort");
			break;

		default:
			System.out.println(command);
			System.out.println("The interface does not support this command.");
			break;
		}
	}

	public void wrongNumber(String command) {
		System.out.println(command);
		System.out.println("The number of arguments provided in this command are wrong.");
		System.out.println("Type help, <commandname> to check usage of this command.");
	}

	public void ongoingTxn(){
		System.out.println("A transaction is already started, a new one cannot be started");
		System.out.println("Commit, abort or continue the ongoing transaction");
	}



	public int getInt(Object temp) throws Exception {
		try {
			return (new Integer((String)temp)).intValue();
		}
		catch(Exception e) {
			throw e;
		}
	}

	public boolean getBoolean(Object temp) throws Exception {
		try {
			return (new Boolean((String)temp)).booleanValue();
		}
		catch(Exception e) {
			throw e;
		}
	}

	public String getString(Object temp) throws Exception {
		try {    
			return (String)temp;
		}
		catch (Exception e) {
			throw e;
		}
	}
}
