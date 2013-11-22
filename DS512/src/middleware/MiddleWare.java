package middleware;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileAlreadyExistsException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


import java.util.*;

import lockManager.DeadlockException;
import lockManager.LockManager;

import server.resInterface.InvalidTransactionException;
import server.resInterface.ResourceManager;
import server.resInterface.TransactionAbortedException;
import transaction.Operation;
import transaction.RAFList;
import transaction.Transaction;
import transaction.TransactionManager;

public class MiddleWare implements server.resInterface.ResourceManager {

	protected RMHashtable m_itemHT = new RMHashtable();
	static ResourceManager rmFlight = null;
	static ResourceManager rmCar = null;
	static ResourceManager rmRoom = null;
	static LockManager lockManager;
	static TransactionManager transactionManager;
	LinkedList<Transaction> ongoingTransactions;
	int trCount;
	private RAFList recordA;
	private RAFList recordB;
	private RAFList masterRec;
	private RAFList workingRec;
	private RandomAccessFile stateLog;
	private int txnMaster;

	private static int SHUTDOWN_TIMEOUT = 30000;
	private static int TIME_TO_LIVE = 20000;
	public static Random r = new Random();

	public static final String PATHING = System.getProperty("user.dir").substring(0,System.getProperty("user.dir").length() - 3);
	public static final String SEPARATOR = ",";

