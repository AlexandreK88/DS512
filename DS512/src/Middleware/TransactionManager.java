package Middleware;

import java.rmi.RemoteException;
import java.util.LinkedList;

import Server.ResInterface.*;

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
	
	public boolean addOperation(int tid, String[] request, LinkedList<ResourceManager> rmL) {
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
	
	public boolean commit(int tid) {
		for (int i=0; i < onGoingTransactions.size(); i++) {
			if (onGoingTransactions.get(i).getID() == tid) {
				Transaction t = onGoingTransactions.remove(i);
				for (ResourceManager rm: t.getRMList()) {
					try {
						rm.commit(tid);
					} catch (RemoteException | TransactionAbortedException
							| InvalidTransactionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				return true;
			}
		}
		return false;	
	}
	
	public void abort(int tid) {
		for (int i=0; i < onGoingTransactions.size(); i++) {
			if (onGoingTransactions.get(i).getID() == tid) {
				Transaction t = onGoingTransactions.remove(i);
				for (ResourceManager rm: t.getRMList()) {
					try {
						rm.abort(tid);
					} catch (RemoteException | InvalidTransactionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
				
	}
	
}
