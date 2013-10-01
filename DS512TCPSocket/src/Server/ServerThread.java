package Server;
import java.net.*;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.Vector;
import java.io.*;
import java.sql.Timestamp;
import java.util.Date;

import NetPacket.NetPacket;

public class ServerThread extends Thread {

	// Used to communicate with client/Middlethread.
	private Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private LinkedList<NetPacket> packetsToSend;
	public static final int ERRORCODE = -1;

	private int packetID;
	private int serverID;

	// Used to start RM tasks and requests.
	ResourceManagerImpl rm;
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



	public ServerThread(Socket s, int sID, ResourceManagerImpl resMan) {
		super("serverThread " + sID);
		if (s == null || sID < 0) {
			serverID = sID;
			this.interrupt();
		} else {
			// So far, it's one serverThread for one client. If there is ever more than
			// one client per thread, a list of clientIDs and sockets will be kept instead of the serverID and single socket.
			// The middleware will provide all the clientIDs to its own thread and the serverThreads.
			// when opening connection. 
			socket = s;
			serverID = sID;
			packetID = 0;
			packetsToSend = new LinkedList<NetPacket>();
			try {
				rm = resMan;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void run() {
		try {
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(
					new InputStreamReader(
							socket.getInputStream()));
			String inputLine;
			String[] content = {Integer.toString(serverID)};
			System.out.println("Server ready");
			packetID++;
			while (!socket.isClosed()) {
				if (in.ready()) {
					inputLine = in.readLine();
					NetPacket p = NetPacket.fromStringToPacket(inputLine);
					decode(p);
					if (socket.isClosed()) {
						return;
					}
				}
				synchronized(packetsToSend) {
					while (!packetsToSend.isEmpty()) {
						NetPacket p = packetsToSend.removeFirst();
						out.println(p.fromPacketToString());
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void close() {

		String[] empty = {"empty"};
		NetPacket closingPacket = new NetPacket(serverID, packetID, "close connection", empty);
		out.println(closingPacket.fromPacketToString());
		out.close();
		try {
			in.close();
			socket.close();	
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void decode(NetPacket p) {
		String command = p.getType();
		for (String arg: p.getContent()) {
			command += NetPacket.COMMAND_SEPARATOR + arg;
		}
		readCommand(command);
	}

	public void packetToSend(NetPacket packet) {
		packetsToSend.addLast(packet);
	}

	public void packetToSend(String type, String[] content) {
		packetToSend(new NetPacket(serverID, packetID, type, content));
		packetID++;
	}

	/*	public int getID() {
		return serverID;
	}*/

	private void readCommand(String command) {

		//remove heading and trailing white space
		command = command.trim();
		arguments = parse(command);
		String t = arguments.elementAt(0).toString();
		String c[] = new String[1];
		//decide which of the commands this was
		switch(findChoice(t)){

		case 2:  //new flight
			if(arguments.size()!=5){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				flightNum = getInt(arguments.elementAt(2));
				flightSeats = getInt(arguments.elementAt(3));
				flightPrice = getInt(arguments.elementAt(4));
				if(rm.addFlight(Id,flightNum,flightSeats,flightPrice)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 3:  //new Car
			if(arguments.size()!=5){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(2));
				numCars = getInt(arguments.elementAt(3));
				price = getInt(arguments.elementAt(4));
				if(rm.addCars(Id,location,numCars,price)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 4:  //new Room
			if(arguments.size()!=5){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(2));
				numRooms = getInt(arguments.elementAt(3));
				price = getInt(arguments.elementAt(4));
				if(rm.addRooms(Id,location,numRooms,price)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 5:  //new Customer
			if(arguments.size()!=2){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				int customer=rm.newCustomer(Id);
				c[0] = Integer.toString(customer);
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 6: //delete Flight
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				flightNum = getInt(arguments.elementAt(2));
				if(rm.deleteFlight(Id,flightNum)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 7: //delete Car
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(2));

				if(rm.deleteCars(Id,location)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 8: //delete Room
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(2));
				if(rm.deleteRooms(Id,location)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 9: //delete Customer
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			System.out.println("Deleting a customer from the database using id: "+arguments.elementAt(1));
			System.out.println("Customer id: "+arguments.elementAt(2));
			try{
				Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(2));
				if(rm.deleteCustomer(Id,customer)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 10: //querying a flight
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				flightNum = getInt(arguments.elementAt(2));
				int seats=rm.queryFlight(Id,flightNum);
				c[0] = Integer.toString(seats);
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 11: //querying a Car Location
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(2));
				numCars=rm.queryCars(Id,location);
				c[0] = Integer.toString(numCars);
				packetToSend(t,c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 12: //querying a Room location
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(2));
				numRooms=rm.queryRooms(Id,location);
				c[0] = Integer.toString(numRooms);
				packetToSend(t,c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 13: //querying Customer Information
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(2));
				if(rm.queryCustomerInfo(Id,customer)==""){
					c[0] = Integer.toString(ERRORCODE);
				} else{
					c = rm.queryCustomerInfo(Id,customer).split("\n");
				}
				packetToSend(t,c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;               

		case 14: //querying a flight Price
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				flightNum = getInt(arguments.elementAt(2));
				price=rm.queryFlightPrice(Id,flightNum);

				// If price is 0, then this flight number does not exist
				if(price!=0){
					c[0] = Integer.toString(price);
				} else{
					c[0] = Integer.toString(ERRORCODE);
				}
				packetToSend(t,c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 15: //querying a Car Price
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(2));
				price=rm.queryCarsPrice(Id,location);

				// If price is 0, then cars don't exist at this location
				if(price!=0){
					c[0] = Integer.toString(price);
				}
				else{
					c[0] = Integer.toString(ERRORCODE);
				}
				packetToSend(t,c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}                
			break;

		case 16: //querying a Room price
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				location = getString(arguments.elementAt(2));

				price=rm.queryRoomsPrice(Id,location);

				// If price is 0, then rooms don't exist at this location
				if(price!=0){
					c[0] = Integer.toString(price);
				}
				else{
					c[0] = Integer.toString(ERRORCODE);
				}
				packetToSend(t,c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 17:  //reserve a flight
			if(arguments.size()!=4){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(2));
				flightNum = getInt(arguments.elementAt(3));
				if(rm.reserveFlight(Id,customer,flightNum)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 18:  //reserve a car
			if(arguments.size()!=4){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(2));
				location = getString(arguments.elementAt(3));

				if(rm.reserveCar(Id,customer,location)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 19:  //reserve a room
			if(arguments.size()!=4){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(2));
				location = getString(arguments.elementAt(3));

				if(rm.reserveRoom(Id,customer,location)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 20:  //reserve an Itinerary
			if(arguments.size()<7){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				int customer = getInt(arguments.elementAt(2));
				Vector<Integer> flightNumbers = new Vector<Integer>();
				for(int i=0;i<arguments.size()-6;i++)
					flightNumbers.addElement(Integer.parseInt(arguments.elementAt(3+i).toString()));
				location = getString(arguments.elementAt(arguments.size()-3));
				Car = getBoolean(arguments.elementAt(arguments.size()-2));
				Room = getBoolean(arguments.elementAt(arguments.size()-1));

				if(rm.itinerary(Id,customer,flightNumbers,location,Car,Room)) {
					c[0] = Boolean.toString(true);
				} else {
					c[0] = Boolean.toString(false);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;

		case 21:  //quit the client
			if(arguments.size()!=1){
				wrongNumber(t);
				break;
			}
			close();
			break;

		case 22:  //new Customer given id
			if(arguments.size()!=3){
				wrongNumber(t);
				break;
			}
			try{
				Id = getInt(arguments.elementAt(1));
				Cid = getInt(arguments.elementAt(2));
				if (rm.newCustomer(Id,Cid)) {
					c[0] = Integer.toString(Cid);
				} else {
					c[0] = Integer.toString(ERRORCODE);
				}
				packetToSend(t, c);
			}
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			break;
		default:
			System.out.println("Unknown command.");
			break;
		}//end of switch

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
		else if (argument.compareToIgnoreCase("runscript")==0)
			return 101;
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
		System.out.println("nquit");
		System.out.println("\ntype help, <commandname> for detailed info(NOTE the use of comma).");
	}

	public void wrongNumber(String t) {
		String[] c = {"INVALID"};
		packetToSend(t, c);
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

	public boolean isWorking() {
		return !socket.isClosed();
	}

}
