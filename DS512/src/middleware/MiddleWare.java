package middleware;

import java.io.IOException;
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
import transaction.DiskAccess;
import transaction.Operation;
import transaction.Transaction;
import transaction.TransactionManager;

public class MiddleWare implements server.resInterface.ResourceManager {

	public static final String SEPARATOR = ",";

	protected RMHashtable m_itemHT = new RMHashtable();
	static ResourceManager rmFlight = null;
	static ResourceManager rmCar = null;
	static ResourceManager rmRoom = null;
	static LockManager lockManager;
	static TransactionManager transactionManager;
	LinkedList<Transaction> ongoingTransactions;
	DiskAccess stableStorage;
	int trCount;
	static String name;

	private static int SHUTDOWN_TIMEOUT = 300000;
	private static int TIME_TO_LIVE = 200000;
	public static Random r = new Random();

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
		name = "Resort21ResourceManager";

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
			registry.rebind(name, rm);
			System.err.println("Server ready");
		} catch (Exception e) {
			System.err.println("Server exception: " + e.toString());
			//e.printStackTrace();
		}

		// Create and install a security manager
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new RMISecurityManager());
		}
		obj.initiateFromDisk();
		while(true){
			try{
				if(obj != null){
					//System.out.println("Looping");
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

	public MiddleWare() throws RemoteException, InvalidTransactionException {
		ongoingTransactions = new LinkedList<Transaction>();
		trCount = 0;
	}
	
	private void initiateFromDisk() {
		try {
			stableStorage = new DiskAccess(this, "Customer");
			lockManager.UnlockAll(0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String getName() {
		return name;
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
				bill1=rmFlight.queryCustomerInfo(id, customerID).split("\n");
				bill2=rmCar.queryCustomerInfo(id, customerID).split("\n");
				bill3=rmRoom.queryCustomerInfo(id, customerID).split("\n");
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
				if(bill1[0].equalsIgnoreCase("")){
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
		return newTr;
	}
	
	public boolean commit(int transactionId){
		if(transactionManager.commit(transactionId, this)){
			lockManager.UnlockAll(transactionId);
			return true;
		}else{
			try {
				abort(transactionId);
			} catch (RemoteException | InvalidTransactionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return false;
		}
	}

	public boolean canCommit(int transactionId) throws RemoteException, 
	TransactionAbortedException, InvalidTransactionException {
		synchronized(ongoingTransactions) {
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == transactionId) {
					String operation = transactionId + ",canCommit,YES \n";
					System.out.println("Can commit, sent vote YES.");
					try {
						stableStorage.writeToLog(operation);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return true;
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
							String updatedLine = convertItemLine(transactionId, dataName);
							stableStorage.updateData(dataName, updatedLine);
						}
					}
				}
				break;
			}
			stableStorage.masterSwitch(transactionId);
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == transactionId) {
					List<Operation> ops = t.getOperations(); 
					for (int i = ops.size()-1; i >= 0; i--) {
						for (String dataName: ops.get(i).getDataNames()) {
							String updatedLine = convertItemLine(transactionId, dataName);
							stableStorage.updateData(dataName, updatedLine);
						}
					}
					break;
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		//boolean returnValue = transactionManager.commit(transactionId, this);
		System.out.println("Transaction " + transactionId + " has committed.");
		for (int i = 0; i < ongoingTransactions.size(); i++) {
			if (ongoingTransactions.get(i).getID() == transactionId) {
				synchronized(ongoingTransactions) { 
					//return (returnValue && ongoingTransactions.remove(ongoingTransactions.get(i)));
					return (ongoingTransactions.remove(ongoingTransactions.get(i)));
				}
			}
		}
		return true;
	}

	@Override
	public void abort(int transactionId) throws RemoteException, InvalidTransactionException {
		transactionManager.abort(transactionId, this);
		System.out.println("Transaction " + transactionId + " has ABORTED.");
		for (int i = 0; i < ongoingTransactions.size(); i++) {
			if (ongoingTransactions.get(i).getID() == transactionId) {
				ongoingTransactions.get(i).undo();
				synchronized(ongoingTransactions) { 
					ongoingTransactions.remove(ongoingTransactions.get(i));
				}
			}
		}
		lockManager.UnlockAll(transactionId);
		String operation = transactionId + ", abort\n";
		try{
			stableStorage.writeToLog(operation);
			System.out.println("Writing op");
		}catch(Exception e){
			System.out.println("Some god damn exception");
		}
	}
	
	public void selfDestruct(){
		System.exit(1);
	}
		
	public boolean crash(String which) throws RemoteException{
		if(which.equalsIgnoreCase("flight")){
			rmFlight.selfDestruct();
			return true;
		}else if(which.equalsIgnoreCase("car")){
			rmCar.selfDestruct();
			return true;
		}else if(which.equalsIgnoreCase("room")){
			rmRoom.selfDestruct();
			return true;
		}else{
			return false;
		}
	}
	
	private String convertItemLine(int id, String dataName) {
		String line = "";
		System.out.println("Name is " + dataName);
		if (dataName.substring(0, 8).equalsIgnoreCase("Customer")) {
			line += dataName;
		}
		for (int i = line.length(); i < TransactionManager.LINE_SIZE-1; i++) {
			line += " ";
		}
		line += "\n";
		return line;
	}


	private void addOperation(int id, Operation op) {
		if (id == 0) {return;}
		for (Transaction t: ongoingTransactions) {
			if (t.getID() == id) {
				t.addOp(op);
				stableStorage.logOperation(id, op);
				return;
			}
		}
		Transaction t = new Transaction(id);
		t.addOp(op);
		synchronized(ongoingTransactions) { 
			ongoingTransactions.add(t);
		}

		stableStorage.logOperation(id, op);
	}
	
	
}
