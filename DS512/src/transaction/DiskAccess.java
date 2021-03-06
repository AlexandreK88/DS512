package transaction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.LinkedList;

import coordinatorInterface.Coordinator;
import lockManager.DeadlockException;
import server.resInterface.InvalidTransactionException;
import server.resInterface.ResourceManager;

public class DiskAccess {

	public static final String PATHING = System.getProperty("user.dir").substring(0,System.getProperty("user.dir").length() - 3);


	private RAFList recordA;
	private RAFList recordB;
	private RAFList masterRec;
	private RAFList workingRec;
	private RandomAccessFile stateLog;
	private LinkedList<Transaction> redoneTransactions;

	private int txnMaster;
	String task;
	boolean isRM;

	int coordPort;
	String coordName;


	public DiskAccess() throws IOException {
		isRM = false;
		String location = PATHING + "TM/Transactions.db";
		File file = new File(location);
		boolean readData = !file.createNewFile();
		stateLog = new RandomAccessFile(location, "rwd");

	}

	public DiskAccess(ResourceManager rm, String t, int cp, String cn) throws IOException {
		isRM = true;
		txnMaster = -1;
		task = t;
		String locationA = PATHING + task + "/RecordA.db";
		String locationB = PATHING + task + "/RecordB.db";
		String locationLog = PATHING + task + "/StateLog.db";
		coordPort = cp;
		coordName = cn;
		System.out.println(locationA + " is location.");
		System.out.println(PATHING);
		File file = new File(locationA);
		file.createNewFile();
		recordA = new RAFList("A", locationA, "rwd");
		file = new File(locationB);
		file.createNewFile();
		recordB = new RAFList("B", locationB, "rwd");
		file = new File(locationLog);
		file.createNewFile();
		stateLog = new RandomAccessFile(locationLog, "rwd");
		recordA.setNext(recordB);
		recordB.setNext(recordA);
		masterRec = getMasterRecord();
		workingRec = masterRec.getNext();
	}

	public void memInit(ResourceManager rm) {
		System.out.println("RM memory initialization...");
		try {
			if (masterRec == recordA) {
				initializeMemory(recordA, rm);
			} else {
				initializeMemory(recordB, rm);
			}	
		} catch (DeadlockException e) {
			e.printStackTrace();
		} catch (InvalidTransactionException e) {
			e.printStackTrace();
		}
		System.out.println("RM memory initialization done.");
	}

	public void logOperation(int id, Operation op) {
		//Add operation on stateLog file
		String operation = "";
		if (op == null ) {
			operation = id + ",failed";
		} else {
			operation = id + "," + op.getOpName();
			for (String param: op.getParameters()){
				operation +=  "," + param;
			}
		}
		operation += "\n";
		synchronized(stateLog){
			try{
				stateLog.seek(stateLog.length());
				stateLog.writeBytes(operation);
				System.out.println("Writing op " + operation);
				//write_stateLog.newLine();
			}catch(Exception e){
				e.printStackTrace();
			}
		}		
	}

	public void writeToLog(String operation) throws IOException {
		stateLog.seek(stateLog.length());
		stateLog.writeBytes(operation);
	}