	public static void main(String args[]) {
		// Figure out where server is running		
		String server1 = "localhost";
		String server2 = "localhost"; 
		String server3 = "localhost";
		String serverMW = "localhost";
		int port1 = 1099;
		int port2 = 1099;
		int port3 = 1099;
		int portMW = 1099;
		lockManager = new LockManager();
		transactionManager = new TransactionManager();
		MiddleWare obj = null;

		if(args.length == 1){
			serverMW = serverMW + ":" + args[0];
			portMW = Integer.parseInt(args[0]);
		} else if(args.length == 7){

			serverMW = serverMW + ":" + args[0];
			portMW = Integer.parseInt(args[0]);

			//First arguments indicate server and port of the Flight RMI
			server1 = args[1];
			port1 = Integer.parseInt(args[2]);

			//Next arguments indicate server and port of the Car RMI
			server2 = args[3];
			port2 = Integer.parseInt(args[4]);

			//Last arguments indicate server and port of the Room RMI
			server3 = args[5];
			port3 = Integer.parseInt(args[6]);
		} else{
			System.err.println ("Wrong usage");
			System.out.println ("Usage: java middleware [port]"
					+ "[(flight)rmihost (flight)rmiport (car)rmihost (car)rmiportCar (room)rmihost (room)rmiport]");       
			System.exit(1);
		}

		//Connection to RMIs
		try {
			// get a reference to the rmiregistry
			Registry registry1 = LocateRegistry.getRegistry(server1, port1);
			Registry registry2 = LocateRegistry.getRegistry(server2, port2);
			Registry registry3 = LocateRegistry.getRegistry(server3, port3);

			// get the proxy and the remote reference by rmiregistry lookup
			rmFlight = (ResourceManager) registry1.lookup("Flight21ResourceManager");
			rmCar = (ResourceManager) registry2.lookup("Car21ResourceManager");
			rmRoom = (ResourceManager) registry3.lookup("Room21ResourceManager");
			if(rmFlight!=null && rmCar!=null && rmRoom!=null)
			{
				System.out.println("Successful");
				System.out.println("Connected to RMFlight, RMCar and RMRoom");
			}
			else{
				System.out.println("Unsuccessful");
			}//if
		} catch (Exception e) {    
			System.err.println("Client exception: " + e.toString());
			//e.printStackTrace();
		}//try

		//Getting server ready
		try{
			// create a new Server object
			obj = new MiddleWare();
			// dynamically generate the stub (client proxy)
			ResourceManager rm = (ResourceManager) UnicastRemoteObject.exportObject(obj, 0);

			// Bind the remote object's stub in the registry
			Registry registry = LocateRegistry.getRegistry(portMW);
			registry.rebind("Resort21ResourceManager", rm);
			System.err.println("Server ready");
		} catch (Exception e) {
			System.err.println("Server exception: " + e.toString());
			//e.printStackTrace();
		}

		// Create and install a security manager
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new RMISecurityManager());
		}
		while(true){
			try{
				if(obj != null){
					System.out.println("Looping");
					obj.verifyIfShutdown();
					obj.timeToLive();
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
				}
			}catch(Exception e){
				System.out.println(e.getMessage());
				System.out.println("O_o wut");
			}
		}
	}

	public MiddleWare() throws RemoteException, IOException {
		ongoingTransactions = new LinkedList<Transaction>();
		trCount = 0;
		txnMaster = -1;

		String locationA = PATHING + "Customer/RecordA.db";
		String locationB = PATHING + "Customer/RecordB.db";
		String locationLog = PATHING + "Customer/StateLog.db";

		System.out.println(locationA + " is location.");
		System.out.println(PATHING);
		File file = new File(locationA);
		boolean readDataA = !file.createNewFile();
		recordA = new RAFList("A", locationA, "rwd");
		file = new File(locationB);
		boolean readDataB = file.createNewFile();
		recordB = new RAFList("B", locationB, "rwd");
		file = new File(locationLog);
		file.createNewFile();
		stateLog = new RandomAccessFile(locationLog, "rwd");
		recordA.setNext(recordB);
		recordB.setNext(recordA);
		masterRec = getMasterRecord();
		workingRec = masterRec.getNext();
		if (masterRec == recordA && readDataA) {
			initializeMemory(recordA);
		} else {
			initializeMemory(recordB);
		}	
	}

	private void timeToLive() throws TransactionAbortedException{
		Date date;
		LinkedList<Transaction> ongoingTxns = transactionManager.getOngoingTransactions();
		for (int i=0; i < ongoingTxns.size(); i++) {
			date = new Date();
			if((date.getTime() - ongoingTxns.get(i).getTime()) >= TIME_TO_LIVE){
				int tID = ongoingTxns.get(i).getID();
				try {
					abort(tID);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
				} catch (InvalidTransactionException e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
				}
				throw new TransactionAbortedException(tID, "Transaction expired");
			} else {
				//System.out.println("ID " + ongoingTxns.get(i).getID() + "'s idle time is " + (date.getTime() - ongoingTxns.get(i).getTime()));
			}
		}

	}

	private void verifyIfShutdown() throws RemoteException {
		Date date = new Date();
		long time = date.getTime();
		long timer = 0;	
		if(!transactionManager.hasOngoingTransactions()){
			date = new Date();
			timer += date.getTime() - time;
			time = date.getTime();
			if(timer >= SHUTDOWN_TIMEOUT){
				System.out.println("Shutting down");
				if(rmFlight.shutdown() && rmCar.shutdown() && rmRoom.shutdown()){
					shutdown();
				}	
			}
		} else {
			timer = 0;
		}
	}

	// Reads a data item
	private RMItem readData( int id, String key )
	{
		synchronized(m_itemHT) {
			return (RMItem) m_itemHT.get(key);
		}
	}

	// Writes a data item
	private void writeData( int id, String key, RMItem value ) {
		synchronized(m_itemHT) {
			m_itemHT.put(key, value);
		}
	}

	// Remove the item out of storage
	protected RMItem removeData(int id, String key) {
		synchronized(m_itemHT) {
			return (RMItem)m_itemHT.remove(key);
		}
	}

	// Create a new flight, or add seats to existing flight
	// NOTE: if flightPrice <= 0 and the flight already exists, it maintains its current price
	public boolean addFlight(int id, int flightNum, int flightSeats, int flightPrice)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{		
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Flight"+flightNum, LockManager.WRITE)){
				rmList.add(rmFlight);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmFlight.addFlight(id,flightNum,flightSeats,flightPrice);
			}
			return false;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	public boolean deleteFlight(int id, int flightNum)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Flight"+flightNum+flightNum, LockManager.WRITE)){
				rmList.add(rmFlight);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmFlight.deleteFlight(id,flightNum);
			}
			return false;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Create a new room location or add rooms to an existing location
	//  NOTE: if price <= 0 and the room location already exists, it maintains its current price
	public boolean addRooms(int id, String location, int count, int price)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try {
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Room"+location, LockManager.WRITE)){
				rmList.add(rmRoom);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmRoom.addRooms(id,location,count,price);
			}
			return false;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Delete rooms from a location
	public boolean deleteRooms(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Room"+location, LockManager.WRITE)){
				rmList.add(rmRoom);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmRoom.deleteRooms(id,location);
			}
			return false;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Create a new car location or add cars to an existing location
	//  NOTE: if price <= 0 and the location already exists, it maintains its current price
	public boolean addCars(int id, String location, int count, int price)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Car"+location, LockManager.WRITE)){
				rmList.add(rmCar);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmCar.addCars(id,location, count, price);
			}
			return false;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Delete cars from a location
	public boolean deleteCars(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Car"+location, LockManager.WRITE)){
				rmList.add(rmCar);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmCar.deleteCars(id,location);
			}
			return false;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Returns the number of empty seats on this flight
	public int queryFlight(int id, int flightNum)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Flight"+flightNum, LockManager.READ)){
				rmList.add(rmFlight);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmFlight.queryFlight(id,flightNum);
			}
			return 0;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		} 
	}

	// Returns price of this flight
	public int queryFlightPrice(int id, int flightNum )
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Flight"+flightNum, LockManager.READ)){
				rmList.add(rmFlight);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmFlight.queryFlightPrice(id,flightNum);
			}
			return -1;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}

	}

	// Returns the number of rooms available at a location
	public int queryRooms(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException	{
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Room"+location, LockManager.READ)){
				rmList.add(rmRoom);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmRoom.queryRooms(id,location);
			}
			return 0;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Returns room price at this location
	public int queryRoomsPrice(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException	{
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Room"+location, LockManager.READ)){
				rmList.add(rmRoom);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmRoom.queryRoomsPrice(id,location);
			}
			return -1;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Returns the number of cars available at a location
	public int queryCars(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Car"+location, LockManager.READ)){
				rmList.add(rmCar);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmCar.queryCars(id,location);
			}
			return 0;

		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Returns price of cars at this location
	public int queryCarsPrice(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Car"+location, LockManager.READ)){
				rmList.add(rmCar);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmCar.queryCarsPrice(id,location);
			}
			return -1;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	public int newCustomer(int id)
			throws RemoteException, DeadlockException, InvalidTransactionException
			{
		Trace.info("INFO: RM::newCustomer(" + id +  ") called" );
		// Generate a globally unique ID for the new customer
		int cid = Integer.parseInt( String.valueOf(id) +
				String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
				String.valueOf( Math.round( Math.random() * 100 + 1 )));
		Customer cust = new Customer( cid );
		writeData(id, cust.getKey(), cust);
		String[] parameters = {((Integer)cid).toString()}; 
		Operation op = new Operation("newcustomer", parameters, this);
		addOperation(id, op);
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Customer"+cid, LockManager.WRITE)){
				rmList.add(rmFlight);
				rmList.add(rmCar);
				rmList.add(rmRoom);
				rmList.add(this);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				rmFlight.newCustomer(id, cid); 
				rmCar.newCustomer(id, cid);
				rmRoom.newCustomer(id, cid);
			}
			return cid;
		} 
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
			}

	// I opted to pass in customerID instead. This makes testing easier
	public boolean newCustomer(int id, int customerID )
			throws RemoteException, DeadlockException, InvalidTransactionException {
		Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") called" );
		Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
		if ( cust == null ) {
			cust = new Customer(customerID);
			writeData( id, cust.getKey(), cust );
			writeData(id, cust.getKey(), cust);
			String[] parameters = {((Integer)customerID).toString()}; 
			Operation op = new Operation("newcustomer", parameters, this);
			addOperation(id, op);
			Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") created a new customer" );
			try{
				LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
				if(lockManager.Lock(id, "Customer"+customerID, LockManager.WRITE)){
					rmList.add(rmFlight);
					rmList.add(rmCar);
					rmList.add(rmRoom);
					rmList.add(this);
					transactionManager.enlist(id, rmList);
					rmList.clear();

					rmFlight.newCustomer(id,customerID); 
					rmCar.newCustomer(id,customerID);
					rmRoom.newCustomer(id,customerID);
				}
				return true;
			}
			catch(DeadlockException e) {
				abort(id);
				System.out.println(e.getMessage());
				throw e;
			} catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				//e.printStackTrace();
				throw e;
			}
		} else {
			Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") failed--customer already exists");
			return false;
		}
	}

	// Deletes customer from the database. 
	public boolean deleteCustomer(int id, int customerID)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
		if ( cust == null ) {
			return false;
		} else {            
			// Increase the reserved numbers of all reservable items which the customer reserved. 
			RMHashtable reservationHT = cust.getReservations();
			for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {        
				String reservedkey = (String) (e.nextElement());
				ReservedItem reserveditem = cust.getReservedItem(reservedkey);
				ReservableItem item  = (ReservableItem) readData(id, reserveditem.getKey());
				item.setReserved(item.getReserved()-reserveditem.getCount());
				item.setCount(item.getCount()+reserveditem.getCount());
			}

			String[] billFlight = null;
			String[] billCar = null;
			String[] billRoom = null;
			if(lockManager.Lock(id, "Customer"+customerID, LockManager.READ)){
				billFlight=rmFlight.queryCustomerInfo(id, customerID).split("\\n");
				billCar=rmCar.queryCustomerInfo(id, customerID).split("\\n");
				billRoom=rmRoom.queryCustomerInfo(id, customerID).split("\\n");
			}
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			rmList.add(rmFlight);
			rmList.add(rmCar);
			rmList.add(rmRoom);
			rmList.add(this);
			transactionManager.enlist(id, rmList);
			rmList.clear();

			String[] info = null;
			int flightNumber = 0;
			String location = "";
			for(int i=1; i < billFlight.length; i++){
				info = billFlight[i].split(" ");
				flightNumber = Integer.parseInt(info[1].substring(7));
				lockManager.Lock(id, "Flight"+flightNumber, LockManager.WRITE);
			}
			for(int i=1; i < billCar.length; i++){
				info = billCar[i].split(" ");
				location = info[1].substring(4);
				lockManager.Lock(id, "Car"+location, LockManager.WRITE);
			}
			for(int i=1; i < billRoom.length; i++){
				info = billRoom[i].split(" ");
				location = info[1].substring(5);
				lockManager.Lock(id, "Room"+location, LockManager.WRITE);
			}



			try{
				if(lockManager.Lock(id, "Customer"+customerID, LockManager.WRITE)){					
					rmFlight.deleteCustomer(id,customerID);
					rmCar.deleteCustomer(id,customerID);
					rmRoom.deleteCustomer(id,customerID);

					String[] opParameters = new String[3];
					opParameters[0] = ((Integer)customerID).toString();
					opParameters[1] = "";
					opParameters[2] = "";
					Operation op = new Operation("deletecustomer", opParameters, this);
					addOperation(id, op);			

					// remove the customer from the storage
					removeData(id, cust.getKey());



					return true;
				}
			} catch(DeadlockException e) {
				abort(id);
				System.out.println(e.getMessage());
				throw e;
			} catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				//e.printStackTrace();
				throw e;
			}
			return false;
		}
	}

	// Adds car reservation to this customer. 
	public boolean reserveCar(int id, int customerID, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException{
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Car"+location, LockManager.WRITE)){
				if (lockManager.Lock(id, "Customer"+customerID, LockManager.WRITE)) {
					rmList.add(rmCar);
					transactionManager.enlist(id, rmList);
					rmList.clear();
					return rmCar.reserveCar(id,customerID,location);
				}
			}
			return false;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Adds room reservation to this customer. 
	public boolean reserveRoom(int id, int customerID, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Room"+location, LockManager.WRITE)){
				if (lockManager.Lock(id, "Customer"+customerID, LockManager.WRITE)) {
					rmList.add(rmRoom);
					transactionManager.enlist(id, rmList);
					rmList.clear();
					return rmRoom.reserveRoom(id,customerID,location);
				}
			}
			return false;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Adds flight reservation to this customer.  
	public boolean reserveFlight(int id, int customerID, int flightNum)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();
			if(lockManager.Lock(id, "Flight"+flightNum, LockManager.WRITE)){
				if (lockManager.Lock(id, "Customer"+customerID, LockManager.WRITE)) {
					rmList.add(rmFlight);
					transactionManager.enlist(id, rmList);
					rmList.clear();
					return rmFlight.reserveFlight(id,customerID,flightNum);
				}
			}
			return false;
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
	}

	// Reserve an itinerary 
	public boolean itinerary(int id, int customer, Vector flightNumbers,String location,boolean Car,boolean Room)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();

			//Check if flights available
			for(int i = 0; i<flightNumbers.size(); i++){
				if(lockManager.Lock(id, "Flight"+flightNumbers.get(i), LockManager.READ)){
					if(rmFlight.queryFlight(id, Integer.parseInt(flightNumbers.get(i).toString())) == 0)
						return false;
				}
			}
			//If car wanted, check if available
			if(Car){
				if(lockManager.Lock(id, "Car"+location, LockManager.READ)){
					if (rmCar.queryCars(id, location) == 0)
						return false;
				}

			}
			//If room wanted, check if available
			if(Room){
				if(lockManager.Lock(id, "Room"+location, LockManager.READ)){
					if (rmRoom.queryRooms(id, location) == 0)
						return false;
				}				
			}
			rmList.add(rmFlight);

			lockManager.Lock(id, "Customer"+customer, LockManager.WRITE);

			//Reserve flight
			for(int i = 0; i<flightNumbers.size(); i++){
				if(lockManager.Lock(id, "Flight"+flightNumbers.get(i), LockManager.WRITE)){
					if (!rmFlight.reserveFlight(id, customer, Integer.parseInt(flightNumbers.get(i).toString()))){
						return false;
					}
				}				
			}
			//Reserve car, if wanted
			if(Car){
				rmList.add(rmCar);
				if(lockManager.Lock(id, "Car"+location, LockManager.WRITE)){
					if (!rmCar.reserveCar(id, customer, location)) {
						return false;
					}
				}				
			}
			//Reserve room, if wanted
			if(Room){
				rmList.add(rmRoom);
				if(lockManager.Lock(id, "Room"+location, LockManager.WRITE)){
					if (!rmRoom.reserveRoom(id, customer, location)) {
						return false;
					}
				}				
			}
			transactionManager.enlist(id, rmList);
			rmList.clear();
		}
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}
		return true;
	}

	@Override
	public String queryCustomerInfo(int id, int customerID)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			String[] bill1 = null;
			String[] bill2 = null;
			String[] bill3 = null;
			if(lockManager.Lock(id, "Customer"+customerID, LockManager.READ)){
				bill1=rmFlight.queryCustomerInfo(id, customerID).split("\\n");
				bill2=rmCar.queryCustomerInfo(id, customerID).split("\\n");
				bill3=rmRoom.queryCustomerInfo(id, customerID).split("\\n");
			}
			else{
				return "Impossible to query customer";
			}
			/*Querying Customer information using id: 5
	                Customer id: 589576
	                Customer info:Bill for customer 589576
	                1 car-ottawa $45
	                1 car-toronto $45
	                2 room-montreal $20
	                1 flight-177 $103
	                1 flight-153 $64
	                1 flight-137 $101*/

			String bill = "";
			if(bill1.length == 1 || bill2.length == 1 || bill3.length == 1){
				if(bill1[0].equals("")){
					return bill;
				}
				bill = "This customer has no reservations.";
			}
			if(bill1.length > 1 || bill2.length > 1 || bill3.length > 1){
				bill = bill1[0] + "\n";

				for(int i=1; i < bill1.length; i++){
					bill=bill+bill1[i]+"\n";
				}
				for(int i=1; i < bill2.length; i++){
					bill=bill+bill2[i]+"\n";
				}
				for(int i=1; i < bill3.length; i++){
					bill=bill+bill3[i]+"\n";
				}
			}			
			return bill;
		}	
		catch(DeadlockException e) {
			abort(id);
			System.out.println(e.getMessage());
			throw e;
		} catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			//e.printStackTrace();
			throw e;
		}

	}

	@Override
	public boolean shutdown() throws RemoteException {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public int start() throws RemoteException {
		int newTr;
		System.out.println("Lock of TM attempted"); 
		synchronized(transactionManager) {
			System.out.println("Lock of TM successful!");
			newTr = transactionManager.start();
		}
		System.out.println("New transaction " + newTr + " started.");
		return newTr;
	}

	public boolean canCommit(int transactionId) throws RemoteException, 
	TransactionAbortedException, InvalidTransactionException {
		synchronized(ongoingTransactions) {
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == transactionId) {
					// if t's status is still ongoing.
					// Add vote yes to log for trxn t.
					// set transaction to have a ready to commit value (DONE)
					t.setReadyToCommit(true);
					boolean didCommit = transactionManager.commit(transactionId, this);	
					String operation = "";
					/*if(didCommit){
						operation = transactionId + ",canCommit,YES";
					}else{
						operation = transactionId + ",canCommit,NO";
					}	*/		
					operation = transactionId + ",canCommit,YES";
					System.out.println("Can commit, sent vote YES.");
					try {
						stateLog.writeBytes(operation);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return didCommit;
				}else{
					t.setReadyToCommit(false);
					String operation = transactionId + ",canCommit,NO";
					System.out.println("Cannot commit, sent vote NO, aborting transaction "+ transactionId);
					transactionManager.abort(transactionId, this);					
					abort(transactionId);
					try {
						stateLog.writeBytes(operation);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return false;
				}
			}
		} 	
		return false;
	}

	@Override
	public boolean doCommit(int transactionId) throws RemoteException,
	TransactionAbortedException, InvalidTransactionException {	
		try{	
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == transactionId) {
					List<Operation> ops = t.getOperations(); 
					for (int i = ops.size()-1; i >= 0; i--) {
						for (String dataName: ops.get(i).getDataNames()) {
							int line = workingRec.getLine(dataName);
							workingRec.rewriteLine(line, convertItemLine(dataName));
						}
					}
					break;
				}
			}
			masterSwitch(transactionId);
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == transactionId) {
					List<Operation> ops = t.getOperations(); 
					for (int i = ops.size()-1; i >= 0; i--) {
						for (String dataName: ops.get(i).getDataNames()) {
							int line = workingRec.getLine(dataName);
							System.out.println("Line is: " + line);
							workingRec.rewriteLine(line, convertItemLine(dataName));
						}
					}
					break;
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		//boolean returnValue = transactionManager.commit(transactionId, this);
		lockManager.UnlockAll(transactionId);
		System.out.println("Transaction " + transactionId + " has committed.");
		for (int i = 0; i < ongoingTransactions.size(); i++) {
			if (ongoingTransactions.get(i).getID() == transactionId) {
				System.out.println("Lock of ongoingtransactions attempted"); 
				synchronized(ongoingTransactions) { 
					System.out.println("Lock of ongoingtransactions successful!");
					//return (returnValue && ongoingTransactions.remove(ongoingTransactions.get(i)));
					return (ongoingTransactions.remove(ongoingTransactions.get(i)));
				}
			}
		}
		//return returnValue;
		return false;
	}

	@Override
	public void abort(int transactionId) throws RemoteException, InvalidTransactionException {
		transactionManager.abort(transactionId, this);
		System.out.println("Transaction " + transactionId + " has ABORTED.");
		for (int i = 0; i < ongoingTransactions.size(); i++) {
			if (ongoingTransactions.get(i).getID() == transactionId) {
				ongoingTransactions.get(i).undo();
				System.out.println("Lock of ongoingtransactions attempted"); 
				synchronized(ongoingTransactions) { 
					System.out.println("Lock of ongoingtransactions successful!");
					ongoingTransactions.remove(ongoingTransactions.get(i));
				}
			}
		}
		lockManager.UnlockAll(transactionId);
	}

	public void cancelNewFlight(String[] parameters) {

	}

	public void cancelNewCar(String[] parameters) {
	}

	public void cancelNewRoom(String[] parameters) {
	}

	public void cancelNewCustomer(String[] parameters) {
		// Generate a globally unique ID for the new customer
		removeData(0, Customer.getKey(Integer.parseInt(parameters[0])));
	}


	public void cancelFlightDeletion(String[] parameters) {

	}

	public void cancelCarDeletion(String[] parameters) {
	}

	public void cancelRoomDeletion(String[] parameters) {
	}

	// Parameter 0: customerID, parameter 1: type of reservation for all reservations, parameter 2: reservation identifiers.
	public void cancelCustomerDeletion(String[] parameters) {
		int cID = Integer.parseInt(parameters[0]);
		Customer cust = new Customer(cID);
		writeData( 0, cust.getKey(), cust );

	}

	public void cancelFlightReservation(String[] parameters) {
		// TODO Auto-generated method stub

	}

	@Override
	public void cancelCarReservation(String[] parameters) {
		// TODO Auto-generated method stub

	}

	@Override
	public void cancelRoomReservation(String[] parameters) {
		// TODO Auto-generated method stub

	}

	private void addOperation(int id, Operation op) {
		for (Transaction t: ongoingTransactions) {
			if (t.getID() == id) {
				t.addOp(op);
				return;
			}
		}
		Transaction t = new Transaction(id);
		t.addOp(op);
		System.out.println("Lock of ongoingtransactions attempted"); 
		synchronized(ongoingTransactions) { 
			System.out.println("Lock of ongoingtransactions successful!");
			ongoingTransactions.add(t);
		}

		logOperation(id, op);
	}

	private void logOperation(int id, Operation op) {
		//Add operation on stateLog file
		String operation = id + "," + op.getOpName();
		for (String param: op.getParameters()){
			operation +=  "," + param;
		}
		operation += "\n";
		try{
			stateLog.writeBytes(operation);
			System.out.println("Writing op");
			//write_stateLog.newLine();
		}catch(Exception e){
			System.out.println("Some god damn exception");
		}
	}

	private RAFList getMasterRecord() {
		// TODO Auto-generated method stub
		try {
			String op = "";
			String[] opElements;
			String[] lastCommit = null;
			op = stateLog.readLine();
			if(op == null){
				System.out.println("Log file is empty, master record is A");
				return recordA;
			}
			do{
				opElements = op.split(",");
				if(opElements[1].trim().equals("commit")){
					lastCommit = opElements;
				}	
				op = stateLog.readLine();
			}while(op != null);

			if(lastCommit == null){
				System.out.println("Log file is not empty but has no commit, master record is A");
				return recordA;
			}else{
				if(opElements[2].equals("A")){
					System.out.println("Commit found, master record is A");
					return recordA;
				}else{
					System.out.println("Commit found, master record is B");
					return recordB;
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	//Master points to the one which is currently the latest committed version, linked to transactionID
	private void masterSwitch(int transactionID){
		String operation = transactionID + ",commit," + workingRec.getName() + "\n";
		workingRec = workingRec.getNext();
		masterRec = masterRec.getNext();
		txnMaster = transactionID;
		try{
			stateLog.writeBytes(operation);
		}catch(Exception e){
			System.out.println("Problem trying to modify stateLog file on switchMaster on commit of transaction with ID : " + transactionID);
			System.out.println(e);
		}		
	}

	private void initializeMemory(RAFList record) {
		try {
			String line = "";
			try {
				line = record.getFileAccess().readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			while (line != null && line != "") {
				try {
					line = record.getFileAccess().readLine();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				line = line.trim();
				String[] lineDetails = line.split(",");
				if(lineDetails[0].startsWith("Room")) {
					try {
						this.addRooms(0, lineDetails[1], Integer.parseInt(lineDetails[2]), Integer.parseInt(lineDetails[3]));
					} catch (DeadlockException | InvalidTransactionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if(lineDetails[0].startsWith("Car")) {
					try {
						this.addCars(0, lineDetails[1], Integer.parseInt(lineDetails[2]), Integer.parseInt(lineDetails[3]));
					} catch (DeadlockException | InvalidTransactionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}				
				} else if(lineDetails[0].startsWith("Flight")) {
					try {
						this.addFlight(0, Integer.parseInt(lineDetails[1]), Integer.parseInt(lineDetails[2]), Integer.parseInt(lineDetails[3]));
					} catch (DeadlockException | InvalidTransactionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

			try {
				record.getFileAccess().seek(0);
				line = record.getFileAccess().readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			while (line != null && line != "") {
				try {
					line = record.getFileAccess().readLine();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				line = line.trim();
				String[] lineDetails = line.split(",");
				if(lineDetails[0].startsWith("Cust")) {
					for (String reservation: lineDetails) {
						if (reservation == lineDetails[0]) {
							continue;
						}
						String[] details = reservation.split(" ");
						if (details[1].startsWith("flight")) {
							try {
								this.addFlight(0, Integer.parseInt(details[1].substring(7)), Integer.parseInt(details[0]), Integer.parseInt(details[2].substring(1)));
								for (int i = 0; i < Integer.parseInt(details[0]); i++) {
									reserveFlight(0, Integer.parseInt(lineDetails[0].substring(8)), Integer.parseInt(details[1].substring(7)));
								}
							} catch (DeadlockException
									| InvalidTransactionException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}						
						} else if (details[1].startsWith("car")) {
							try {
								this.addCars(0, details[1].substring(4), Integer.parseInt(details[0]), Integer.parseInt(details[2].substring(1)));
								for (int i = 0; i < Integer.parseInt(details[0]); i++) {
									reserveCar(0, Integer.parseInt(lineDetails[0].substring(8)), details[1].substring(4));
								}
							} catch (DeadlockException
									| InvalidTransactionException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}						
						} else if (details[1].startsWith("room")) {
							try {
								this.addRooms(0, details[1].substring(5), Integer.parseInt(details[0]), Integer.parseInt(details[2].substring(1)));
								for (int i = 0; i < Integer.parseInt(details[0]); i++) {
									reserveRoom(0, Integer.parseInt(lineDetails[0].substring(8)), details[1].substring(5));
								}
							} catch (DeadlockException
									| InvalidTransactionException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

						}
					}
				}
			}
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private String convertItemLine(String dataName) {
		String line = "";
		System.out.println("Name is " + dataName);
		if (dataName.substring(0, 6).equalsIgnoreCase("Flight")) {
		ReservableItem flight = (ReservableItem)readData(0,"flight-" + dataName.substring(6));
		if ( flight == null ) {
			System.out.println("well well well, " + "flight-" + dataName.substring(6) + " is messed up.");
		}
		line += dataName + SEPARATOR + flight.getCount() + SEPARATOR + flight.getPrice();
	} else if (dataName.substring(0, 3).equalsIgnoreCase("Car")) {
		ReservableItem car = (ReservableItem)readData(0,"car-" + dataName.substring(3));
		line += dataName + SEPARATOR + car.getCount() + SEPARATOR + car.getPrice();
	} else if (dataName.substring(0, 4).equalsIgnoreCase("Room")) {
		ReservableItem room = (ReservableItem)readData(0,"room-" + dataName.substring(4));
		line += dataName + SEPARATOR + room.getCount() + SEPARATOR + room.getPrice();
	} else if (dataName.substring(0, 8).equalsIgnoreCase("Customer")) {
		line += dataName;
		String rawData = "";
		try {
			try {
				rawData = queryCustomerInfo(0, Integer.parseInt(dataName.substring(8)));
			} catch (DeadlockException | InvalidTransactionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String[] lines = rawData.split("\n");
			for (int i = 1; i < lines.length; i++) {
				line += SEPARATOR + lines[i]; 
			}
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	for (int i = line.length(); i < TransactionManager.LINE_SIZE-1; i++) {
		line += " ";
	}
		line += "\n";
		return line;
	}

	private void clear(){
		/* 
		 *Generate a new file called temp.
		 *In temp, write all transactions down.
		 *Close and delete current log.
		 *change name of temp to log.
		 *If crashes and no log file to be found, search for temp file.
		 */

		String locationLog = PATHING + "Customer/stateLog.db";
		String locationTemp = PATHING + "Customer/temp.db";
		RandomAccessFile temp = null;
		try {
			temp = new RandomAccessFile(locationTemp, "rwd");
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		String operation = "";
		synchronized(ongoingTransactions){
			for (Transaction t: ongoingTransactions){				
				for(Operation op: t.getOperations()){
					operation = t.getID() + "," + op.getOpName() + ",";
					for(String p: op.getParameters()){
						operation += p + ",";
					}
					try {
						temp.writeBytes(operation);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}					
				}
			}
		}

		try {
			stateLog.close();
			temp.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		File theLog = new File(locationLog);
		File theTemp = new File(locationTemp);
		theTemp.renameTo(theLog);
		theLog.delete();
	}
}
