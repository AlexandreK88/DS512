package transaction;

import Server.ResInterface.ResourceManager;

public class Operation {
 
	private String opName;
	private String[] parameters;
	private ResourceManager rm;
	
	public Operation(String opN, String[] prmtrs, ResourceManager rMan) {
		opName = opN;
		parameters = prmtrs;
		rm = rMan;
	}
	
	public void undoOp() {
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
	
}
