package transaction;

import java.rmi.RemoteException;

import lockManager.DeadlockException;

import server.resInterface.InvalidTransactionException;
import server.resInterface.ResourceManager;


public class Operation {

	private String opName;
	private String[] params;
	private ResourceManager rm;

	public Operation(String opN, String[] prmtrs, ResourceManager rMan) {
		opName = opN;
		params = prmtrs;
		rm = rMan;
	}

	public String getOpName(){
		return opName;
	}

	public String[] getParameters(){
		return params;
	}

	public void doOp(int id) throws RemoteException, InvalidTransactionException {
		try{
			if (opName.equals("newflight")) {
				rm.addFlight(id, Integer.parseInt(params[0]), Integer.parseInt(params[1]), Integer.parseInt(params[2]));
			} else if (opName.equals("newcar")) {
				rm.addCars(id, params[0], Integer.parseInt(params[1]), Integer.parseInt(params[2]));
			} else if (opName.equals("newroom")) {
				rm.addRooms(id, params[0], Integer.parseInt(params[1]), Integer.parseInt(params[2]));
			} else if (opName.equals("newcustomer")) {
				rm.newCustomer(id, Integer.parseInt(params[0]));
			} else if (opName.equals("deleteflight")) {
				rm.deleteFlight(id, Integer.parseInt(params[0]));
			} else if (opName.equals("deletecar")) {
				rm.deleteCars(id, params[1]);
			} else if (opName.equals("deleteroom")) {
				rm.deleteRooms(id, params[0]);
			} else if (opName.equals("deletecustomer")) {
				rm.deleteCustomer(id, Integer.parseInt(params[0]));
			} else if (opName.equals("reserveflight")) {
				rm.reserveFlight(id, Integer.parseInt(params[0]), Integer.parseInt(params[1]));
			} else if (opName.equals("reservecar")) {
				rm.reserveCar(id, Integer.parseInt(params[0]),params[1]);
			} else if (opName.equals("reserveroom")) {
				rm.reserveRoom(id, Integer.parseInt(params[0]),params[1]);
			}
		}
		catch (InvalidTransactionException e) {
			e.printStackTrace();
			throw e;
		}
		catch (DeadlockException e) {
			e.printStackTrace();
		}
	}

	public void undoOp() throws RemoteException {
		if (opName.equals("newflight")) {
			rm.cancelNewFlight(params);
		} else if (opName.equals("newcar")) {
			rm.cancelNewCar(params);
		} else if (opName.equals("newroom")) {
			rm.cancelNewRoom(params);
		} else if (opName.equals("newcustomer")) {
			rm.cancelNewCustomer(params);
		} else if (opName.equals("deleteflight")) {
			rm.cancelFlightDeletion(params);
		} else if (opName.equals("deletecar")) {
			rm.cancelCarDeletion(params);
		} else if (opName.equals("deleteroom")) {
			rm.cancelRoomDeletion(params);
		} else if (opName.equals("deletecustomer")) {
			rm.cancelCustomerDeletion(params);
		} else if (opName.equals("reserveflight")) {
			rm.cancelFlightReservation(params);
		} else if (opName.equals("reservecar")) {
			rm.cancelCarReservation(params);
		} else if (opName.equals("reserveroom")) {
			rm.cancelRoomReservation(params);
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
			dataNames[0] = "Flight"+params[0];
		} else if (opName.equals("newcar") || opName.equals("deletecar")) {
			dataNames = new String[1];
			dataNames[0] = "Car"+params[0];			
		} else if (opName.equals("newroom") || opName.equals("deleteroom")) {
			dataNames = new String[1];
			dataNames[0] = "Room"+params[0];			
		} else if (opName.equals("newcustomer")) {
			dataNames = new String[1];
			dataNames[0] = "Customer"+params[0];
		} else if (opName.equals("deletecustomer")) {
			String[] resType = params[1].split("::");
			String[] resKey = params[2].split("::");
			if (resType.length == 1 && resType[0].length() == 0) {
				dataNames = new String[1];
				dataNames[0] = "Customer"+params[0];
			} else {
				dataNames = new String[resType.length+1];
				dataNames[0] = "Customer"+params[0];
				for (int i = 1; i <= resType.length; i++) {
					dataNames[i] = resType[i-1] + resKey[i-1];
				}
			}
		} else if (opName.equals("reserveflight")) {
			dataNames = new String[2];
			dataNames[0] = "Customer"+params[0];
			dataNames[1] = "Flight"+params[1].substring(7);
		} else if (opName.equals("reservecar")) {
			dataNames = new String[2];
			dataNames[0] = "Customer"+params[0];
			dataNames[1] = "Car"+params[1].substring(4);			
		} else if (opName.equals("reserveroom")) {
			dataNames = new String[2];
			dataNames[0] = "Customer"+params[0];
			dataNames[1] = "Room"+params[1].substring(5);
		}
		return dataNames;
	}

}
