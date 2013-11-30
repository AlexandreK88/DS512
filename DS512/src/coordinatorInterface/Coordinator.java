package coordinatorInterface;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Coordinator extends Remote {

	public boolean transactionCommitted(int id) throws RemoteException;
	
}
