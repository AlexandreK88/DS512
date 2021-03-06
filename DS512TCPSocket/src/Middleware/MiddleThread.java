package Middleware;

import NetPacket.NetPacket;

import java.util.*;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

public class MiddleThread extends Thread {

	public static final int CLIENT = 1;
	public static final int FLIGHT = 2;
	public static final int CAR = 3;
	public static final int ROOM = 4;
	
	public static final int REGULAR_MERGE_PACKET_SIZE = 3;

	private Socket clientSocket;
	private Socket flightSocket;
	private Socket carSocket;
	private Socket roomSocket;
	private BufferedReader clientIn;
	private PrintWriter clientOut;
	private BufferedReader flightIn;
	private PrintWriter flightOut;
	private BufferedReader carIn;
	private PrintWriter carOut;
	private BufferedReader roomIn;
	private PrintWriter roomOut;
	private int packetID;
	private int serverID;
	LinkedList<NetPacket> flightPackets;
	LinkedList<NetPacket> carPackets;
	LinkedList<NetPacket> roomPackets;

	LinkedList<NetPacket> clientPackets;
	LinkedList<NetPacket> clientMergePackets;
	boolean sendingItinerary = false;
	String[] args;

	int mergePacketSizeExpected = 3;
	
	public MiddleThread(Socket s, int sID, String flightName, int flightPort, String carName, int carPort, String roomName, int roomPort) {
		super("MiddleThread " + sID);
		if (s == null || sID < 0) {
			serverID = sID;
			this.interrupt();
		} else {
			clientSocket = s;
			serverID = sID;
			packetID = 0;
			flightPackets = new LinkedList<NetPacket>();
			carPackets = new LinkedList<NetPacket>();
			roomPackets = new LinkedList<NetPacket>();
			clientPackets = new LinkedList<NetPacket>();
			clientMergePackets = new LinkedList<NetPacket>();
		}

		try {
			flightSocket = new Socket(flightName, flightPort);			
			flightOut = new PrintWriter(flightSocket.getOutputStream(), true);
			flightIn = new BufferedReader(new InputStreamReader(
					flightSocket.getInputStream()));

		}	catch (UnknownHostException e) {
			System.err.println("Don't know about host: " + flightName + ".");
			System.exit(1);
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Couldn't get I/O for "
					+ "the connection to: " + flightName + ".");
			System.exit(1);
		}
		try {
			carSocket = new Socket(carName, carPort);
			carOut = new PrintWriter(carSocket.getOutputStream(), true);
			carIn = new BufferedReader(new InputStreamReader(
					carSocket.getInputStream()));
		}	catch (UnknownHostException e) {
			System.err.println("Don't know about host: " + carName + ".");
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Couldn't get I/O for "
					+ "the connection to: " + carName + ".");
		}
		try {
			roomSocket = new Socket(roomName, roomPort);
			roomOut = new PrintWriter(roomSocket.getOutputStream(), true);
			roomIn = new BufferedReader(new InputStreamReader(
					roomSocket.getInputStream()));
		}	catch (UnknownHostException e) {
			System.err.println("Don't know about host: " + roomName + ".");
			System.exit(1);
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Couldn't get I/O for "
					+ "the connection to: " + roomName + ".");
		}
		if (flightIn != null && carIn != null && roomIn != null) {
			System.out.println("\nSuccessful");
			System.out.println("\nConnected to RMFlight, RMCar and RMRoom");
		} else {
			System.out.println("\nUnsuccessful");
			close(false);
		}
		try {
			clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
			clientIn = new BufferedReader(
					new InputStreamReader(
							clientSocket.getInputStream()));
			System.err.println("Server ready");
		} catch (Exception e) {
			System.err.println("Server exception: " + e.toString());
			e.printStackTrace();
		}		
	}




	public void run() {
		try {
			String inputLine;
			String[] content = {Integer.toString(serverID)};
			clientOut.println((new NetPacket(serverID,packetID,"open connection", content)).fromPacketToString());
			packetID++;
			while (!clientSocket.isClosed() && !flightSocket.isClosed() && !carSocket.isClosed() && !roomSocket.isClosed()) {
				if (clientIn.ready()) {
					inputLine = clientIn.readLine();
					NetPacket p = NetPacket.fromStringToPacket(inputLine);
					decodeClientPacket(p);
					if (clientSocket.isClosed()) {
						break;
					}
				}
				synchronized(clientPackets) {
					while (!clientPackets.isEmpty()) {
						NetPacket p = clientPackets.removeFirst();
						clientOut.println(p.fromPacketToString());
					}
				}
				if (flightIn.ready()) {
					inputLine = flightIn.readLine();
					NetPacket p = NetPacket.fromStringToPacket(inputLine);
					if (p.getType().equals("newcustomer") 
							||  p.getType().equals("newcustomerid")
							||  p.getType().equals("querycustomer")
							||  p.getType().equals("deletecustomer")
							||  (p.getType().equals("queryflight") && sendingItinerary)
							||  (p.getType().equals("reserveflight") && sendingItinerary)) {
						regroupServerPacket(p);
					} else {
						System.out.println("Flight sending response to client about " + p.getType() + " and response " + p.getContent()[0] +".");
						packetToSend(p,CLIENT);
					}
					if (flightSocket.isClosed()) {
						break;
					}
				}
				synchronized(flightPackets) {
					while (!flightPackets.isEmpty()) {
						NetPacket p = flightPackets.removeFirst();
						flightOut.println(p.fromPacketToString());
					}
				}
				if (carIn.ready()) {
					inputLine = carIn.readLine();
					NetPacket p = NetPacket.fromStringToPacket(inputLine);
					if (p.getType().equals("newcustomer") 
							||  p.getType().equals("newcustomerid")
							||  p.getType().equals("querycustomer")
							||  p.getType().equals("deletecustomer")
							||  (p.getType().equals("querycar") && sendingItinerary)
							||  (p.getType().equals("reservecar") && sendingItinerary)) {
						regroupServerPacket(p);
					} else {
						System.out.println("Car sending response to client about " + p.getType() + " and response " + p.getContent()[0] +".");
						packetToSend(p,CLIENT);
					}
					if (carSocket.isClosed()) {
						break;
					}
				}
				synchronized(carPackets) {
					while (!carPackets.isEmpty()) {
						NetPacket p = carPackets.removeFirst();
						carOut.println(p.fromPacketToString());
					}
				}
				if (roomIn.ready()) {
					inputLine = roomIn.readLine();
					NetPacket p = NetPacket.fromStringToPacket(inputLine);
					if (p.getType().equals("newcustomer") 
							||  p.getType().equals("newcustomerid")
							||  p.getType().equals("querycustomer")
							||  p.getType().equals("deletecustomer")
							||  (p.getType().equals("queryroom") && sendingItinerary)
							||  (p.getType().equals("reserveroom") && sendingItinerary)){
						regroupServerPacket(p);
					} else {
						System.out.println("Room sending response to client about " + p.getType() + " and response " + p.getContent()[0] +".");
						packetToSend(p,CLIENT);
					}
					if (roomSocket.isClosed()) {
						break;
					}
				}
				synchronized(roomPackets) {
					while (!roomPackets.isEmpty()) {
						NetPacket p = roomPackets.removeFirst();
						roomOut.println(p.fromPacketToString());
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void close(boolean sendResponseToClient) {

		String[] empty = {"empty"};
		NetPacket closingPacket = new NetPacket(serverID, packetID, "close connection", empty);
		if (sendResponseToClient) {
			clientOut.println(closingPacket.fromPacketToString());
		}
		clientOut.close();
		flightOut.println(closingPacket.fromPacketToString());
		flightOut.close();
		carOut.println(closingPacket.fromPacketToString());
		carOut.close();
		roomOut.println(closingPacket.fromPacketToString());
		roomOut.close();
		try {
			clientIn.close();
			clientSocket.close();
			flightIn.close();
			flightSocket.close();	
			carIn.close();
			carSocket.close();	
			roomIn.close();
			roomSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public void regroupServerPacket(NetPacket p) {
		clientMergePackets.add(p);
		if (clientMergePackets.size() == mergePacketSizeExpected) {
			System.out.println("Response about " + p.getType() + " received from all RMs.");
			if(p.getType().equals("deletecustomer")) {
				Boolean allTrue = true;
				for (NetPacket pack: clientMergePackets) {
					if (!Boolean.parseBoolean(pack.getContent()[0])) {
						allTrue = false;
						System.out.println("Customer was not deleted.");
						break;
					}
				}
				clientMergePackets.clear();
				p.getContent()[0] = allTrue.toString();
				packetToSend(p, CLIENT);
			} else if(  p.getType().equals("querycustomer")) {
				int size = clientMergePackets.get(0).getContent().length
						+ clientMergePackets.get(1).getContent().length
						+ clientMergePackets.get(2).getContent().length -2;
				
				String[] newContent = new String[size];
				
				if(clientMergePackets.get(0).getContent()[0].equals("-1")
						|| clientMergePackets.get(1).getContent()[0].equals("-1")
						|| clientMergePackets.get(2).getContent()[0].equals("-1")) {
					System.out.println("Customer was not found.");
					newContent[0] = "-1";
				}
				else{
					int i = 0;
					System.out.println("Customer was found with details: ");
					for (String arg: clientMergePackets.get(0).getContent()) {
	                    newContent[i] = arg;
	                    System.out.println(newContent[i]);
	                    i++;
	                }
	                for(int j=1; j < clientMergePackets.get(1).getContent().length; j++){
	                    newContent[i]=clientMergePackets.get(1).getContent()[j];
	                    System.out.println(newContent[i]);
	                    i++;
	                }
	                for(int j=1; j < clientMergePackets.get(2).getContent().length; j++){
	                    newContent[i]=clientMergePackets.get(2).getContent()[j];
	                    System.out.println(newContent[i]);
	                    i++;
	                }
				}              
				clientMergePackets.clear();
				packetToSend(p.getType(), newContent, CLIENT);
			} else if (p.getType().equals("newcustomer")
					|| p.getType().equals("newcustomerid")) {

				if (!p.getContent()[0].equals("-1")) {
					System.out.println("Customer with ID " + p.getContent()[0] + " added.");
				} else {
					System.out.println("Customer was not added.");
				}
				clientMergePackets.clear();
				packetToSend(p, CLIENT);		
			} else if (p.getType().equals("queryflight") 
					|| p.getType().equals("querycar") 
					|| p.getType().equals("queryroom")) {
				boolean allTrue = true;
				for (NetPacket pack: clientMergePackets) {
					if (Integer.parseInt(pack.getContent()[0]) <= 0) {
						allTrue = false;
						System.out.println(pack.getType() + " returns that reservation not available.");
						break;
					} else { 
						System.out.println(pack.getType() + " returns that reservation available.");
					}
				}
				if (allTrue) {
					//Divide command in three (reserveFlight, reserveCar, reserveRoom)
					System.out.println("All reservations are available, completing itinerary reservation.");
					for(int i=0;i<args.length-5;i++){
						String[] reserveFlight = new String[3];
						reserveFlight[0] = args[0]; //ID
						reserveFlight[1] = args[1]; //customer ID
						reserveFlight[2] = args[2+i]; // Flight Number
						System.out.println("Reserving flight with details " + readableForm(reserveFlight));
						packetToSend("reserveflight", reserveFlight, FLIGHT);
					}			
					if (Boolean.parseBoolean(args[args.length-2])){
						String[] reserveCar = new String[3];
						reserveCar[0] = args[0]; //ID
						reserveCar[1] = args[1]; //customer ID
						reserveCar[2] = args[args.length-3]; // Location
						System.out.println("Reserving car with details " + reserveCar.toString());
						packetToSend("reservecar", reserveCar, CAR);
					}
					if (Boolean.parseBoolean(args[args.length-1])){
						String[] reserveRoom = new String[3];
						reserveRoom[0] = args[0]; //ID
						reserveRoom[1] = args[1]; //customer ID
						reserveRoom[2] = args[args.length-3]; // Location
						System.out.println("Reserving room with details " + reserveRoom.toString());
						packetToSend("reserveroom", reserveRoom, ROOM);
					}	
				} else {
					sendingItinerary = false;
					mergePacketSizeExpected = MiddleThread.REGULAR_MERGE_PACKET_SIZE;
					String[] c = {Boolean.toString(false)};
					packetToSend("itinerary", c, CLIENT);
				}
				clientMergePackets.clear();
			} else if (p.getType().equals("reserveflight") 
					|| p.getType().equals("reservecar") 
					|| p.getType().equals("reserveroom")) {
				boolean allTrue = true;
				mergePacketSizeExpected = MiddleThread.REGULAR_MERGE_PACKET_SIZE;
				sendingItinerary = false;
				for (NetPacket pack: clientMergePackets) {
					if (!Boolean.parseBoolean(pack.getContent()[0])) {
						System.out.println(pack.getType() + " returns that reservation was not completed.");
						allTrue = false;
					} else {
						System.out.println(pack.getType() + " returns that reservation was completed.");
					}
				}
				if (allTrue) {
					System.out.println("Itinerary reservation completed.");
					String[] c = {Boolean.toString(true)};
					packetToSend("itinerary", c, CLIENT);					
				} else {
					System.out.println("Itinerary reservation not entirely completed.");
					String[] c = {Boolean.toString(false)};
					packetToSend("itinerary", c, CLIENT);
				}
				clientMergePackets.clear();
			} else {
				System.out.println("Something, somewhere and somehow, went wrong and light no longer shines upon our world...");
				clientMergePackets.clear();
			}
		} else {
			System.out.println("Response about " + p.getType() + ". Waiting response from the other RMs.");
		}
	}


	public void decodeClientPacket(NetPacket p) {
		String command = p.getType();
		if (command.compareToIgnoreCase("newflight")==0) {
			System.out.println("\nClient requesting to add a flight with details " + readableForm(p.getContent()));
			packetToSend(p, FLIGHT); //flightOut.println(p.fromPacketToString());
		}
		else if(command.compareToIgnoreCase("newcar")==0) {
			System.out.println("\nClient requesting to add a car with details " + readableForm(p.getContent()));
			packetToSend(p, CAR); //carOut.println(p.fromPacketToString());
		}
		else if(command.compareToIgnoreCase("newroom")==0) {
			System.out.println("\nClient requesting to add a room with details " + readableForm(p.getContent()));
			packetToSend(p, ROOM); //roomOut.println(p.fromPacketToString());
		}
		else if(command.compareToIgnoreCase("newcustomer")==0)
		{
			System.out.println("\nClient requesting to add a new customer with details " + readableForm(p.getContent()));
			String cid = (new Integer(Integer.parseInt(String.valueOf(Integer.parseInt(p.getContent()[0])) +
					String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
					String.valueOf( Math.round( Math.random() * 100 + 1 ))))).toString();
			String[] newContent = {p.getContent()[0], cid};
			packetToSend("newcustomerid", newContent, FLIGHT);//flightOut.println(p.fromPacketToString());
			packetToSend("newcustomerid", newContent, CAR); //carOut.println(p.fromPacketToString());
			packetToSend("newcustomerid", newContent, ROOM); //roomOut.println(p.fromPacketToString());
		}
		else if(command.compareToIgnoreCase("deleteflight")==0) {
			System.out.println("\nClient requesting to delete a flight with details " + readableForm(p.getContent()));
			packetToSend(p, FLIGHT); //flightOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("deletecar")==0) {
			System.out.println("\nClient requesting to delete a car with details " + readableForm(p.getContent()));
			packetToSend(p, CAR); //carOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("deleteroom")==0) {
			System.out.println("\nClient requesting to delete a room with details " + readableForm(p.getContent()));
			packetToSend(p, ROOM); //roomOut.println(p.fromPacketToString());
		}	else if(command.compareToIgnoreCase("deletecustomer")==0)
		{
			System.out.println("\nClient requesting to delete a customer with details " + readableForm(p.getContent()));
			packetToSend(p, FLIGHT); //flightOut.println(p.fromPacketToString());
			packetToSend(p, CAR); //carOut.println(p.fromPacketToString());
			packetToSend(p, ROOM); //roomOut.println(p.fromPacketToString());
		}
		else if(command.compareToIgnoreCase("queryflight")==0) {
			System.out.println("\nClient requesting how many spaces on a flight with details " + readableForm(p.getContent()));
			packetToSend(p, FLIGHT); //flightOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("querycar")==0) {
			System.out.println("\nClient requesting how many cars available with details " + readableForm(p.getContent()));
			packetToSend(p, CAR); //carOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("queryroom")==0) {
			System.out.println("\nClient requesting how many rooms left with details " + readableForm(p.getContent()));
			packetToSend(p, ROOM); //roomOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("querycustomer")==0)
		{
			System.out.println("\nClient requesting what are the reservations of a customer with details " + readableForm(p.getContent()));
			packetToSend(p, FLIGHT); //flightOut.println(p.fromPacketToString());
			packetToSend(p, CAR); //carOut.println(p.fromPacketToString());
			packetToSend(p, ROOM); //roomOut.println(p.fromPacketToString());
		}
		else if(command.compareToIgnoreCase("queryflightprice")==0) {
			System.out.println("\nClient requesting how much for a flight with details " + readableForm(p.getContent()));
			packetToSend(p, FLIGHT); //flightOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("querycarprice")==0) {
			System.out.println("\nClient requesting how much for a car with details " + readableForm(p.getContent()));
			packetToSend(p, CAR); //carOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("queryroomprice")==0) {
			System.out.println("\nClient requesting how much for a room with details " + readableForm(p.getContent()));
			packetToSend(p, ROOM); //roomOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("reserveflight")==0) {
			System.out.println("\nClient requesting to reserve flight with details " + readableForm(p.getContent()));
			packetToSend(p, FLIGHT); //flightOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("reservecar")==0) {
			System.out.println("\nClient requesting to reserve car with details " + readableForm(p.getContent()));
			packetToSend(p, CAR); //carOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("reserveroom")==0) {
			System.out.println("\nClient requesting to reserve room with details " + readableForm(p.getContent()));
			packetToSend(p, ROOM); //roomOut.println(p.fromPacketToString());
		} else if(command.compareToIgnoreCase("itinerary")==0) {
			System.out.println("\nClient requesting to reserve itinerary with details " + readableForm(p.getContent()));
			args = p.getContent();
			sendingItinerary = true;
			
			//Before starting any reservations, we need to check that it is possible to reserve each item
			mergePacketSizeExpected = 0;
			for(int i=0;i<args.length-6;i++){
				String[] queryFlight = new String[2];
				queryFlight[0] = args[0]; //ID
				queryFlight[1] = args[2+i]; // Flight Number
				packetToSend("queryflight", queryFlight, FLIGHT);
				mergePacketSizeExpected++;
			}			
			if (Boolean.parseBoolean(args[args.length-2])){
				String[] queryCar = new String[2];
				queryCar[0] = args[0]; //ID
				queryCar[1] = args[args.length-3]; // Location
				packetToSend("querycar", queryCar, CAR);
				mergePacketSizeExpected++;
			}
			if (Boolean.parseBoolean(args[args.length-1])){
				String[] queryRoom = new String[2];
				queryRoom[0] = args[0]; //ID
				queryRoom[1] = args[args.length-3]; // Location
				packetToSend("queryroom", queryRoom, ROOM);
				mergePacketSizeExpected++;
			}	
		} else if (command.compareToIgnoreCase("newcustomerid")==0) {
			System.out.println("\nClient requesting to add a customer with specified ID " + readableForm(p.getContent()));
			packetToSend(p, FLIGHT); //flightOut.println(p.fromPacketToString());
			packetToSend(p, CAR); //carOut.println(p.fromPacketToString());
			packetToSend(p, ROOM); //roomOut.println(p.fromPacketToString());
		} else if (command.compareToIgnoreCase("Close Connection")==0) {
			close(false);
		}

	}


	public void packetToSend(NetPacket packet, int destination) {
		if (destination == CLIENT) {
			clientPackets.addLast(packet);
		} else if (destination == FLIGHT) {
			flightPackets.addLast(packet);
		} else if (destination == CAR) {
			carPackets.addLast(packet);
		} else if (destination == ROOM) {
			roomPackets.addLast(packet);
		}
	}

	public void packetToSend(String type, String[] content, int destination) {
		packetToSend(new NetPacket(serverID, packetID, type, content), destination);
		packetID++;
	}


	public boolean isWorking() {
		return (!clientSocket.isClosed());
		
	}
	
	public String readableForm(String[] c) {
		String answer = "";
		for (String part: c) {			
			answer+=part;
			if (c[c.length-1] != part)
				answer+=", ";
		}
		answer+=".";
		return answer;
	}



}
