package Server.ResInterface;

public class InvalidTransactionException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public InvalidTransactionException (int xid, String msg)
    {
        super("The transaction " + xid + " aborted: " + msg);
    }

}
