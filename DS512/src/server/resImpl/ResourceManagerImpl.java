// -------------------------------
// adapted from Kevin T. Manley
// CSE 593
//

package server.resImpl;

import java.util.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RMISecurityManager;

import server.resInterface.*;
import transaction.Operation;
import transaction.Transaction;


public class ResourceManagerImpl implements server.resInterface.ResourceManager 
{

	public class RAFList {
		RandomAccessFile cur;
		RAFList next;
		String name;
		
		RAFList(String n, String location, String mode) {
			name = n;
			try {
				cur = new RandomAccessFile(location, mode);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			next = null;
		}
		
		public void setNext(RAFList n) {
			next = n;
		}
		
		public RAFList getNext() {
			return next;
		}
		
		public RandomAccessFile getFileAccess() {
			return cur;
		}
		
		public String getName() {
			return name;
		}	
		
		// To do: returns line in database where data is held for 'dataname' object is held.
		public int getLine(String dataName) {
			return 0;
		}

		// Rewrite line in database with information from RMItem
		public void rewriteLine(int line, RMItem itemToRewrite) {
			// TODO Auto-generated method stub
			// set itemToRewrite in readableItemCSVForm
			// Will go and cur.write(readableItemCSVForm, line * TransactionManager.LINE_SIZE);
		}
	}
	
	protected RMHashtable m_itemHT = new RMHashtable();
	private LinkedList<Transaction> ongoingTransactions;
	int trCount;
	// Add transaction log
	private static String responsibility;
	private RAFList recordA;
	private RAFList recordB;
	private RAFList masterRec;
	private RAFList workingRec;
	private RAFList stateLog;
	
	private int txnMaster;

