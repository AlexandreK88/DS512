package server.resInterface;


import java.rmi.Remote;
import java.rmi.RemoteException;

import java.util.*;

import transaction.Transaction;

import lockManager.DeadlockException;
/** 
 * Simplified version from CSE 593 Univ. of Washington
 *
 * Distributed  System in Java.
 * 
 * failure reporting is done using two pieces, exceptions and boolean 
 * return values.  Exceptions are used for systemy things. Return
 * values are used for operations that would affect the consistency
 * 
 * If there is a boolean return value and you're not sure how it 
 * would be used in your implementation, ignore it.  I used boolean
 * return values in the interface generously to allow flexibility in 
 * implementation.  But don't forget to return true when the operation
 * has succeeded.
 */

public interface ResourceManager extends Remote 
{
	public String getName();

	/* Add seats to a flight.  In general this will be used to create a new
	 * flight, but it should be possible to add seats to an existing flight.
	 * Adding to an existing flight should overwrite the current price of the
	 * available seats.
	 *
	 * @return success.
	 */
	public boolean addFlight(int id, int flightNum, int flightSeats, int flightPrice) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* Add cars to a location.  
	 * This should look a lot like addFlight, only keyed on a string location
	 * instead of a flight number.
	 */
	public boolean addCars(int id, String location, int numCars, int price) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* Add rooms to a location.  
	 * This should look a lot like addFlight, only keyed on a string location
	 * instead of a flight number.
	 */
	public boolean addRooms(int id, String location, int numRooms, int price) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 			    


	/* new customer just returns a unique customer identifier */
	public int newCustomer(int id) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* new customer with providing id */
	public boolean newCustomer(int id, int cid)
			throws RemoteException, DeadlockException, InvalidTransactionException;

	/**
	 *   Delete the entire flight.
	 *   deleteflight implies whole deletion of the flight.  
	 *   all seats, all reservations.  If there is a reservation on the flight, 
	 *   then the flight cannot be deleted
	 *
	 * @return success.
	 */   
	public boolean deleteFlight(int id, int flightNum) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* Delete all Cars from a location.
	 * It may not succeed if there are reservations for this location
	 *
	 * @return success
	 */		    
	public boolean deleteCars(int id, String location) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* Delete all Rooms from a location.
	 * It may not succeed if there are reservations for this location.
	 *
	 * @return success
	 */
	public boolean deleteRooms(int id, String location) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* deleteCustomer removes the customer and associated reservations */
	public boolean deleteCustomer(int id,int customer) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* queryFlight returns the number of empty seats. */
	public int queryFlight(int id, int flightNumber) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* return the number of cars available at a location */
	public int queryCars(int id, String location) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* return the number of rooms available at a location */
	public int queryRooms(int id, String location) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* return a bill */
	public String queryCustomerInfo(int id,int customer) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* queryFlightPrice returns the price of a seat on this flight. */
	public int queryFlightPrice(int id, int flightNumber) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* return the price of a car at a location */
	public int queryCarsPrice(int id, String location) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* return the price of a room at a location */
	public int queryRoomsPrice(int id, String location) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* Reserve a seat on this flight*/
	public boolean reserveFlight(int id, int customer, int flightNumber) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* reserve a car at this location */
	public boolean reserveCar(int id, int customer, String location) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	/* reserve a room certain at this location */
	public boolean reserveRoom(int id, int customer, String locationd) 
			throws RemoteException, DeadlockException, InvalidTransactionException; 


	/* reserve an itinerary */
	public boolean itinerary(int id,int customer,Vector flightNumbers,String location, boolean Car, boolean Room)
			throws RemoteException, DeadlockException, InvalidTransactionException; 

	public void cancelNewFlight(String[] parameters) throws RemoteException;

	public void cancelNewCar(String[] parameters) throws RemoteException;

	public void cancelNewRoom(String[] parameters) throws RemoteException;

	public void cancelNewCustomer(String[] parameters) throws RemoteException;

	public void cancelFlightDeletion(String[] parameters) throws RemoteException;

	public void cancelCarDeletion(String[] parameters) throws RemoteException;

	public void cancelRoomDeletion(String[] parameters) throws RemoteException;

	public void cancelCustomerDeletion(String[] parameters) throws RemoteException;

	public void cancelFlightReservation(String[] parameters) throws RemoteException;

	public void cancelCarReservation(String[] parameters) throws RemoteException;

	public void cancelRoomReservation(String[] parameters) throws RemoteException;

	public int start() 
			throws RemoteException;

	public boolean commit(int transactionId) 
			throws RemoteException, TransactionAbortedException, InvalidTransactionException;

	public boolean canCommit(int transactionId) 
			throws RemoteException, TransactionAbortedException, InvalidTransactionException;

	public boolean doCommit(int transactionId) 
			throws RemoteException, TransactionAbortedException, InvalidTransactionException;

	public void abort(int transactionId) 
			throws RemoteException, InvalidTransactionException;
	
	public void doAbort(int transactionId) 
			throws RemoteException, InvalidTransactionException;

	public boolean shutdown() 
			throws RemoteException;
	
	public void neatCrash(int transactionId, int option);

	public void selfDestruct();

}
