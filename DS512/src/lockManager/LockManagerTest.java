package lockManager;

class LockManagerTest {
	public static void main (String[] args) {
		MyThread t1, t2;
		LockManager lm = new LockManager ();
		t1 = new MyThread (lm, 1);
		t2 = new MyThread (lm, 2);
		t1.start ();
		t2.start ();
	}
}

class MyThread extends Thread {
	LockManager lm;
	int threadId;

	public MyThread (LockManager lm, int threadId) {
		this.lm = lm;
		this.threadId = threadId;
	}

	public void run () {
		if (threadId == 1) {
			try {
				System.out.println("Thread 1, reading a");
				lm.Lock (1, "a", LockManager.READ);
				//lm.Lock (1, "a", LockManager.WRITE);
			}
			catch (DeadlockException e) {
				System.out.println ("Deadlock.... ");
			}
			try {
				System.out.println("Thread 1, reading b");
				lm.Lock (1, "b", LockManager.READ);
				//lm.Lock (1, "a", LockManager.WRITE);
			}
			catch (DeadlockException e) {
				System.out.println ("Deadlock.... ");
			}
			try {
				sleep(2000);
			}
			catch (InterruptedException e) { }

			try {
				//lm.Lock (1, "b", LockManager.WRITE);
				System.out.println("Thread 1, writing a");
				lm.Lock (1, "a", LockManager.WRITE);
			}
			catch (DeadlockException e) {
				System.out.println ("Deadlock.... ");
			}
			System.out.println("Thread 1 unlocking all");
			lm.UnlockAll (1);
		}
		else if (threadId == 2) {
			try {
				System.out.println("Thread 2, reading b");
				lm.Lock (2, "b", LockManager.READ);
				//lm.Lock (2, "a", LockManager.READ);
			}
			catch (DeadlockException e) { 
				System.out.println ("Deadlock.... ");
			}

			try {
				System.out.println("Thread 2, writing b");
				lm.Lock (2, "b", LockManager.WRITE);
				//lm.Lock (2, "a", LockManager.READ);
			}
			catch (DeadlockException e) { 
				System.out.println ("Deadlock.... ");
			}
			System.out.println("Thread 2 unlocking all");
			lm.UnlockAll (2);
		}
	}
}