package Middleware;

import java.rmi.RemoteException;
import java.util.LinkedList;


public class TransactionManager {

	public static final int READ_REQUEST = 0;
	public static final int WRITE_REQUEST = 1;
	
	int latestTransaction;
	LinkedList<Transaction> onGoingTransactions;
	
	LinkedList<ResInterface.ResourceManager> rmList;
	
	public TransactionManager(LinkedList<ResInterface.ResourceManager> rmL) {
		latestTransaction = 0;
		rmList = rmL;
	}
	
	public int start() {
		latestTransaction++;
		Transaction t = new Transaction(latestTransaction);
		return latestTransaction;
	}
	
	public boolean addOperation(int tid, String[] request, LinkedList<ResInterface.ResourceManager> rmL) {
		for (Transaction t: onGoingTransactions) {
			if (t.getID() == tid) {
				for (ResInterface.ResourceManager rm: rmL) {
					if (!t.getRMList().contains(rm)) {
						t.addrm(((ResInterface.ResourceManager)rm));
					}
				}
				return true;
			}
		}
		
		return false;
	}
	
	public boolean commit(int tid) {
		for (int i=0; i < onGoingTransactions.size(); i++) {
			if (onGoingTransactions.get(i).getID() == tid) {
				Transaction t = onGoingTransactions.remove(i);
				//for (ResInterface.ResourceManager rm: t.getRMList()) {
				//	rm.commit(tid);
				//}
				return true;
			}
		}
		return false;	
	}
	
	public void abort(int tid) {
		for (int i=0; i < onGoingTransactions.size(); i++) {
			if (onGoingTransactions.get(i).getID() == tid) {
				Transaction t = onGoingTransactions.remove(i);
				//for (ResInterface.ResourceManager rm: t.getRMList()) {
				//	rm.abort(tid);
				//}
			}
		}
				
	}
	
}
