package transaction;

import java.rmi.RemoteException;
import java.util.LinkedList;
import Server.ResInterface.*;

public class TransactionManager {

	public static final int READ_REQUEST = 0;
	public static final int WRITE_REQUEST = 1;
	
	int latestTransaction;
	LinkedList<Transaction> ongoingTransactions;
	
	public TransactionManager() {
		latestTransaction = 0;
		ongoingTransactions = new LinkedList<Transaction>();
	}
	
	public int start() {
		latestTransaction++;
		Transaction t = new Transaction(latestTransaction);
		ongoingTransactions.add(t);
		return latestTransaction;
	}
	
	public boolean enlist(int tid, LinkedList<ResourceManager> rmL) {
		for (Transaction t: ongoingTransactions) {
			if (t.getID() == tid) {
				for (ResourceManager rm: rmL) {
					t.addrm(rm);
					t.setCurrentTime();
				}
				return true;
			}
		}		
		return false;
	}
	
	public boolean commit(int tid, ResourceManager middleware) {
		for (int i=0; i < ongoingTransactions.size(); i++) {
			if (ongoingTransactions.get(i).getID() == tid) {
				Transaction t = ongoingTransactions.remove(i);
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
		for (int i=0; i < ongoingTransactions.size(); i++) {
			if (ongoingTransactions.get(i).getID() == tid) {
				Transaction t = ongoingTransactions.remove(i);
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
	public LinkedList<Transaction> getOngoingTransactions(){
		return ongoingTransactions;
	}
	public boolean hasOngoingTransactions(){
		return !ongoingTransactions.isEmpty();
	}
	
}
