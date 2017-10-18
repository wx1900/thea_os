package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * wx - 10.17
 * basicly I took the code from semaphore and make it work directly here
 * using locks
 * this works fine since the code is almostly same
 * and I pust 'c + ' to mark the notation from this function
 */

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
        this.conditionLock = conditionLock;
        waitQueue_1 = new LinkedList<Condition2>();
    }

    String getSName () {
		return this.name;
	}
	void setSName (String name) {
		this.name = name;
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());

        Lock lock1 = new Lock();
        Condition2 waiter = new Condition2(lock1);
        waitQueue_1.add(waiter);

        conditionLock.release();
        waiter.P();
        conditionLock.acquire();
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        if (!waitQueue_1.isEmpty())
            (waitQueue_1.removeFirst()).V();
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        while (!waitQueue_1.isEmpty())
            wake();
    }

    public void P() {
        boolean intStatus = Machine.interrupt().disable();
        
        if (value == 0) {
            waitQueue.waitForAccess(KThread.currentThread());
            KThread.sleep();
            System.out.println("c + thread -" + KThread.currentThread().getName() +" sleep P " + this.getSName());
        }
        else {
            value--;
        }

        Machine.interrupt().restore(intStatus);
    }

    public void V() {
        boolean intStatus = Machine.interrupt().disable();
        
        KThread thread = waitQueue.nextThread();
        if (thread != null) {
            thread.ready();
            System.out.println("c + thread -" + thread.getName() + " ready V " + this.getSName());
        }
        else {
            value++;
        }
        
        Machine.interrupt().restore(intStatus);
    }
    private static class PingTest implements Runnable {
		PingTest(Condition2 ping, Condition2 pong) {
			this.ping = ping; this.ping.setSName("PING");
			this.pong = pong; this.pong.setSName("PONG");
		}

		// ping ready
		// ping sleep
		public void run() {
			for (int i=0; i<10; i++) {
				ping.P();
				pong.V();
			}
		}

		private Condition2 ping;
		private Condition2 pong;
    }

    /**
     * Test if this module is working.
     */
    public static void selfTest() {
        System.out.println("Condition2 - selfTest - begin.");
        
        Lock lock1 = new Lock();
        Lock lock2 = new Lock();
		Condition2 ping = new Condition2(lock1);
		Condition2 pong = new Condition2(lock2);

		new KThread(new PingTest(ping, pong)).setName("thea").fork();
		
		// main ready 
		// main sleep
		for (int i=0; i<10; i++) {
			ping.V();
			pong.P();
		}

		System.out.println("Condition2 - selfTest - finished.");
    }

	
    
    private int value;
    private String name;
    private Lock conditionLock;
    private ThreadQueue waitQueue =	ThreadedKernel.scheduler.newThreadQueue(false);
    private LinkedList<Condition2> waitQueue_1;
}

