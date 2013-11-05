package Middleware;

import java.util.LinkedList;

import ResInterface.*;

public class TransactionManager {

	public static final int READ_REQUEST = 0;
	public static final int WRITE_REQUEST = 1;
	
	int latestTransaction;
	LinkedList<Transaction> onGoingTransactions;
	
	LinkedList<ResourceManager> rmList;
	
	public TransactionManager(LinkedList<ResourceManager> rmL) {
		latestTransaction = 0;
		rmList = rmL;
	}
	
	public int start() {
		latestTransaction++;
		Transaction t = new Transaction(latestTransaction);
		return latestTransaction;
	}
	
	public boolean addOperation(int tid, String request, LinkedList<ResourceManager> rmL, boolean willWrite) {
		for (Transaction t: onGoingTransactions) {
			if (t.getID() == tid) {
				if (willWrite) {
					t.addWR(request);
				} else {
					for (ResourceManager rm: rmL) {	
						//t.addrm((ResInterface.ResourceManager)rm);
					}
				}
				return true;
			}
		}
		
		return false;
	}
	
}
