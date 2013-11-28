// -------------------------------
// adapted from Kevin T. Manley
// CSE 593
//

package server.resImpl;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RMISecurityManager;
import server.resInterface.*;
import transaction.DiskAccess;
import transaction.Operation;
import transaction.Transaction;
import transaction.TransactionManager;


public class ResourceManagerImpl implements server.resInterface.ResourceManager 
{

	public static final String SEPARATOR = ",";
	public static final String PATHING = System.getProperty("user.dir").substring(0,System.getProperty("user.dir").length() - 3);

	protected RMHashtable m_itemHT = new RMHashtable();
	private LinkedList<Transaction> ongoingTransactions;
	int trCount;
	private static String responsibility;
	static String name;
	static String server;
	static int port;
	DiskAccess stableStorage;

	private boolean crashBeforeVoting;
	private boolean crashAfterVoting;
	private boolean crashAfterDecision;
	private int transactionToCrash;

	private static AtomicBoolean crashNow;

	public static void main(String args[]) {
		// Figure out where server is running
		server = "localhost";
		port = 1099;
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
		ResourceManagerImpl obj = null;
		try {
			// create a new Server object
			obj = new ResourceManagerImpl();
			// dynamically generate the stub (client proxy)
			ResourceManager rm = (ResourceManager) UnicastRemoteObject.exportObject(obj, 0);

			// Bind the remote object's stub in the registry
			Registry registry = LocateRegistry.getRegistry(port);
			// Defining the RM's task
			responsibility = args[0];
			name = responsibility+"21ResourceManager";
			registry.rebind(name, rm);

			System.err.println("Server ready");
		} catch (Exception e) {
			System.err.println("Server exception: " + e.toString());
			e.printStackTrace();
		}

		// Create and install a security manager
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new RMISecurityManager());
		}
		obj.initiateFromDisk();
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		while(true){
			try {
				Thread.sleep(2);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if(crashNow.get()){
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				obj.selfDestruct();
			}
		}
	}

	public ResourceManagerImpl() throws RemoteException {
		ongoingTransactions = new LinkedList<Transaction>();
		trCount = 0;
		crashBeforeVoting = false;
		crashAfterVoting = false;
		crashAfterDecision = false;
		transactionToCrash = 0;		
		crashNow = new AtomicBoolean();
	}

	private void initiateFromDisk() {
		try {
			stableStorage = new DiskAccess(this, responsibility);
			stableStorage.memInit(this);
			stableStorage.readLog(this);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public String getName() {
		return name;
	}

	public void setCrashBeforeVoting(boolean crash) {
		crashBeforeVoting = crash;
	}

	public void setcrashAfterVoting(boolean crash) {
		crashAfterVoting = crash;
	}

	public void setcrashAfterDecision(boolean crash) {
		crashAfterDecision = crash;
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
	public boolean deleteCustomer(int id, int customerID) throws RemoteException {
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
	public boolean reserveFlight(int id, int customerID, int flightNum) throws RemoteException
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
			if (reservationType[i].equalsIgnoreCase("flight")) {
				reserveItem(0, cID, Flight.getKey(Integer.parseInt(reservationIdentifier[i])), reservationIdentifier[i]);
			} else if (reservationType[i].equalsIgnoreCase("car")) {
				reserveItem(0, cID, Car.getKey(reservationIdentifier[i]), reservationIdentifier[i]);
			} else if (reservationType[i].equalsIgnoreCase("room")) {
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
			if (reservedkey.equalsIgnoreCase(parameters[1])) {
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
			if (reservedkey.equalsIgnoreCase(parameters[1])) {
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
			if (reservedkey.equalsIgnoreCase(parameters[1])) {
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

	@Override
	public boolean shutdown() throws RemoteException {
		return true;
	}

	// stub
	public int start() throws RemoteException {
		trCount++;
		return trCount;
	}

	public boolean commit(int transactionId) throws RemoteException,
	TransactionAbortedException, InvalidTransactionException {
		if (canCommit(transactionId)) {
			if (doCommit(transactionId)) {
				return true;
			} else {
				return false;
			}
		} else {
			abort(transactionId);
			return false;
		}
	}

	public boolean canCommit(int transactionId) throws RemoteException, 
	TransactionAbortedException, InvalidTransactionException {		
		if(crashBeforeVoting && transactionToCrash == transactionId){
			selfDestruct();
		}
		boolean canCommit = false;

		for (Transaction t: ongoingTransactions) {
			if (t.getID() == transactionId) {
				// if t's status is still ongoing.
				// Add vote yes to log for trxn t.
				String operation = transactionId + ", canCommit, YES \n";
				System.out.println("Yes, I do commit <3");
				try {
					stableStorage.writeToLog(operation);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				canCommit = true;
				break;
			}
		}
		if(crashAfterVoting && transactionToCrash == transactionId){
			crashNow.set(true);
		}	
		return canCommit;
	}

	@Override
	public boolean doCommit(int transactionId) throws RemoteException,
	TransactionAbortedException, InvalidTransactionException {
		if(crashAfterDecision && transactionToCrash == transactionId){
			selfDestruct();
		}
		try{	
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == transactionId) {
					List<Operation> ops = t.getOperations(); 
					for (int i = ops.size()-1; i >= 0; i--) {
						for (String dataName: ops.get(i).getDataNames()) {
							if(ops.get(i).getOpName().equals("deletecustomer")) {
								stableStorage.deleteData(dataName);
							} else {
								String updatedLine = convertItemLine(dataName);
								stableStorage.updateData(dataName, updatedLine);
							}
						}
					}
					break;
				}
			}
			stableStorage.masterSwitch(transactionId);
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == transactionId) {
					List<Operation> ops = t.getOperations(); 
					for (int i = ops.size()-1; i >= 0; i--) {
						for (String dataName: ops.get(i).getDataNames()) {
							if(ops.get(i).getOpName().equals("deletecustomer")) {
								stableStorage.deleteData(dataName);
							} else {
								String updatedLine = convertItemLine(dataName);
								stableStorage.updateData(dataName, updatedLine);
							}
						}
					}
					break;
				}
			}
		}catch(Exception e){
			e.printStackTrace();
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
	public void abort(int transactionId) throws RemoteException, InvalidTransactionException {
		synchronized(ongoingTransactions) {
			for (int i = 0; i < ongoingTransactions.size(); i++) {
				if (ongoingTransactions.get(i).getID() == transactionId) {
					ongoingTransactions.get(i).undo();
					ongoingTransactions.remove(ongoingTransactions.get(i));
				}	
			}
		}
		String operation = transactionId + ", abort\n";
		try{
			stableStorage.writeToLog(operation);
			System.out.println("Writing op");
		}catch(Exception e){
			System.out.println("Some god damn exception");
		}
	}
	
	public void doAbort(int transactionId) throws RemoteException, InvalidTransactionException {
		if(crashAfterDecision && transactionToCrash == transactionId){
			selfDestruct();
		}
		abort(transactionId);
	}

	public void neatCrash(int transactionId, int option){
		crashBeforeVoting = false;
		crashAfterVoting = false;
		crashAfterDecision = false;		

		transactionToCrash = transactionId;
		System.out.println("Ok, I am to crash at some point. =(");
		switch(option){
		case 1:
			crashBeforeVoting = true;
			break;
		case 2:
			crashAfterVoting = true;
			break;
		case 3:
			crashAfterDecision = true;
			break;
		default:
			System.out.println("What are you talking about, this option is not an option.");
		}
	}

	public void selfDestruct(){
		System.out.println("Why me?? :(");
		try {
		Registry registry = LocateRegistry.getRegistry(port);
		// Defining the RM's task
			registry.unbind(name);
		} catch (RemoteException | NotBoundException e) {
			e.printStackTrace();
		}
		System.exit(1);
	}

	private void addOperation(int id, Operation op) {
		if (id == 0) {return;}
		synchronized(ongoingTransactions) {
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == id) {
					t.addOp(op);
					stableStorage.logOperation(id, op);
					return;
				}
			}
		}
		Transaction t = new Transaction(id);
		t.addOp(op);
		synchronized(ongoingTransactions) {
			ongoingTransactions.add(t);
		}
		stableStorage.logOperation(id, op);
	}


	private String convertItemLine(String dataName) {
		String line = "";
		System.out.println("Name is " + dataName);
		if (dataName.length() >= 6 && dataName.substring(0, 6).equalsIgnoreCase("Flight")) {
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
			String rawData;
			try {
				rawData = queryCustomerInfo(0, Integer.parseInt(dataName.substring(8)));
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



}
