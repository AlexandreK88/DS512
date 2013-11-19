package transaction;

import java.rmi.RemoteException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import server.resInterface.*;



public class Transaction {
	int id;
	Stack<Operation> writeRequests;
	LinkedList<ResourceManager> rmList;

	private Date date = new Date();


	public Transaction(int i) {
		id = i;
		writeRequests = new Stack<Operation>();
		rmList = new LinkedList<ResourceManager>();
	}

	public int getID() {
		return id;
	}

	public void addrm(ResourceManager rm) {
		if (!rmList.contains(rm)) {
			rmList.add(rm);
		}
	}

	public void undo() throws RemoteException {
		while (!writeRequests.isEmpty()) {
			Operation op = writeRequests.pop();
			op.undoOp();
		}
	}

	public void addOp(Operation op) {
		writeRequests.push(op);
	}

	public LinkedList<ResourceManager> getRMList() {
		return rmList;
	}
	
	public List<Operation> getOperations() {
		return writeRequests;
	}

	public long getTime() {
		synchronized(date){
			return date.getTime();
		}		 
	}

	public void setCurrentTime(){
		synchronized(date){
			date = new Date();
		}		 	
	}


}
