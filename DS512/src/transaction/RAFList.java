package transaction;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class RAFList {
	RandomAccessFile cur;
	RAFList next;
	String name;
	
	public static final String SEPARATOR = ",";


	public RAFList(String n, String location, String mode) {
		name = n;
		try {
			cur = new RandomAccessFile(location, mode);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		next = null;
	}

	public void setNext(RAFList n) {
		next = n;
	}

	public RAFList getNext() {
		return next;
	}

	public RandomAccessFile getFileAccess() {
		return cur;
	}

	public String getName() {
		return name;
	}	

	// To do: returns line in database where data is held for 'dataname' object is held.
	public int getLine(String dataName) {
		int i;
		String line = "";
		try {
			cur.seek(0);
			line = cur.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for (i = 0; line != null && !line.equals(""); i++) {
			if (line.split(SEPARATOR)[0].trim().equals(dataName)) {
				return i;
			}
			try {
				line = cur.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return i;
	}
	public void write(String itemToWrite){
		try {
			cur.writeBytes(itemToWrite);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	// Rewrite line in database with information from RMItem
	public void rewriteLine(int line, String itemToRewrite) {
		// set itemToRewrite in readableItemCSVForm
		try {
			cur.seek(new Long(line*TransactionManager.LINE_SIZE));
			cur.writeBytes(itemToRewrite);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void deleteLine(int line) {
		try {
			System.out.println("Deleting line " + line);
			Long lineToRewrite = new Long(line*TransactionManager.LINE_SIZE);
			boolean isLastLine = lineToRewrite == cur.length() - TransactionManager.LINE_SIZE;
			cur.seek(cur.length() - TransactionManager.LINE_SIZE);
			String lastLine = cur.readLine();
			cur.setLength(cur.length() - TransactionManager.LINE_SIZE);
			if (!isLastLine) {
				cur.seek(lineToRewrite);
				cur.writeBytes(lastLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
				
		}
	}
}
