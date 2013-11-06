package transaction;

import java.rmi.RemoteException;
import java.util.LinkedList;
import Server.ResInterface.*;

public class TransactionManager {

	public static final int READ_REQUEST = 0;
	public static final int WRITE_REQUEST = 1;
	
	int latestTransaction;
	LinkedList<Transaction> onGoingTransactions;
	
	public TransactionManager() {
		latestTransaction = 0;
	}
	
	public int start() {
		latestTransaction++;
		Transaction t = new Transaction(latestTransaction);
		onGoingTransactions.add(t);
		return latestTransaction;
	}
	
	public boolean enlist(int tid, LinkedList<ResourceManager> rmL) {
		for (Transaction t: onGoingTransactions) {
			if (t.getID() == tid) {
				for (ResourceManager rm: rmL) {
					t.addrm(rm);
				}
				return true;
			}
		}
		
		return false;
	}
	
	public boolean commit(int tid, ResourceManager middleware) {
		for (int i=0; i < onGoingTransactions.size(); i++) {
			if (onGoingTransactions.get(i).getID() == tid) {
				Transaction t = onGoingTransactions.remove(i);
				for (ResourceManager rm: t.getRMList()) {
					if (rm != middleware) {
						try {
							rm.commit(tid);
						} catch (RemoteException | TransactionAbortedException
								| InvalidTransactionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				return true;
			}
		}
		return false;	
	}
	
	public void abort(int tid, ResourceManager middleware) {
		for (int i=0; i < onGoingTransactions.size(); i++) {
			if (onGoingTransactions.get(i).getID() == tid) {
				Transaction t = onGoingTransactions.remove(i);
				for (ResourceManager rm: t.getRMList()) {
					try {
						if (rm != middleware) {
							rm.abort(tid);
						}
					} catch (RemoteException | InvalidTransactionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
				
	}
	
}