	public int getLatestTransaction() {
		try {
			stateLog.seek(0);
			String op = "";
			String[] opElements;
			int value = 0;
			op = stateLog.readLine();
			if (op == null) {
				System.out.println("Log file is empty, latest transaction is 0.");
				return value;
			}
			do {
				opElements = op.split(",");
				if(Integer.parseInt(opElements[0].trim()) > value){
					value = Integer.parseInt(opElements[0].trim());
				}	
				op = stateLog.readLine();
			} while(op != null);
			return value;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	public RAFList getMasterRecord() {
		try {
			stateLog.seek(0);
			String op = "";
			String[] opElements;
			String[] lastCommit = null;
			op = stateLog.readLine();
			if(op == null){
				System.out.println("Log file is empty, master record is A");
				return recordA;
			}
			do{
				opElements = op.split(",");
				if(opElements[1].trim().equalsIgnoreCase("commit")){
					lastCommit = opElements;
				}	
				op = stateLog.readLine();
			}while(op != null);

			if(lastCommit == null){
				System.out.println("Log file is not empty but has no commit, master record is A");
				return recordA;
			}else{
				if(lastCommit[2].equalsIgnoreCase("A")){
					System.out.println("Commit found, master record is A");
					// Needs to rewrite record B, as it could have been corrupted on a crash.
					return recordA;
				}else{
					System.out.println("Commit found, master record is B");
					// Needs to rewrite record A, as it could have been corrupted on a crash.
					return recordB;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	//Master points to the one which is currently the latest committed version, linked to transactionID
	public void masterSwitch(int transactionID){
		if (!isRM) {
			return;
		}
		String operation = transactionID + ",commit," + workingRec.getName() + "\n";
		workingRec = workingRec.getNext();
		masterRec = masterRec.getNext();
		txnMaster = transactionID;
		synchronized(stateLog){
			try{
				stateLog.writeBytes(operation);
			}catch(Exception e){
				System.out.println("Problem trying to modify stateLog file on switchMaster on commit of transaction with ID : " + transactionID);
				System.out.println(e);
			}
		}				
	}

	private void initializeMemory(RAFList record, ResourceManager rm) throws DeadlockException, InvalidTransactionException {
		if (!isRM) {
			return;
		}
		try {
			String line = "";
			try {
				line = record.getFileAccess().readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			while (line != null && line != "") {

				line = line.trim();
				String[] lineDetails = line.split(",");
				if(lineDetails[0].startsWith("Room")) {
					try {
						rm.addRooms(0, lineDetails[0].substring(4), Integer.parseInt(lineDetails[1]), Integer.parseInt(lineDetails[2]));
					} catch (DeadlockException | InvalidTransactionException e) {
						e.printStackTrace();
					}
				} else if(lineDetails[0].startsWith("Car")) {
					try {
						rm.addCars(0, lineDetails[0].substring(3), Integer.parseInt(lineDetails[1]), Integer.parseInt(lineDetails[2]));
					} catch (DeadlockException | InvalidTransactionException e) {
						e.printStackTrace();
					}				
				} else if(lineDetails[0].startsWith("Flight")) {
					try {
						rm.addFlight(0, Integer.parseInt(lineDetails[0].substring(6)), Integer.parseInt(lineDetails[1]), Integer.parseInt(lineDetails[2]));
					} catch (DeadlockException | InvalidTransactionException e) {
						e.printStackTrace();
					}
				}
				try {
					line = record.getFileAccess().readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			try {
				record.getFileAccess().seek(0);
				line = record.getFileAccess().readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}

			while (line != null && line != "") {
				line = line.trim();
				String[] lineDetails = line.split(",");
				if(lineDetails[0].startsWith("Cust")) {
					for (String reservation: lineDetails) {
						if (reservation == lineDetails[0]) {
							rm.newCustomer(0, Integer.parseInt(lineDetails[0].substring(8)));
							continue;
						}
						String[] details = reservation.split(" ");
						if (details[1].startsWith("flight")) {
							try {
								rm.addFlight(0, Integer.parseInt(details[1].substring(7)), Integer.parseInt(details[0]), Integer.parseInt(details[2].substring(1)));
								for (int i = 0; i < Integer.parseInt(details[0]); i++) {
									rm.reserveFlight(0, Integer.parseInt(lineDetails[0].substring(8)), Integer.parseInt(details[1].substring(7)));
								}
							} catch (DeadlockException
									| InvalidTransactionException e) {
								e.printStackTrace();
							}						
						} else if (details[1].startsWith("car")) {
							try {
								rm.addCars(0, details[1].substring(4), Integer.parseInt(details[0]), Integer.parseInt(details[2].substring(1)));
								for (int i = 0; i < Integer.parseInt(details[0]); i++) {
									rm.reserveCar(0, Integer.parseInt(lineDetails[0].substring(8)), details[1].substring(4));
								}
							} catch (DeadlockException
									| InvalidTransactionException e) {
								e.printStackTrace();
							}						
						} else if (details[1].startsWith("room")) {
							try {
								rm.addRooms(0, details[1].substring(5), Integer.parseInt(details[0]), Integer.parseInt(details[2].substring(1)));
								for (int i = 0; i < Integer.parseInt(details[0]); i++) {
									rm.reserveRoom(0, Integer.parseInt(lineDetails[0].substring(8)), details[1].substring(5));
								}
							} catch (DeadlockException
									| InvalidTransactionException e) {
								e.printStackTrace();
							}

						}
					}
				}
				try {
					line = record.getFileAccess().readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public LinkedList<Transaction> readLog(){
		System.out.println("TM reading stored log...");
		LinkedList<Transaction> ongoings = new LinkedList<Transaction>();
		LinkedList<Integer> completed = new LinkedList<Integer>();
		String line = "";
		try {
			stateLog.seek(0);
			line = stateLog.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (line != null && line != "") {
			line = line.trim();
			String[] lineDetails = line.split(",");
			if(lineDetails[1].trim().equalsIgnoreCase("commit") || lineDetails[1].trim().equalsIgnoreCase("abort")) {
				completed.add(Integer.parseInt(lineDetails[0]));
			}
			try {
				line = stateLog.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			stateLog.seek(0);
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		try {
			line = stateLog.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (line != null && line != "") {
			line = line.trim();
			String[] lineDetails = line.split(",");
			if(!completed.contains(Integer.parseInt(lineDetails[0]))){
				if (lineDetails[1].trim().equalsIgnoreCase("startedtid")) {
					System.out.println("Transaction " + lineDetails[0] + " was ongoing.");
					ongoings.add(new Transaction(Integer.parseInt(lineDetails[0])));
				} else {
					for (Transaction t: ongoings) {
						if (t.getID() == Integer.parseInt(lineDetails[0])) {
							t.addLogLine(line);
							break;
						}
					}
				}
			}
			try {
				line = stateLog.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return ongoings;
	}

	public	LinkedList<Integer> readLog(ResourceManager rm){
		if(!isRM){
			return null;
		}else{
			LinkedList<Integer> done = new LinkedList<Integer>();
			LinkedList<Integer> redo = new LinkedList<Integer>();
			LinkedList<Integer> pendings = new LinkedList<Integer>();
			redoneTransactions = new LinkedList<Transaction>();
			try {
				String line = "";
				stateLog.seek(0);
				try {
					line = stateLog.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
				while (line != null && line != "") {
					line = line.trim();
					String[] lineDetails = line.split(",");
					if(lineDetails[1].trim().equalsIgnoreCase("commit") || lineDetails[1].trim().equalsIgnoreCase("abort")){
						done.add(Integer.parseInt(lineDetails[0]));
					}else if(lineDetails[1].trim().equalsIgnoreCase("canCommit")){
						if(lineDetails[2].trim().equalsIgnoreCase("YES")){
							redo.add(Integer.parseInt(lineDetails[0]));
						}else{
							done.add(Integer.parseInt(lineDetails[0]));
						}
					}
					try {
						line = stateLog.readLine();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				redo.removeAll(done);
				stateLog.seek(0);

				try {
					line = stateLog.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
				Coordinator coord = connectToCoordinator();

				while (line != null && line != "") {
					line = line.trim();
					String[] lineDetails = line.split(",");
					Integer id = Integer.parseInt(lineDetails[0]);
					if(redo.contains(id)){
						if(coord.transactionCommitted(id)) {
							String[] params = new String[lineDetails.length-2];
							for(int i = 0; i < lineDetails.length - 2; i++){
								params[i] = lineDetails[i+2].trim();
							}
							Operation op = new Operation(lineDetails[1], params, rm);
							try {
								if (op.doOp(0)) {
									boolean opAdded = false;
									for (Transaction t: redoneTransactions) {
										if (t.getID() == id.intValue()) {
											t.addOp(op);
											opAdded = true;
											break;
										}
									}
									if (!opAdded) {
										Transaction t = new Transaction(id);
										t.addOp(op);
										redoneTransactions.add(t);
									}
								}
								System.out.println("Transaction executed with id > 0.");
							} catch (InvalidTransactionException e) {
								System.out.println("Ok, An aborted transaction was attempted.");
								rm.abort(Integer.parseInt(lineDetails[0].trim()));
							}
						} else {
							redo.remove(id);
							long currentLocation = stateLog.getFilePointer();
							stateLog.seek(stateLog.length());
							try{
								String operation = id + ", abort\n";
								writeToLog(operation);
								System.out.println("Writing op");
							}catch(Exception e){
								System.out.println("Some god damn exception");
							}
							stateLog.seek(currentLocation);
						}
					}else if(!done.contains(id)){
						if(!pendings.contains(id)){
							pendings.add(id);
						}
					}

					try {
						line = stateLog.readLine();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

			}catch(Exception e){
				e.printStackTrace();
			}
			System.out.print("Pending list: "); 
			for (Integer integ: pendings) {
				System.out.print(integ + ", ");
			}
			System.out.println("");
			System.out.print("Ongoing txns list: "); 
			for (Transaction t: redoneTransactions) {
				System.out.print(t.getID() + ", ");
			}
			System.out.println("");
			return pendings;
		}		
	}

	public void updateData(String dataName, String updatedLine) {
		if (!isRM) {
			return;
		}
		int line = workingRec.getLine(dataName);
		System.out.println("Line is: " + line);
		workingRec.rewriteLine(line, updatedLine);
	}

	public void clearLog(LinkedList<Transaction> ongoingTransactions){
		/* 
		 *Generate a new file called temp.
		 *In temp, write all transactions down.
		 *Close and delete current log.
		 *change name of temp to log.
		 *If crashes and no log file to be found, search for temp file.
		 */

		String locationLog = PATHING + task + "/stateLog.db";
		String locationTemp = PATHING + task + "/temp.db";
		RandomAccessFile temp = null;
		try {
			temp = new RandomAccessFile(locationTemp, "rwd");
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

		String operation = "";
		synchronized(ongoingTransactions){
			for (Transaction t: ongoingTransactions){				
				for(Operation op: t.getOperations()){
					operation = t.getID() + "," + op.getOpName() + ",";
					for(String p: op.getParameters()){
						operation += p + ",";
					}
					try {
						temp.writeBytes(operation);
					} catch (IOException e) {
						e.printStackTrace();
					}					
				}
			}
		}

		try {
			stateLog.close();
			temp.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		File theLog = new File(locationLog);
		File theTemp = new File(locationTemp);
		theTemp.renameTo(theLog);
		theLog.delete();
	}

	private void resetWorking(){
		try {
			masterRec.getFileAccess().seek(0);
			workingRec.getFileAccess().seek(0);

			String line = masterRec.getFileAccess().readLine();
			while(line != null && line != ""){
				workingRec.getFileAccess().writeBytes(line);
				line = masterRec.getFileAccess().readLine();
			}
			workingRec.getFileAccess().setLength(masterRec.getFileAccess().length());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void deleteData(String dataName) {
		if (!isRM) {
			return;
		}
		int line = workingRec.getLine(dataName);
		System.out.println("Line is: " + line);
		workingRec.deleteLine(line);
	}

	public LinkedList<Transaction> getRedoneTransactions() {
		return redoneTransactions;
	}

	private Coordinator connectToCoordinator() {
		Coordinator coord = null;
		boolean connectionFailed = true;
		while (connectionFailed) {
			try {

				// get a reference to the rmiregistry
				Registry registry = LocateRegistry.getRegistry(coordName, coordPort);

				// This should be put in a function so that upon crash, an RM can be looked up again. 
				// get the proxy and the remote reference by rmiregistry lookup
				coord = (Coordinator) registry.lookup("Coord21");
				if(coord != null)
				{
					System.out.println("Successful");
					System.out.println("Connected to coordinator");
					connectionFailed = false;
				}
				else{
					System.out.println("Unsuccessful");
				}//if
			} catch (Exception e) {
				System.out.println("Could not connect to coordinator. Retrying...");
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
		return coord;
	}

	public boolean transactionCommitted(int id) {
		try {
			String line = "";
			stateLog.seek(0);
			try {
				line = stateLog.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			while (line != null && line != "") {
				line = line.trim();
				String[] lineDetails = line.split(",");
				if (Integer.parseInt(lineDetails[0].trim()) == id) {
					System.out.println(line + ": is in log");
					if (lineDetails[1].trim().equalsIgnoreCase("commit")) {
						System.out.println(" committed ");
						return true;
					}
					if (lineDetails[1].trim().equalsIgnoreCase("abort")) {
						System.out.println(" aborted ");
						return false;
					}					
				}
				try {
					line = stateLog.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}

}
