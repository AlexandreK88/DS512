package transaction;

import java.util.LinkedList;
import java.util.Stack;

import Server.ResInterface.*;


public class Transaction {
	int id;
	Stack<Operation> writeRequests;
	LinkedList<ResourceManager> rmList;
	
	
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
	
	public void addOp(Operation op) {
		writeRequests.push(op);
	}
	
	public LinkedList<ResourceManager> getRMList() {
		return rmList;
	}

}
