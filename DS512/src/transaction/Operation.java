package transaction;

import java.rmi.RemoteException;

import server.resInterface.ResourceManager;


public class Operation {
 
	private String opName;
	private String[] parameters;
	private ResourceManager rm;
	
	public Operation(String opN, String[] prmtrs, ResourceManager rMan) {
		opName = opN;
		parameters = prmtrs;
		rm = rMan;
	}
	
	public String getOpName(){
		return opName;
	}
	
	public String[] getParameters(){
		return parameters;
	}
	
	public void undoOp() throws RemoteException {
		if (opName.equals("newflight")) {
			rm.cancelNewFlight(parameters);
		} else if (opName.equals("newcar")) {
			rm.cancelNewCar(parameters);
		} else if (opName.equals("newroom")) {
			rm.cancelNewRoom(parameters);
		} else if (opName.equals("newcustomer")) {
			rm.cancelNewCustomer(parameters);
		} else if (opName.equals("deleteflight")) {
			rm.cancelFlightDeletion(parameters);
		} else if (opName.equals("deletecar")) {
			rm.cancelCarDeletion(parameters);
		} else if (opName.equals("deleteroom")) {
			rm.cancelRoomDeletion(parameters);
		} else if (opName.equals("deletecustomer")) {
			rm.cancelCustomerDeletion(parameters);
		} else if (opName.equals("reserveflight")) {
			rm.cancelFlightReservation(parameters);
		} else if (opName.equals("reservecar")) {
			rm.cancelCarReservation(parameters);
		} else if (opName.equals("reserveroom")) {
			rm.cancelRoomReservation(parameters);
		}
	}
	
	//TO DO
	public String getOperationData() {
		return null;
	}

	
	//TO DO
	// Will return all the different data structures modified by the operation.
	public String[] getDataNames() {
		String[] dataNames = null;
		if (opName.equals("newflight") || opName.equals("deleteflight")) {
			dataNames = new String[1];
			dataNames[0] = "Flight"+parameters[0];
		} else if (opName.equals("newcar") || opName.equals("deletecar")) {
			dataNames = new String[1];
			dataNames[0] = "Car"+parameters[0];			
		} else if (opName.equals("newroom") || opName.equals("deleteroom")) {
			dataNames = new String[1];
			dataNames[0] = "Room"+parameters[0];			
		} else if (opName.equals("newcustomer")) {
			dataNames = new String[1];
			dataNames[0] = "Customer"+parameters[0];
		} else if (opName.equals("deletecustomer")) {
			String[] resType = parameters[1].split("::");
			String[] resKey = parameters[2].split("::");
			dataNames = new String[resType.length+1];
			dataNames[0] = "Customer"+parameters[0];
			for (int i = 1; i <= resType.length; i++) {
				dataNames[i] = resType[i-1] + resKey[i-1];
			}
		} else if (opName.equals("reserveflight")) {
			dataNames = new String[2];
			dataNames[0] = "Customer"+parameters[0];
			dataNames[1] = "Flight"+parameters[1].substring(7);
		} else if (opName.equals("reservecar")) {
			dataNames = new String[2];
			dataNames[0] = "Customer"+parameters[0];
			dataNames[1] = "Room"+parameters[1].substring(4);			
		} else if (opName.equals("reserveroom")) {
			dataNames = new String[2];
			dataNames[0] = "Customer"+parameters[0];
			dataNames[1] = "Room"+parameters[1].substring(5);
		}
		return dataNames;
	}
	
}
