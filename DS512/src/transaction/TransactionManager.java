package transaction;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import middleware.MiddleWare;

import server.resInterface.*;


public class TransactionManager {

	public static final int READ_REQUEST = 0;
	public static final int WRITE_REQUEST = 1;
	public static final int LINE_SIZE = 1000;

	private boolean crashBeforeSendingRequest;
	private boolean crashAfterSendingRequest;
	private boolean crashAfterSomeReplies;
	private boolean crashAfterAllReplies;
	private boolean crashAfterDeciding;
	private boolean crashAfterSendingSomeDecisions;
	private boolean crashAfterSendingAllDecisions;
	private int transactionToCrash;

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

		crashBeforeSendingRequest = false;
		crashAfterSendingRequest = false;
		crashAfterSomeReplies = false;
		crashAfterAllReplies = false;
		crashAfterDeciding = false;
		crashAfterSendingSomeDecisions = false;
		crashAfterSendingAllDecisions = false;
		transactionToCrash = 0;
	}

	public void initializeTMFromDisk(ResourceManager flight, ResourceManager car, ResourceManager room, MiddleWare mw) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		ongoingTransactions = stableStorage.readLog();
		LinkedList<Transaction> toBeRemoved = new LinkedList<Transaction>();
		while (!ongoingTransactions.isEmpty()) {
			Transaction t = ongoingTransactions.getFirst();
			LinkedList<String> logLines = t.getLogLines();
			boolean started2PC = false;
			boolean abortVoteReceived = false;
			boolean decided = false;
			boolean decision = false;
			LinkedList<ResourceManager> votesReceived = new LinkedList<ResourceManager>();
			LinkedList<ResourceManager> decisionConfirmed = new LinkedList<ResourceManager>();
			for (String line: logLines) {
				String[] lineDetails = line.split(",");
				if (lineDetails[1].trim().equalsIgnoreCase("rm")) {
					if (lineDetails[2].trim().equalsIgnoreCase(flight.getName())) {
						t.addrm(flight);
					} else if (lineDetails[2].trim().equalsIgnoreCase(car.getName())) {
						t.addrm(car);
					} else if (lineDetails[2].trim().equalsIgnoreCase(room.getName())) {
						t.addrm(room);
					} else if (lineDetails[2].trim().equalsIgnoreCase(mw.getName())) {
						t.addrm(mw);
					}
				} else if (lineDetails[1].trim().equalsIgnoreCase("Start2PC")) {
					started2PC = true;
				} else if (lineDetails[1].trim().equalsIgnoreCase("vote")) {
					if (lineDetails[2].trim().equalsIgnoreCase(flight.getName())) {
						votesReceived.add(flight);
					} else if (lineDetails[2].trim().equalsIgnoreCase(car.getName())) {
						votesReceived.add(car);
					} else if (lineDetails[2].trim().equalsIgnoreCase(room.getName())) {
						votesReceived.add(room);
					} else if (lineDetails[2].trim().equalsIgnoreCase(mw.getName())) {
						votesReceived.add(mw);
					}
					if (lineDetails[3].trim().equalsIgnoreCase("false")) {
						// There is one abort received.
						abortVoteReceived = true;
					}
				} else if (lineDetails[1].trim().equalsIgnoreCase("decision")) {
					decided = true;
					if (lineDetails[2].trim().equalsIgnoreCase("YES")) {
						decision = true;
					} else {
						decision = false;
					}
				} else if (lineDetails[1].trim().equalsIgnoreCase("commitconfirm")
						|| lineDetails[1].trim().equalsIgnoreCase("abortconfirm")) {
					if (lineDetails[2].trim().equalsIgnoreCase(flight.getName())) {
						decisionConfirmed.add(flight);
					} else if (lineDetails[2].trim().equalsIgnoreCase(car.getName())) {
						decisionConfirmed.add(car);
					} else if (lineDetails[2].trim().equalsIgnoreCase(room.getName())) {
						decisionConfirmed.add(room);
					} else if (lineDetails[2].trim().equalsIgnoreCase(mw.getName())) {
						decisionConfirmed.add(mw);
					}					
				}
			}
			// If the 2PC didn't start, transaction can be aborted.
			if (!started2PC) {
				abort(t.getID());
			} else if (votesReceived.isEmpty()) {
				// No votes received, but the vote started
				// Resend votes. Assumed it doesn't matter if start2PC is logged twice.
				commit(t.getID(), mw);
			} else if (!decided) {
				// Votes received, but not all.
				// Abort if abort vote received, simply keep on asking votes otherwise.
				if (abortVoteReceived) {
					abort(t.getID());
				} else {
					boolean canCommit = true;
					for (ResourceManager rm: t.getRMList()) {
						if (!votesReceived.contains(rm)) {
							try {
								canCommit = rm.canCommit(t.getID());
								String voteResponse = t.getID() + ",vote," + rm.getName() + "," + canCommit + "\n";	
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
					}
					if (canCommit) {
						doCommit(t, mw);
					} else{
						abort(t.getID());
						// Should complete abort here so log can be updated accordingly.
						String voteDecision = t.getID() + ",NOOOOOOO\n";
						try {
							stableStorage.writeToLog(voteDecision);
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}
			} else if (decisionConfirmed.isEmpty()) {
				// No decisions sent.
				if (decision) {
					// commit
					doCommit(t, mw);
				} else {
					// abort
					abort(t.getID());
				}
			} else {
				// Some decisions received, but not all.
				if (decision) {
					// commit
					toBeRemoved.add(t);
					// Craa-aasssh.
					for (ResourceManager rm: t.getRMList()) {
						if (decisionConfirmed.contains(rm)) {
							continue;
						}
						try {
							rm.doCommit(t.getID());
						} catch (RemoteException | TransactionAbortedException
								| InvalidTransactionException e) {
							e.printStackTrace();
						}
						// Write to log. Confirm decision sent to rm.
						String rmCommit = t.getID() + ",commitconfirm,"+ rm.getName() + "\n";
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
						stableStorage.writeToLog(Integer.toString(t.getID()) + ", Commit\n");
						// Boom! Boom! Boom! Boom! I want you in my crash!...
						// http://www.youtube.com/watch?v=llyiQ4I-mcQ yes, the Vengaboys.
						// It's just another crash.
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					// abort
					for (ResourceManager rm: t.getRMList()) {
						if (decisionConfirmed.contains(rm)) {
							continue;
						}
						try {
							rm.doAbort(t.getID());
						} catch (RemoteException | InvalidTransactionException e) {
							e.printStackTrace();
						}
						// Write to log. Confirm decision sent to rm.
						String rmAbort = t.getID() + ",abortconfirm,"+ rm.getName() + "\n";
						try {
							stableStorage.writeToLog(rmAbort);
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
					try {
						System.out.println("Transaction " + t.getID() + " aborted.");
						stableStorage.writeToLog(Integer.toString(t.getID()) + ", Abort\n");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

		}
		ongoingTransactions.removeAll(toBeRemoved);
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
		String started = t.getID() + ",StartedTID\n";
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

	public boolean prepare(Transaction t) throws RemoteException, 
	TransactionAbortedException, InvalidTransactionException{
		boolean canCommit = true;
		// Write to log started TID's vote request
		String prepareStarted = t.getID() + ",Start2PC\n";
		try {
			stableStorage.writeToLog(prepareStarted);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		for (ResourceManager rm: t.getRMList()) {
			canCommit = rm.canCommit(t.getID());
			String voteResponse = t.getID() + ",vote," + rm.getName() + "," + canCommit + "\n";
			try {
				stableStorage.writeToLog(voteResponse);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			// Implement a crash here. Crash after some but not all votes
			if (!canCommit){
				break;
			}
		}
		// Crash here. Crash after all votes but no decision.
		return canCommit;
	}

	public boolean commit(int tid, MiddleWare mw) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		// Implement a crash here. Crash before sending vote req.
		for (int i=0; i < ongoingTransactions.size(); i++) {
			Transaction t = ongoingTransactions.get(i);
			if (t.getID() == tid) {
				// Or here.
				if(prepare(t)){
					doCommit(t, mw);
					return true;
				} else{
					String voteDecision = t.getID() + ",NOOOOOOO\n";
					try {
						stableStorage.writeToLog(voteDecision);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					abort(t.getID());
					return false;
				}
			}
		}
		return false;	
	}

	private void doCommit(Transaction t, MiddleWare mw) throws RemoteException {
		ongoingTransactions.remove(t);
		String voteDecision = t.getID() + ",decision,YES\n";
		try {
			stableStorage.writeToLog(voteDecision);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (t.getID() == transactionToCrash && crashAfterDeciding) {
			mw.selfDestruct();
		}
		for (ResourceManager rm: t.getRMList()) {
			attemptIndividualCommit(rm, mw, t);
			// Write to log. Confirm decision sent to rm.
			String rmCommit = t.getID() + ",commitconfirm,"+ rm.getName() + "\n";
			try {
				stableStorage.writeToLog(rmCommit);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			if (t.getID() == transactionToCrash && this.crashAfterSendingSomeDecisions) {
				mw.selfDestruct();
			}
		}
		try {
			// All decisions are sent. Write to log.
			System.out.println("Transaction " + t.getID() + " committed.");
			stableStorage.writeToLog(Integer.toString(t.getID()) + ", Commit\n");
			if (t.getID() == transactionToCrash && this.crashAfterSendingAllDecisions) {
				mw.selfDestruct();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void attemptIndividualCommit(ResourceManager rm, MiddleWare mw,
			Transaction t) {
		try {
			rm.doCommit(t.getID());
		} catch (RemoteException e) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			mw.reconnect("flight");
			mw.reconnect("car");
			mw.reconnect("room");
			attemptIndividualCommit(rm, mw, t);
		} catch (TransactionAbortedException | InvalidTransactionException e) {
			e.printStackTrace();
		}
	}

	public void abort(int tid) throws RemoteException {
		synchronized(ongoingTransactions) { 
			for (int i=0; i < ongoingTransactions.size(); i++) {
				if (ongoingTransactions.get(i).getID() == tid) {
					Transaction t = ongoingTransactions.remove(i);
					for (ResourceManager rm: t.getRMList()) {
						try {
							rm.doAbort(tid);
						} catch (RemoteException | InvalidTransactionException e) {
							e.printStackTrace();
						}
						// Write to log. Confirm decision sent to rm.
						String rmAbort = t.getID() + ",abortconfirm,"+ rm.getName() + "\n";
						try {
							stableStorage.writeToLog(rmAbort);
						} catch (IOException e1) {
							e1.printStackTrace();
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

	public void neatCrash(int transactionId, int option){
		crashBeforeSendingRequest = false;
		crashAfterSendingRequest = false;
		crashAfterSomeReplies = false;
		crashAfterAllReplies = false;
		crashAfterDeciding = false;
		crashAfterSendingSomeDecisions = false;
		crashAfterSendingAllDecisions = false;

		transactionToCrash = transactionId;

		switch(option){
		case 1:
			crashBeforeSendingRequest = true;
			break;
		case 2:
			crashAfterSendingRequest = true;
			break;
		case 3:
			crashAfterSomeReplies = true;
			break;
		case 4:
			crashAfterAllReplies = true;
			break;
		case 5: 
			crashAfterDeciding = true;
			break;
		case 6: 
			crashAfterSendingSomeDecisions = true;
			break;
		case 7:
			crashAfterSendingAllDecisions = true;
			break;
		default:
			System.out.println("What are you talking about, this option is not an option.");
		}

	}

}
