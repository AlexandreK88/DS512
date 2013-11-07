package Server.ResInterface;

public class TransactionAbortedException extends Exception {
    
	private static final long serialVersionUID = 3294226088243719419L;

	public TransactionAbortedException (int xid, String msg)
    {
        super("The transaction " + xid + " aborted:" + msg);
    }

}
