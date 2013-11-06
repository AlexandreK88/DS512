package Middleware;

import java.util.LinkedList;
import ResInterface.*;

public class Transaction {
	int id;
//	LinkedList<String[]> writeRequests;
	LinkedList<ResourceManager> rmList;
	
	public Transaction(int i) {
		id = i;
//		writeRequests = new LinkedList<String[]>();
		rmList = new LinkedList<ResourceManager>();
	}
	
	public int getID() {
		return id;
	}
	
	//public void addWR(String[] request) {
		//addrmL(rmL);
		//writeRequests.add(request);
	//}
	
	public void addrm(ResourceManager rm) {
		if (!rmList.contains(rm)) {
			rmList.add(rm);
		}
	}
	
	/*public LinkedList<String[]> getWR() {
		LinkedList<String[]> wrCopy = new LinkedList<String[]>();
		for (String[] sa: writeRequests) {
			wrCopy.add(sa.clone());
		}
		return wrCopy;
	}*/
	
	public LinkedList<ResourceManager> getRMList() {
		return rmList;
	}

}
