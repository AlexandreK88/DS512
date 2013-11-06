package LockManager;

/*
	The transaction requested a lock that it already had.
*/

public class RedundantLockRequestException extends Exception
{
	private static final long serialVersionUID = -2257968081131971263L;
	
	protected int xid = 0;
	
	public RedundantLockRequestException (int xid, String msg)
	{
		super(msg);
		this.xid = xid;
		System.out.println(msg);
	}
	
	public int getXId() {
		return this.xid;
	}
}
