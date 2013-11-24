package transaction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.rmi.RemoteException;
import java.util.LinkedList;

import lockManager.DeadlockException;
import middleware.ReservableItem;
import server.resInterface.InvalidTransactionException;
import server.resInterface.ResourceManager;

public class DiskAccess {

	public static final String PATHING = System.getProperty("user.dir").substring(0,System.getProperty("user.dir").length() - 3);


	private RAFList recordA;
	private RAFList recordB;
	private RAFList masterRec;
	private RAFList workingRec;
	private RandomAccessFile stateLog;

	private int txnMaster;
	String task;
	boolean isRM;


	public DiskAccess() throws IOException {
		isRM = false;
		String location = PATHING + "TM/Transactions.db";
		File file = new File(location);
		boolean readData = !file.createNewFile();
		stateLog = new RandomAccessFile(location, "rwd");
	}
	
	public DiskAccess(ResourceManager rm, String t) throws IOException {
		isRM = true;
		txnMaster = -1;
		task = t;
		String locationA = PATHING + task + "/RecordA.db";
		String locationB = PATHING + task + "/RecordB.db";
		String locationLog = PATHING + task + "/StateLog.db";

		System.out.println(locationA + " is location.");
		System.out.println(PATHING);
		File file = new File(locationA);
		boolean readDataA = !file.createNewFile();
		recordA = new RAFList("A", locationA, "rwd");
		file = new File(locationB);
		boolean readDataB = file.createNewFile();
		recordB = new RAFList("B", locationB, "rwd");
		file = new File(locationLog);
		file.createNewFile();
		stateLog = new RandomAccessFile(locationLog, "rwd");
		recordA.setNext(recordB);
		recordB.setNext(recordA);
		masterRec = getMasterRecord();
		workingRec = masterRec.getNext();
		try {
			if (masterRec == recordA && readDataA) {

				initializeMemory(recordA, rm);

			} else {
				initializeMemory(recordB, rm);
			}	
		} catch (DeadlockException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidTransactionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void logOperation(int id, Operation op) {
		//Add operation on stateLog file
		String operation = id + "," + op.getOpName();
		for (String param: op.getParameters()){
			operation +=  "," + param;
		}
		operation += "\n";
		synchronized(stateLog){
			try{
				stateLog.writeBytes(operation);
				System.out.println("Writing op");
				//write_stateLog.newLine();
			}catch(Exception e){
				System.out.println("Some god damn exception");
			}
		}		
	}

	public void writeToLog(String operation) throws IOException {
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	public RAFList getMasterRecord() {
		// TODO Auto-generated method stub
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
				if(opElements[1].trim().equals("commit")){
					lastCommit = opElements;
				}	
				op = stateLog.readLine();
			}while(op != null);

			if(lastCommit == null){
				System.out.println("Log file is not empty but has no commit, master record is A");
				return recordA;
			}else{
				if(opElements[2].equals("A")){
					System.out.println("Commit found, master record is A");
					return recordA;
				}else{
					System.out.println("Commit found, master record is B");
					return recordB;
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			while (line != null && line != "") {
				line = line.trim();
				String[] lineDetails = line.split(",");
				if(lineDetails[0].startsWith("Room")) {
					try {
						rm.addRooms(0, lineDetails[0].substring(4), Integer.parseInt(lineDetails[1]), Integer.parseInt(lineDetails[2]));
					} catch (DeadlockException | InvalidTransactionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if(lineDetails[0].startsWith("Car")) {
					try {
						rm.addCars(0, lineDetails[0].substring(3), Integer.parseInt(lineDetails[1]), Integer.parseInt(lineDetails[2]));
					} catch (DeadlockException | InvalidTransactionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}				
				} else if(lineDetails[0].startsWith("Flight")) {
					try {
						rm.addFlight(0, Integer.parseInt(lineDetails[0].substring(6)), Integer.parseInt(lineDetails[1]), Integer.parseInt(lineDetails[2]));
					} catch (DeadlockException | InvalidTransactionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				try {
					line = record.getFileAccess().readLine();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			try {
				record.getFileAccess().seek(0);
				line = record.getFileAccess().readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
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
								// TODO Auto-generated catch block
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
								// TODO Auto-generated catch block
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
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

						}
					}
					try {
						line = record.getFileAccess().readLine();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			// TODO Auto-generated catch block
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
						// TODO Auto-generated catch block
						e.printStackTrace();
					}					
				}
			}
		}

		try {
			stateLog.close();
			temp.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		File theLog = new File(locationLog);
		File theTemp = new File(locationTemp);
		theTemp.renameTo(theLog);
		theLog.delete();

	}

}
