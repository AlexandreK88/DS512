package transaction;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import server.resInterface.*;


public class TransactionManager {

	public static final int READ_REQUEST = 0;
	public static final int WRITE_REQUEST = 1;
	public static final int LINE_SIZE = 1000;

	AtomicInteger latestTransaction;
	LinkedList<Transaction> ongoingTransactions;
	DiskAccess stableStorage;

	public TransactionManager() {
		ongoingTransactions = new LinkedList<Transaction>();
		try {
			stableStorage = new DiskAccess();
		} catch (IOException e) {
			e.printStackTrace();
		}
		latestTransaction = new AtomicInteger(stableStorage.getLatestTransaction());
		// Call the read log from the stable storage.
	}

	public int start() {
		Transaction t;
		synchronized(latestTransaction) {
			t = new Transaction(latestTransaction.incrementAndGet());
			synchronized(ongoingTransactions) { 
				ongoingTransactions.add(t);
			}
		}
		// Write to log started TID
		String started = t.getID() + ",StartedTID";
		try {
			stableStorage.writeToLog(started);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		System.out.println("New transaction " + t.getID() + " started.");
		return t.getID();
	}

	public boolean enlist(int tid, LinkedList<ResourceManager> rmL) throws InvalidTransactionException {
		if (tid == 0) {
			return true;
		}
		synchronized(ongoingTransactions) { 
			for (Transaction t: ongoingTransactions) {
				if (t.getID() == tid) {
					for (ResourceManager rm: rmL) {
						if (t.addrm(rm)) {
							try {
								stableStorage.writeToLog(Integer.toString(tid) + ", " + "rm, " + rm.getName() + "\n");
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
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
		// Write to log started TID's vote request
		String prepareStarted = t.getID() + ",Start2PC";
		try {
			stableStorage.writeToLog(prepareStarted);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		for (ResourceManager rm: t.getRMList()) {
			try {
				canCommit = rm.canCommit(t.getID());
				// Write to log started TID's vote request
				String voteResponse = t.getID() + ",vote," + rm.getName() + "," + canCommit;
				try {
					stableStorage.writeToLog(voteResponse);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				// Implement a crash here. Crash after some but not all votes
				if (!canCommit){
					break;
				}
			} catch(RemoteException | TransactionAbortedException
					| InvalidTransactionException e) {
				e.printStackTrace();
			}
		}
		// Crash here. Crash after all votes but no decision.
		return canCommit;
	}

	public boolean commit(int tid, ResourceManager middleware) {
		// Implement a crash here. Crash before sending vote req.
		for (int i=0; i < ongoingTransactions.size(); i++) {
			Transaction t = ongoingTransactions.get(i);
			if (t.getID() == tid) {
				// Or here.
				if(prepare(t)){
					ongoingTransactions.remove(i);
					String voteDecision = t.getID() + ",decision,YES";
					try {
						stableStorage.writeToLog(voteDecision);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					// Craa-aasssh.
					for (ResourceManager rm: t.getRMList()) {
						try {
							rm.doCommit(tid);
						} catch (RemoteException | TransactionAbortedException
								| InvalidTransactionException e) {
							e.printStackTrace();
						}
						// Write to log. Confirm decision sent to rm.
						String rmCommit = t.getID() + ",commitconfirm,"+ rm.getName();
						try {
							stableStorage.writeToLog(rmCommit);
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						// KRRRRRSSSHSHHHSHH. Yes, you read well. CRASH. 
					}
					try {
						// All decisions are sent. Write to log.
						System.out.println("Transaction " + t.getID() + " committed.");
						stableStorage.writeToLog(Integer.toString(tid) + ", Commit\n");
						// Boom! Boom! Boom! Boom! I want you in my crash!...
						// http://www.youtube.com/watch?v=llyiQ4I-mcQ yes, the Vengaboys.
						// It's just another crash.
					} catch (IOException e) {
						e.printStackTrace();
					}
					return true;
				}
				else{
					// Should complete abort here so log can be updated accordingly.
					String voteDecision = t.getID() + ",NOOOOOOO";
					try {
						stableStorage.writeToLog(voteDecision);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					return false;
				}
			}
		}
		return false;	
	}

	// Subject to changes.
	public void abort(int tid, ResourceManager middleware) {
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
							e.printStackTrace();
						}
					}
					try {
						System.out.println("Transaction " + t.getID() + " aborted.");
						stableStorage.writeToLog(Integer.toString(tid) + ", Abort\n");
					} catch (IOException e) {
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