	public static void main(String args[]) {
		// Figure out where server is running
		String server = "localhost";
		int port = 1099;
		responsibility = "";

		if (args.length > 0) {
			responsibility = args[0];           
		}
		if (args.length > 1){
			server = server + ":" + args[1];
			port = Integer.parseInt(args[1]);
		}
		if (args.length > 2) {
			System.err.println ("Wrong usage");
			System.out.println("Usage: java ResImpl.ResourceManagerImpl responsibility [port] ");
			System.exit(1);
		}

		try {
			// create a new Server object
			ResourceManagerImpl obj = new ResourceManagerImpl();
			// dynamically generate the stub (client proxy)
			ResourceManager rm = (ResourceManager) UnicastRemoteObject.exportObject(obj, 0);

			// Bind the remote object's stub in the registry
			Registry registry = LocateRegistry.getRegistry(port);
			// Defining the RM's task
			responsibility = args[0];
			registry.rebind(responsibility+"21ResourceManager", rm);

			System.err.println("Server ready");
		} catch (Exception e) {
			System.err.println("Server exception: " + e.toString());
			e.printStackTrace();
		}

		// Create and install a security manager
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new RMISecurityManager());
		}

	}

	public ResourceManagerImpl() throws RemoteException, FileAlreadyExistsException, IOException {
		ongoingTransactions = new LinkedList<Transaction>();
		trCount = 0;
		txnMaster = -1;

		Charset charset = Charset.forName("US-ASCII");
		Path pathRMRecordA = Paths.get(responsibility + "/" + responsibility + "_Record_A");
		Path pathRMRecordB = Paths.get(responsibility + "/" + responsibility + "_Record_B");
		Path pathStateLog = Paths.get(responsibility + "/" + responsibility + "_StateLog");
		String locationA = responsibility + "/RecordA";
		String locationB = responsibility + "/RecordB";
		String locationLoh = responsibility + "/StateLog";
		try{
			
			recordA = new RAFList("A", locationA, "rwd");
			recordB = new RAFList("B", locationB, "rwd");
			stateLog = new RAFList("Log", locationB, "rwd");
			
			masterRec = getMasterRecord();
			workingRec = masterRec.getNext();
			
			
			Files.createFile(pathRMRecordA);
			Files.createFile(pathRMRecordB);

			write_stateLog.write("A");
		}catch(FileAlreadyExistsException e){
			System.out.println("Files already exist: " + e.getFile());
		}catch(Exception e){
			System.out.println(e);
		}
	}

	private RAFList getMasterRecord() {
		// TODO Auto-generated method stub
		return recordA;
	}

	// Reads a data item
	private RMItem readData( int id, String key )
	{
		synchronized(m_itemHT) {
			return (RMItem) m_itemHT.get(key);
		}
	}

	// Writes a data item
	private void writeData( int id, String key, RMItem value )
	{
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


	// deletes the entire item
	protected boolean deleteItem(int id, String key)
	{
		Trace.info("RM::deleteItem(" + id + ", " + key + ") called" );
		ReservableItem curObj = (ReservableItem) readData( id, key );
		// Check if there is such an item in the storage
		if ( curObj == null ) {
			Trace.warn("RM::deleteItem(" + id + ", " + key + ") failed--item doesn't exist" );
			return false;
		} else {
			synchronized(curObj) {
				if (curObj.getReserved()==0) {
					removeData(id, curObj.getKey());
					Trace.info("RM::deleteItem(" + id + ", " + key + ") item deleted" );
					return true;
				}
				else {
					Trace.info("RM::deleteItem(" + id + ", " + key + ") item can't be deleted because some customers reserved it" );
					return false;
				}
			}
		} // if
	}


	// query the number of available seats/rooms/cars
	protected int queryNum(int id, String key) {
		Trace.info("RM::queryNum(" + id + ", " + key + ") called" );
		ReservableItem curObj = (ReservableItem) readData( id, key);
		int value = 0;  
		if ( curObj != null ) {
			value = curObj.getCount();
		}else{
			return -1;
		}
		Trace.info("RM::queryNum(" + id + ", " + key + ") returns count=" + value);
		return value;
	}    

	// query the price of an item
	protected int queryPrice(int id, String key) {
		Trace.info("RM::queryCarsPrice(" + id + ", " + key + ") called" );
		ReservableItem curObj = (ReservableItem) readData( id, key);
		int value = 0; 
		if ( curObj != null ) {
			value = curObj.getPrice();
		}else{
			return -1;
		}
		Trace.info("RM::queryCarsPrice(" + id + ", " + key + ") returns cost=$" + value );
		return value;        
	}

	// reserve an item
	protected boolean reserveItem(int id, int customerID, String key, String location) {
		Trace.info("RM::reserveItem( " + id + ", customer=" + customerID + ", " +key+ ", "+location+" ) called" );        
		// Read customer object if it exists (and read lock it)
		Customer cust = (Customer) readData( id, Customer.getKey(customerID) );        
		if ( cust == null ) {
			Trace.warn("RM::reserveCar( " + id + ", " + customerID + ", " + key + ", "+location+")  failed--customer doesn't exist" );
			return false;
		} 

		// check if the item is available
		ReservableItem item = (ReservableItem)readData(id, key);
		if ( item == null ) {
			Trace.warn("RM::reserveItem( " + id + ", " + customerID + ", " + key+", " +location+") failed--item doesn't exist" );
			return false;
		} else {
			synchronized(item) {
				if (item.getCount()==0) {
					Trace.warn("RM::reserveItem( " + id + ", " + customerID + ", " + key+", " + location+") failed--No more items" );
					return false;

				} else {            
					cust.reserve( key, location, item.getPrice());        
					writeData( id, cust.getKey(), cust );

					// decrease the number of available items in the storage
					item.setCount(item.getCount() - 1);
					item.setReserved(item.getReserved()+1);

					Trace.info("RM::reserveItem( " + id + ", " + customerID + ", " + key + ", " +location+") succeeded" );
					return true;
				}   
			}
		}
	}

	// Create a new flight, or add seats to existing flight
	//  NOTE: if flightPrice <= 0 and the flight already exists, it maintains its current price
	public boolean addFlight(int id, int flightNum, int flightSeats, int flightPrice)
			throws RemoteException
			{

		Trace.info("RM::addFlight(" + id + ", " + flightNum + ", $" + flightPrice + ", " + flightSeats + ") called" );
		Flight curObj = (Flight) readData( id, Flight.getKey(flightNum) );
		if ( curObj == null ) {
			// doesn't exist...add it
			Flight newObj = new Flight( flightNum, flightSeats, flightPrice );
			writeData( id, newObj.getKey(), newObj );
			Trace.info("RM::addFlight(" + id + ") created new flight " + flightNum + ", seats=" +
					flightSeats + ", price=$" + flightPrice );
			String[] parameters = {((Integer)flightNum).toString(),((Integer)flightSeats).toString(), ((Integer)flightPrice).toString()}; 
			Operation op = new Operation("newflight", parameters, this);
			addOperation(id, op);
		} else {
			// add seats to existing flight and update the price...
			curObj.setCount( curObj.getCount() + flightSeats );
			String[] parameters = {((Integer)flightNum).toString(),((Integer)flightSeats).toString(), ((Integer)curObj.getPrice()).toString()}; 
			Operation op = new Operation("newflight", parameters, this);
			addOperation(id, op);
			if ( flightPrice > 0 ) {
				curObj.setPrice( flightPrice );
			} // if
			writeData( id, curObj.getKey(), curObj );
			Trace.info("RM::addFlight(" + id + ") modified existing flight " + flightNum + ", seats=" + curObj.getCount() + ", price=$" + flightPrice );
		} // else
		return(true);
			}


	public boolean deleteFlight(int id, int flightNum)
			throws RemoteException
			{
		Flight curObj = (Flight) readData(0, Flight.getKey(flightNum));
		if (deleteItem(id, Flight.getKey(flightNum))) {
			String[] parameters = {((Integer)flightNum).toString(),((Integer)curObj.getCount()).toString(), ((Integer)curObj.getPrice()).toString()}; 
			Operation op = new Operation("deleteflight", parameters, this);
			addOperation(id, op);			
			return true;
		} else {
			return false;
		}
			}

	// Create a new room location or add rooms to an existing location
	//  NOTE: if price <= 0 and the room location already exists, it maintains its current price
	public boolean addRooms(int id, String location, int count, int price)
			throws RemoteException
			{
		Trace.info("RM::addRooms(" + id + ", " + location + ", " + count + ", $" + price + ") called" );
		Hotel curObj = (Hotel) readData( id, Hotel.getKey(location) );
		if ( curObj == null ) {
			// doesn't exist...add it
			Hotel newObj = new Hotel( location, count, price );
			writeData( id, newObj.getKey(), newObj );
			Trace.info("RM::addRooms(" + id + ") created new room location " + location + ", count=" + count + ", price=$" + price );
			String[] parameters = {location,((Integer)count).toString(), ((Integer)price).toString()}; 
			Operation op = new Operation("newroom", parameters, this);
			addOperation(id, op);
		} else {
			// add count to existing object and update price...
			curObj.setCount( curObj.getCount() + count );
			String[] parameters = {location,((Integer)count).toString(), ((Integer)curObj.getPrice()).toString()}; 
			Operation op = new Operation("newroom", parameters, this);
			addOperation(id, op);
			if ( price > 0 ) {
				curObj.setPrice( price );
			} // if
			writeData( id, curObj.getKey(), curObj );
			Trace.info("RM::addRooms(" + id + ") modified existing location " + location + ", count=" + curObj.getCount() + ", price=$" + price );
		} // else
		return(true);
			}

	// Delete rooms from a location
	public boolean deleteRooms(int id, String location)
			throws RemoteException
			{
		Hotel curObj = (Hotel) readData( id, Hotel.getKey(location) );
		if (deleteItem(id, Hotel.getKey(location))) {
			String[] parameters = {location,((Integer)curObj.getCount()).toString(), ((Integer)curObj.getPrice()).toString()}; 
			Operation op = new Operation("deleteroom", parameters, this);
			addOperation(id, op);
			return true;
		} else {
			return false;
		}
			}

	// Create a new car location or add cars to an existing location
	//  NOTE: if price <= 0 and the location already exists, it maintains its current price
	public boolean addCars(int id, String location, int count, int price)
			throws RemoteException
			{
		Trace.info("RM::addCars(" + id + ", " + location + ", " + count + ", $" + price + ") called" );
		Car curObj = (Car) readData( id, Car.getKey(location) );
		if ( curObj == null ) {
			// car location doesn't exist...add it
			Car newObj = new Car( location, count, price );
			writeData( id, newObj.getKey(), newObj );
			Trace.info("RM::addCars(" + id + ") created new location " + location + ", count=" + count + ", price=$" + price );
			String[] parameters = {location,((Integer)count).toString(), ((Integer)price).toString()}; 
			Operation op = new Operation("newcar", parameters, this);
			addOperation(id, op);
		} else {
			// add count to existing car location and update price...
			curObj.setCount( curObj.getCount() + count );
			String[] parameters = {location,((Integer)count).toString(), ((Integer)curObj.getPrice()).toString()}; 
			Operation op = new Operation("newcar", parameters, this);
			addOperation(id, op);
			if ( price > 0 ) {
				curObj.setPrice( price );
			} // if
			writeData( id, curObj.getKey(), curObj );
			Trace.info("RM::addCars(" + id + ") modified existing location " + location + ", count=" + curObj.getCount() + ", price=$" + price );
		} // else
		return(true);
			}


	// Delete cars from a location
	public boolean deleteCars(int id, String location)
			throws RemoteException
			{
		Car curObj = (Car) readData( id, Car.getKey(location) );
		if (deleteItem(id, Car.getKey(location))) {
			String[] parameters = {location,((Integer)curObj.getCount()).toString(), ((Integer)curObj.getPrice()).toString()}; 
			Operation op = new Operation("deletecar", parameters, this);
			addOperation(id, op);
			return true;
		} else {
			return false;
		}
			}



	// Returns the number of empty seats on this flight
	public int queryFlight(int id, int flightNum)
			throws RemoteException
			{
		return queryNum(id, Flight.getKey(flightNum));
			}

	// Returns the number of reservations for this flight. 
	//    public int queryFlightReservations(int id, int flightNum)
	//        throws RemoteException
	//    {
	//        Trace.info("RM::queryFlightReservations(" + id + ", #" + flightNum + ") called" );
	//        RMInteger numReservations = (RMInteger) readData( id, Flight.getNumReservationsKey(flightNum) );
	//        if ( numReservations == null ) {
	//            numReservations = new RMInteger(0);
	//        } // if
	//        Trace.info("RM::queryFlightReservations(" + id + ", #" + flightNum + ") returns " + numReservations );
	//        return numReservations.getValue();
	//    }


	// Returns price of this flight
	public int queryFlightPrice(int id, int flightNum )
			throws RemoteException
			{
		return queryPrice(id, Flight.getKey(flightNum));
			}


	// Returns the number of rooms available at a location
	public int queryRooms(int id, String location)
			throws RemoteException
			{
		return queryNum(id, Hotel.getKey(location));
			}




	// Returns room price at this location
	public int queryRoomsPrice(int id, String location)
			throws RemoteException
			{
		return queryPrice(id, Hotel.getKey(location));
			}


	// Returns the number of cars available at a location
	public int queryCars(int id, String location)
			throws RemoteException
			{
		return queryNum(id, Car.getKey(location));
			}


	// Returns price of cars at this location
	public int queryCarsPrice(int id, String location)
			throws RemoteException
			{
		return queryPrice(id, Car.getKey(location));
			}

	// Returns data structure containing customer reservation info. Returns null if the
	//  customer doesn't exist. Returns empty RMHashtable if customer exists but has no
	//  reservations.
	public RMHashtable getCustomerReservations(int id, int customerID)
			throws RemoteException
			{
		Trace.info("RM::getCustomerReservations(" + id + ", " + customerID + ") called" );
		Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
		if ( cust == null ) {
			Trace.warn("RM::getCustomerReservations failed(" + id + ", " + customerID + ") failed--customer doesn't exist" );
			return null;
		} else {
			return cust.getReservations();
		} // if
			}

	// return a bill
	public String queryCustomerInfo(int id, int customerID)
			throws RemoteException
			{
		Trace.info("RM::queryCustomerInfo(" + id + ", " + customerID + ") called" );
		Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
		if ( cust == null ) {
			Trace.warn("RM::queryCustomerInfo(" + id + ", " + customerID + ") failed--customer doesn't exist" );
			return "";   // NOTE: don't change this--WC counts on this value indicating a customer does not exist...
		} else {
			String s = cust.printBill();
			Trace.info("RM::queryCustomerInfo(" + id + ", " + customerID + "), bill follows..." );
			System.out.println( s );
			return s;
		} // if
			}

	// customer functions
	// new customer just returns a unique customer identifier

	public int newCustomer(int id)
			throws RemoteException
			{
		Trace.info("INFO: RM::newCustomer(" + id + ") called" );
		// Generate a globally unique ID for the new customer
		int cid = Integer.parseInt( String.valueOf(id) +
				String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
				String.valueOf( Math.round( Math.random() * 100 + 1 )));
		Customer cust = new Customer( cid );
		writeData( id, cust.getKey(), cust );
		String[] parameters = {((Integer)cid).toString()}; 
		Operation op = new Operation("newcustomer", parameters, this);
		addOperation(id, op);
		Trace.info("RM::newCustomer(" + cid + ") returns ID=" + cid );
		return cid;
			}

	// I opted to pass in customerID instead. This makes testing easier
	public boolean newCustomer(int id, int customerID )
			throws RemoteException
			{
		Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") called" );
		Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
		if ( cust == null ) {
			cust = new Customer(customerID);
			writeData( id, cust.getKey(), cust );
			Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") created a new customer" );
			String[] parameters = {((Integer)customerID).toString()}; 
			Operation op = new Operation("newcustomer", parameters, this);
			addOperation(id, op);
			return true;
		} else {
			Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") failed--customer already exists");
			return false;
		} // else
			}


	// Deletes customer from the database. 
	public boolean deleteCustomer(int id, int customerID)
			throws RemoteException
			{
		Trace.info("RM::deleteCustomer(" + id + ", " + customerID + ") called" );
		Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
		if ( cust == null ) {
			Trace.warn("RM::deleteCustomer(" + id + ", " + customerID + ") failed--customer doesn't exist" );
			return false;
		} else {            
			// Increase the reserved numbers of all reservable items which the customer reserved.
			String[] opParameters = new String[3];
			opParameters[0] = ((Integer)customerID).toString();
			opParameters[1] = "";
			opParameters[2] = "";
			String rawData = queryCustomerInfo(id, customerID);
			String[] lines = rawData.split("\n");
			for (int i = 1; i < lines.length; i++) {
				String[] parameters = lines[i].split(" ");
				for (int j = 0; j < Integer.parseInt(parameters[0]); j++) {
					String[] resTypeAndKey = parameters[1].split("-");
					opParameters[1] += "::" + resTypeAndKey[0];
					opParameters[2] += "::" + resTypeAndKey[1];
				}
			}
			if (opParameters[1].length() > 0) {
				opParameters[1] = opParameters[1].substring(2);
				opParameters[2] = opParameters[2].substring(2);
			}
			Operation op = new Operation("deletecustomer", opParameters, this);
			addOperation(id, op);			

			RMHashtable reservationHT = cust.getReservations();

			for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {        
				String reservedkey = (String) (e.nextElement());
				ReservedItem reserveditem = cust.getReservedItem(reservedkey);
				Trace.info("RM::deleteCustomer(" + id + ", " + customerID + ") has reserved " + reserveditem.getKey() + " " +  reserveditem.getCount() +  " times"  );
				ReservableItem item  = (ReservableItem) readData(id, reserveditem.getKey());
				Trace.info("RM::deleteCustomer(" + id + ", " + customerID + ") has reserved " + reserveditem.getKey() + "which is reserved" +  item.getReserved() +  " times and is still available " + item.getCount() + " times"  );
				item.setReserved(item.getReserved()-reserveditem.getCount());
				item.setCount(item.getCount()+reserveditem.getCount());
			}


			// remove the customer from the storage
			removeData(id, cust.getKey());

			Trace.info("RM::deleteCustomer(" + id + ", " + customerID + ") succeeded" );
			return true;
		} // if
			}



	// Adds car reservation to this customer. 
	public boolean reserveCar(int id, int customerID, String location)
			throws RemoteException
			{
		if (reserveItem(id, customerID, Car.getKey(location), location)) {
			String[] parameters = {String.valueOf(customerID), Car.getKey(location)};		 
			Operation op = new Operation("reservecar", parameters, this);
			addOperation(id, op);
			return true;
		} else {
			return false;
		}
			}


	// Adds room reservation to this customer. 
	public boolean reserveRoom(int id, int customerID, String location)
			throws RemoteException
			{
		if (reserveItem(id, customerID, Hotel.getKey(location), location)) {
			String[] parameters = {String.valueOf(customerID), Hotel.getKey(location)};		 
			Operation op = new Operation("reserveroom", parameters, this);
			addOperation(id, op);
			return true;
		} else {
			return false;
		}

			}


	// Adds flight reservation to this customer.  
	public boolean reserveFlight(int id, int customerID, int flightNum)
			throws RemoteException
			{
		if (reserveItem(id, customerID, Flight.getKey(flightNum), String.valueOf(flightNum))) {
			String[] parameters = {String.valueOf(customerID), Flight.getKey(flightNum)};		 
			Operation op = new Operation("reserveflight", parameters, this);
			addOperation(id, op);
			return true;
		} else {
			return false;
		}

			}

	// Reserve an itinerary 
	public boolean itinerary(int id,int customer, Vector flightNumbers,String location,boolean Car,boolean Room)
			throws RemoteException
			{
		return false;
			}

	@Override
	public boolean shutdown() throws RemoteException {
		return true;
	}

	// stub
	public int start() throws RemoteException {
		trCount++;
		return trCount;
	}

	public boolean canCommit(int transactionId) throws RemoteException, 
							TransactionAbortedException, InvalidTransactionException {
		synchronized(ongoingTransactions) {
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == transactionId) {
					// if t's status is still ongoing.
					// Add vote yes to log for trxn t.
					// set transaction to have a ready to commit value.
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public boolean commit(int transactionId) throws RemoteException,
	TransactionAbortedException, InvalidTransactionException {
		try{	
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == transactionId) {
					List<Operation> ops = t.getOperations(); 
					for (int i = ops.size()-1; i >= 0; i--) {
						for (String dataName: ops.get(i).getDataNames()) {
							int line = workingRec.getLine(dataName);
							workingRec.rewriteLine(line, readData(0, dataName));
						}
						
						
					}
					break;
				}
			}
			masterSwitch(transactionId);
		}catch(Exception e){
		}
		synchronized(ongoingTransactions) {
			for (int i = 0; i < ongoingTransactions.size(); i++) {
				if (ongoingTransactions.get(i).getID() == transactionId) {
					// write commit to disk.
					// add information to log of successful commit transaction.
					ongoingTransactions.remove(ongoingTransactions.get(i));
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void abort(int transactionId) throws RemoteException,
	InvalidTransactionException {
		synchronized(ongoingTransactions) {
			for (int i = 0; i < ongoingTransactions.size(); i++) {
				if (ongoingTransactions.get(i).getID() == transactionId) {
					ongoingTransactions.get(i).undo();
					ongoingTransactions.remove(ongoingTransactions.get(i));
				}	
			}
		}
	}


	// Parameter 0: flight number, parameter 1: number of seats, parameters 2: previous price.
	public void cancelNewFlight(String[] parameters) {
		int flightNum = Integer.parseInt(parameters[0]);
		Flight curObj = (Flight) readData(0, Flight.getKey(flightNum));
		if ( curObj != null ) {

			if (curObj.getCount() - Integer.parseInt(parameters[1]) > 0) {
				curObj.setCount(curObj.getCount() - Integer.parseInt(parameters[1]));
				curObj.setPrice( Integer.parseInt(parameters[2]));
				writeData( 0, curObj.getKey(), curObj );
			} else {
				deleteItem(0, Flight.getKey(flightNum));
			}
		} // else

	}

	// Parameter 0: location, parameter 1: car count, parameter 2: previous price.
	public void cancelNewCar(String[] parameters) {

		Car curObj = (Car) readData(0, Car.getKey(parameters[0]) );
		if (curObj != null) {
			if (curObj.getCount() - Integer.parseInt(parameters[1]) > 0) {
				curObj.setCount(curObj.getCount() - Integer.parseInt(parameters[1]));
				curObj.setPrice(Integer.parseInt(parameters[2]));
				writeData( 0, curObj.getKey(), curObj);
			} else {
				deleteItem(0, Car.getKey(parameters[0]));
			}
		}

	}

	// Parameter 0: location, parameter 1: room count, parameter 2: previous price.
	public void cancelNewRoom(String[] parameters) {
		Hotel curObj = (Hotel) readData( 0, Hotel.getKey(parameters[0]) );
		if ( curObj != null ) {
			if (curObj.getCount() - Integer.parseInt(parameters[1]) > 0) {
				curObj.setCount(curObj.getCount() - Integer.parseInt(parameters[1]));
				curObj.setPrice(Integer.parseInt(parameters[2]));
				writeData( 0, curObj.getKey(), curObj);
			} else {
				deleteItem(0, Hotel.getKey(parameters[0]));
			}
		} 
	}

	// Parameter 0: cid
	public void cancelNewCustomer(String[] parameters) {
		// Generate a globally unique ID for the new customer
		removeData(0, Customer.getKey(Integer.parseInt(parameters[0])));
	}


	public void cancelFlightDeletion(String[] parameters) {
		try {
			Flight newObj = new Flight(Integer.parseInt(parameters[0]), Integer.parseInt(parameters[1]), Integer.parseInt(parameters[2]));
			writeData( 0, newObj.getKey(), newObj );
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
	}

	public void cancelCarDeletion(String[] parameters) {
		try {
			Car newObj = new Car(parameters[0], Integer.parseInt(parameters[1]), Integer.parseInt(parameters[2]));
			writeData(0, newObj.getKey(), newObj );
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}		
	}

	public void cancelRoomDeletion(String[] parameters) {
		try {
			Hotel newObj = new Hotel( parameters[0], Integer.parseInt(parameters[1]), Integer.parseInt(parameters[2]));
			writeData( 0, newObj.getKey(), newObj );
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}		
	}

	// Parameter 0: customerID, parameter 1: type of reservation for all reservations, parameter 2: reservation identifiers.
	public void cancelCustomerDeletion(String[] parameters) {
		int cID = Integer.parseInt(parameters[0]);
		Customer cust = new Customer(cID);
		writeData( 0, cust.getKey(), cust );
		String[] reservationType = parameters[1].split("::");
		String[] reservationIdentifier = parameters[2].split("::");

		for (int i = 0; i < reservationType.length; i++) {
			if (reservationType[i].equals("flight")) {
				reserveItem(0, cID, Flight.getKey(Integer.parseInt(reservationIdentifier[i])), reservationIdentifier[i]);
			} else if (reservationType[i].equals("car")) {
				reserveItem(0, cID, Car.getKey(reservationIdentifier[i]), reservationIdentifier[i]);
			} else if (reservationType[i].equals("room")) {
				reserveItem(0, cID, Hotel.getKey(reservationIdentifier[i]), reservationIdentifier[i]);
			}
		}
	}


	public void cancelFlightReservation(String[] parameters) {
		Customer cust = (Customer) readData( 0, Customer.getKey(Integer.parseInt(parameters[0])) );
		RMHashtable reservationHT = cust.getReservations();
		boolean canceled = false;
		ReservedItem ri = null; 
		for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {        
			String reservedkey = (String) (e.nextElement());
			if (reservedkey.equals(parameters[1])) {
				ReservedItem reserveditem = cust.getReservedItem(reservedkey);
				ri = reserveditem;
				canceled = true;
				ReservableItem item  = (ReservableItem) readData(0, reserveditem.getKey());
				item.setReserved(item.getReserved()-1);
				item.setCount(item.getCount()+1);
				break;
			}
		}
		if (canceled) {
			cust.getReservations().remove(ri.getKey());
		}
	}

	public void cancelCarReservation(String[] parameters) {
		Customer cust = (Customer) readData( 0, Customer.getKey(Integer.parseInt(parameters[0])) );
		RMHashtable reservationHT = cust.getReservations();
		boolean canceled = false;
		ReservedItem ri = null;

		for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {        
			String reservedkey = (String) (e.nextElement());
			if (reservedkey.equals(parameters[1])) {
				ReservedItem reserveditem = cust.getReservedItem(reservedkey);
				ri = reserveditem;
				canceled = true;
				ReservableItem item  = (ReservableItem) readData(0, reserveditem.getKey());
				item.setReserved(item.getReserved()-1);
				item.setCount(item.getCount()+1);
				break;
			}
		}
		if (canceled) {
			cust.getReservations().remove(ri.getKey());
		}
	}

	public void cancelRoomReservation(String[] parameters) {
		Customer cust = (Customer) readData( 0, Customer.getKey(Integer.parseInt(parameters[0])) );
		RMHashtable reservationHT = cust.getReservations();
		boolean canceled = false;
		ReservedItem ri = null;

		for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {        
			String reservedkey = (String) (e.nextElement());
			if (reservedkey.equals(parameters[1])) {
				ReservedItem reserveditem = cust.getReservedItem(reservedkey);
				ri = reserveditem;
				canceled = true;
				ReservableItem item  = (ReservableItem) readData(0, reserveditem.getKey());
				item.setReserved(item.getReserved()-1);
				item.setCount(item.getCount()+1);
				break;
			}
		}	
		if (canceled) {
			cust.getReservations().remove(ri.getKey());
		}
	}


	private void addOperation(int id, Operation op) {
		synchronized(ongoingTransactions) {
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == id) {
					t.addOp(op);
					return;
				}
			}
		}
		Transaction t = new Transaction(id);
		t.addOp(op);
		synchronized(ongoingTransactions) {
			ongoingTransactions.add(t);
		}
		
		//Add operation on stateLog file
		String operation = id + "," + op.getOpName() + ",";
		for (String param: op.getParameters()){
			operation += param + ",";
		}
		operation += "\n";
		try{
			write_stateLog.write(operation, 0, operation.length());
			//write_stateLog.newLine();
		}catch(Exception e){
		}
	}

	//Master points to the one which is currently the latest committed version, linked to transactionID
	private void masterSwitch(int transactionID){
		String operation = transactionID + ", commit ," + workingRec.getName();
		workingRec = workingRec.getNext();
		masterRec = masterRec.getNext();
			/*if(currentMaster.equals("A")){
				currentMaster = "B";
				master = read_recordB;
				working = write_recordA;
				operation += "A";
			}else{
				currentMaster = "A";
				master = read_recordA;
				working = write_recordB;
				operation += "A";
			}*/
		
		txnMaster = transactionID;
		try{
			stateLog.rewriteLine(line, itemToRewrite).write(operation);
		}catch(Exception e){
			System.out.println("Problem trying to modify stateLog file on switchMaster on commit of transaction with ID : " + transactionID);
			System.out.println(e);
		}		
	}
	
	/*private void clear(int transactionID){
	 * 
	 *Generate a new file called temp.
	 *In temp, write all transactions down.
	 *Close and delete current log.
	 *change name of temp to log.
	 *If crashes and no log file to be found, search for temp file.
	 *
		String line = "";
		try{
			while ((line = read_stateLog.readLine()) != null) {
				if(transactionID == Integer.parseInt(line.substring(0, line.indexOf(",")))){
					//write_stateLog
					//clear line
				}
				System.out.println(line);
			}
		}catch(Exception e){
			System.out.println("Problem trying to clear stateLog of commited transaction with ID: " + transactionID);
			System.out.println(e);
		}
	}*/
}