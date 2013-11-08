package Middleware;

import LockManager.DeadlockException;
import LockManager.LockManager;
import Server.ResInterface.InvalidTransactionException;
import Server.ResInterface.ResourceManager;
import Server.ResInterface.TransactionAbortedException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


import java.util.*;

import transaction.Operation;
import transaction.Transaction;
import transaction.TransactionManager;

public class MiddleWare implements Server.ResInterface.ResourceManager {

	protected RMHashtable m_itemHT = new RMHashtable();
	static ResourceManager rmFlight = null;
	static ResourceManager rmCar = null;
	static ResourceManager rmRoom = null;
	static LockManager lockManager;
	static TransactionManager transactionManager;
	LinkedList<Transaction> ongoingTransactions;
	LinkedList<ResourceManager> rmList = new LinkedList<ResourceManager>();

	private static int SHUTDOWN_TIMEOUT = 30000;
	private static int TIME_TO_LIVE = 20000;


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
			e.printStackTrace();
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
			e.printStackTrace();
		}

		// Create and install a security manager
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new RMISecurityManager());
		}
		while(true){
			try{
				if(obj != null){
					obj.verifyIfShutdown();
					obj.timeToLive();
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}catch(Exception e){
				System.out.println(e.getMessage());
				System.out.println("O_o wut");
			}
		}
	}

	private void timeToLive() throws TransactionAbortedException{
		Date date;
		LinkedList<Transaction> ongoingTxns = transactionManager.getOngoingTransactions();
		for (int i=0; i < ongoingTxns.size(); i++) {
			date = new Date();
			if((date.getTime() - ongoingTxns.get(i).getTime()) >= TIME_TO_LIVE){
				int tID = ongoingTxns.get(i).getID();
				transactionManager.abort(ongoingTxns.get(i).getID(), this);
				throw new TransactionAbortedException(tID, "Transaction expired");
			} else {
				System.out.println(date.getTime() - ongoingTxns.get(i).getTime());
			}
		}

	}
	
	private void verifyIfShutdown() throws RemoteException {
		// time = current time;
		Date date = new Date();
		long time = date.getTime();
		// timer = 0;
		long timer = 0;
		/* if no transactions waiting
		 *		timer += currenttime - time;
		 *		time = current time
		 *		if timer reaches threshold
		 *		shutdown
		 *		endif
		 *	else
		 *		timer = 0;
		 *  endif 	
		 *  sleep maybe to avoid consuming CPU resources. 
		 */		
		if(!transactionManager.hasOngoingTransactions()){
			date = new Date();
			timer += date.getTime() - time;
			time = date.getTime();
			if(timer >= SHUTDOWN_TIMEOUT){
				if(rmFlight.shutdown() && rmCar.shutdown() && rmRoom.shutdown()){
					shutdown();
				}	
			}
			else{
				timer = 0;
			}
		}
	}

	public MiddleWare() throws RemoteException {
		ongoingTransactions = new LinkedList<Transaction>();
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
			if(lockManager.Lock(id, "Flight"+flightNum, LockManager.WRITE)){
				rmList.add(rmFlight);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmFlight.addFlight(id,flightNum,flightSeats,flightPrice);
			}
			return false;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	public boolean deleteFlight(int id, int flightNum)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			if(lockManager.Lock(id, "Flight"+flightNum+flightNum, LockManager.WRITE)){
				rmList.add(rmFlight);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmFlight.deleteFlight(id,flightNum);
			}
			return false;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}



	// Create a new room location or add rooms to an existing location
	//  NOTE: if price <= 0 and the room location already exists, it maintains its current price
	public boolean addRooms(int id, String location, int count, int price)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try {
			if(lockManager.Lock(id, "Room"+location, LockManager.WRITE)){
				rmList.add(rmRoom);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmRoom.addRooms(id,location,count,price);
			}
			return false;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	// Delete rooms from a location
	public boolean deleteRooms(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			if(lockManager.Lock(id, "Room"+location, LockManager.WRITE)){
				rmList.add(rmRoom);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmRoom.deleteRooms(id,location);
			}
			return false;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	// Create a new car location or add cars to an existing location
	//  NOTE: if price <= 0 and the location already exists, it maintains its current price
	public boolean addCars(int id, String location, int count, int price)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			if(lockManager.Lock(id, "Car"+location, LockManager.WRITE)){
				rmList.add(rmCar);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmCar.addCars(id,location, count, price);
			}
			return false;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	// Delete cars from a location
	public boolean deleteCars(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			if(lockManager.Lock(id, "Car"+location, LockManager.WRITE)){
				rmList.add(rmCar);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmCar.deleteCars(id,location);
			}
			return false;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	// Returns the number of empty seats on this flight
	public int queryFlight(int id, int flightNum)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			if(lockManager.Lock(id, "Flight"+flightNum, LockManager.READ)){
				rmList.add(rmFlight);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmFlight.queryFlight(id,flightNum);
			}
			return 0;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		} 
	}

	// Returns price of this flight
	public int queryFlightPrice(int id, int flightNum )
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			if(lockManager.Lock(id, "Flight"+flightNum, LockManager.READ)){
				rmList.add(rmFlight);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmFlight.queryFlightPrice(id,flightNum);
			}
			return -1;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}

	}

	// Returns the number of rooms available at a location
	public int queryRooms(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException	{
		try{
			if(lockManager.Lock(id, "Room"+location, LockManager.READ)){
				rmList.add(rmRoom);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmRoom.queryRooms(id,location);
			}
			return 0;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	// Returns room price at this location
	public int queryRoomsPrice(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException	{
		try{
			if(lockManager.Lock(id, "Room"+location, LockManager.READ)){
				rmList.add(rmRoom);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmRoom.queryRoomsPrice(id,location);
			}
			return -1;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	// Returns the number of cars available at a location
	public int queryCars(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			if(lockManager.Lock(id, "Car"+location, LockManager.READ)){
				rmList.add(rmCar);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmCar.queryCars(id,location);
			}
			return 0;

		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	// Returns price of cars at this location
	public int queryCarsPrice(int id, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			if(lockManager.Lock(id, "Car"+location, LockManager.READ)){
				rmList.add(rmCar);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmCar.queryCarsPrice(id,location);
			}
			return -1;
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
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
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
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
			catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
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
			} catch(Exception e){
				System.out.println("EXCEPTION:");
				System.out.println(e.getMessage());
				e.printStackTrace();
				throw e;
			}
			return false;
		}
	}

	// Adds car reservation to this customer. 
	public boolean reserveCar(int id, int customerID, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException{
		try{
			if(lockManager.Lock(id, "Car"+location, LockManager.WRITE)){
				rmList.add(rmCar);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmCar.reserveCar(id,customerID,location);
			}
			else{
				return false;
			}
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	// Adds room reservation to this customer. 
	public boolean reserveRoom(int id, int customerID, String location)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			if(lockManager.Lock(id, "Room"+location, LockManager.WRITE)){
				rmList.add(rmRoom);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmRoom.reserveRoom(id,customerID,location);
			}
			else{
				return false;
			}		
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	// Adds flight reservation to this customer.  
	public boolean reserveFlight(int id, int customerID, int flightNum)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
			if(lockManager.Lock(id, "Flight"+flightNum, LockManager.WRITE)){
				rmList.add(rmFlight);
				transactionManager.enlist(id, rmList);
				rmList.clear();
				return rmFlight.reserveFlight(id,customerID,flightNum);
			}
			else{
				return false;
			}
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	// Reserve an itinerary 
	public boolean itinerary(int id, int customer, Vector flightNumbers,String location,boolean Car,boolean Room)
			throws RemoteException, DeadlockException, InvalidTransactionException {
		try{
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

			//Reserve flight
			for(int i = 0; i<flightNumbers.size(); i++){
				if(lockManager.Lock(id, "Flight"+flightNumbers.get(i), LockManager.WRITE)){
					rmFlight.reserveFlight(id, customer, Integer.parseInt(flightNumbers.get(i).toString()));
				}				
			}
			//Reserve car, if wanted
			if(Car){
				rmList.add(rmCar);
				if(lockManager.Lock(id, "Car"+location, LockManager.WRITE)){
					rmCar.reserveCar(id, customer, location);
				}				
			}
			//Reserve room, if wanted
			if(Room){
				rmList.add(rmRoom);
				if(lockManager.Lock(id, "Room"+location, LockManager.WRITE)){
					rmRoom.reserveRoom(id, customer, location);
				}				
			}
			transactionManager.enlist(id, rmList);
			rmList.clear();
		}
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
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
		catch(Exception e){
			System.out.println("EXCEPTION:");
			System.out.println(e.getMessage());
			e.printStackTrace();
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
		int newTr = transactionManager.start();
		System.out.println("New transaction " + newTr + " started.");
		return newTr;
	}

	@Override
	public boolean commit(int transactionId) throws RemoteException,
	TransactionAbortedException, InvalidTransactionException {		
		boolean returnValue = transactionManager.commit(transactionId, this);
		lockManager.UnlockAll(transactionId);
		System.out.println("Transaction " + transactionId + " has committed.");
		for (int i = 0; i < ongoingTransactions.size(); i++) {
			if (ongoingTransactions.get(i).getID() == transactionId) {
				return (returnValue && ongoingTransactions.remove(ongoingTransactions.get(i)));
			}
		}
		return returnValue;
	}

	@Override
	public void abort(int transactionId) throws RemoteException, InvalidTransactionException {
		transactionManager.abort(transactionId, this);
		for (int i = 0; i < ongoingTransactions.size(); i++) {
			if (ongoingTransactions.get(i).getID() == transactionId) {
				ongoingTransactions.get(i).undo();
				ongoingTransactions.remove(ongoingTransactions.get(i));
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
		ongoingTransactions.add(t);
	}

}
