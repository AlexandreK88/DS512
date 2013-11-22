package transaction;

import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import server.resInterface.*;

import middleware.MiddleWare;


public class TransactionManager {

	public static final int READ_REQUEST = 0;
	public static final int WRITE_REQUEST = 1;
	public static final int LINE_SIZE = 1000;

	AtomicInteger latestTransaction;
	LinkedList<Transaction> ongoingTransactions;
	// Add transaction log.

	public TransactionManager() {
		latestTransaction = new AtomicInteger();
		ongoingTransactions = new LinkedList<Transaction>();
	}

	public int start() {
		Transaction t;
		synchronized(latestTransaction) {
			t = new Transaction(latestTransaction.incrementAndGet());
			System.out.println("Lock of TM's ongoingtransactions attempted"); 
			synchronized(ongoingTransactions) { 
				ongoingTransactions.add(t);
			}
			System.out.println("Lock of TM's ongoingtransactions successful!");
		}
		return t.getID();
	}

	public boolean enlist(int tid, LinkedList<ResourceManager> rmL) throws InvalidTransactionException {
		synchronized(ongoingTransactions) { 
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == tid) {
					for (ResourceManager rm: rmL) {
						t.addrm(rm);
						t.setCurrentTime();
					}
					return true;
				}
			}
		}
		if (tid == -1) {
			throw new InvalidTransactionException(tid, "The transaction was removed from transaction list (hence shows as -1).");
		} else {
			throw new InvalidTransactionException(tid, "The transaction ID was NOT FOUND.");
		}
	}

	public boolean prepare(Transaction t) /*throws RemoteException, 
	TransactionAbortedException, InvalidTransactionException*/{
		boolean canCommit = true;			
		for (ResourceManager rm: t.getRMList()) {
			try {
				canCommit = rm.canCommit(t.getID());
				if (!canCommit){
					break;
				}
			} catch(RemoteException | TransactionAbortedException
					| InvalidTransactionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return canCommit;
	}

	public boolean commit(int tid, ResourceManager middleware) {
		for (int i=0; i < ongoingTransactions.size(); i++) {
			Transaction t = ongoingTransactions.get(i);
			if (t.getID() == tid) {
				if(prepare(t)){
					for (ResourceManager rm: t.getRMList()) {
						ongoingTransactions.remove(i);
						try {
							rm.doCommit(tid);
						} catch (RemoteException | TransactionAbortedException
								| InvalidTransactionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					return true;
				}
				else{
					return false;
				}
			}
		}
		return false;	
	}

	public void abort(int tid, ResourceManager middleware) {
		System.out.println("Lock of TM's ongoingtransactions attempted"); 
		synchronized(ongoingTransactions) { 
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
		System.out.println("Lock of TM's ongoingtransactions successful!");
	}
	public LinkedList<Transaction> getOngoingTransactions(){
		return ongoingTransactions;
	}
	public boolean hasOngoingTransactions(){
		return !ongoingTransactions.isEmpty();
	}

}
