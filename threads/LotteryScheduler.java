package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */

public class LotteryScheduler extends Scheduler {
	
	/**
     * Allocate a new lottery scheduler.
     */
	public LotteryScheduler() {
	}
	
	/**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
			
		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
			
		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());
			
		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);
	
		getThreadState(thread).setPriority(priority);
	}

	/** the number of tickets held by a process should be increment by one */
	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
				
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority + 1);
		System.out.println("thread-" + thread.getName() + " priority + 1 = " + (priority+1));
		Machine.interrupt().restore(intStatus);
		return true;
	}

	/** the number of tickets held by a process should be decrement by one */
	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
				
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority - 1);
		System.out.println("thread-" + thread.getName() + " priority - 1 = " + (priority-1));
		Machine.interrupt().restore(intStatus);
		return true;
	}

	public static final int priorityDefault = 1;    
	
	public static final int priorityMinimum = 0;    
	
	/** the priority Maximum should be Integer.MAX_VALUE instead of 7 */
	public static final int priorityMaximum = Integer.MAX_VALUE;    

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param	thread	the thread whose scheduling state to return.
	 * @return	the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}    
	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class LotteryQueue extends ThreadQueue {
	
		LotteryQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
		
			getThreadState(thread).waitForAccess(this);
		}


		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
		
			if (!thread.getName().equals("main")) {
				getThreadState(thread).acquire(this);
			}
		}

		public KThread nextThread() {
		
			Lib.assertTrue(Machine.interrupt().disabled());
			
			if(waitQueue.isEmpty())
				return null;
			
			int allTickets=0;
			
			/** count the total number of the tickets in waitQueue */
			for (int i = 0; i < waitQueue.size(); i++) {  
				ThreadState thread = waitQueue.get(i);
				allTickets = allTickets + thread.getEffectivePriority();
			}
			
			/** get the number of tickets to win */
			int toWin = Lib.random(allTickets + 1);

			int nowTickets = 0;
			KThread winThread = null;
			ThreadState threadState = null;
			/** if the thread holds more tickets, it has more chance to wins */
			for (int i = 0; i < waitQueue.size(); i++) {  
				threadState = waitQueue.get(i);
				nowTickets = nowTickets + threadState.getEffectivePriority();
				
				if (nowTickets >= toWin) {
					winThread = threadState.thread;
				    break;
				}
			}
			System.out.println("allTickets = "+allTickets + " toWin = " + toWin + " winThread = "+winThread.getName());
			if(winThread != null)
				waitQueue.remove(threadState);
			// if (!winThread.getName().equals("sh.coff"))
				// System.out.println("winThread is " + winThread.getName());
			// winThread is sh.coff or echo.coff or something similar
			return winThread;	    
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		*/
		protected ThreadState pickNextThread() {
			// implement me (if you want)
			
			return null;
		}
		
		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		public LinkedList<ThreadState> waitQueue = new LinkedList<ThreadState>();
		public ThreadState threadState = null;
		private int index;

    }

    
    protected class ThreadState {
	
		public ThreadState(KThread thread) {
			this.thread = thread;
			setPriority(priorityDefault);
			lotteryQueue = new LotteryQueue(true); // true - transferPriority
		}

	
		public int getPriority() {
			return priority;
		}

		/**
		 * A waiting thread always adds its ticket count to the ticket count of the queue owner;
		 * that is, the owner's ticket count is the sum of its own tickets and 
		 * the tickets of all waiters, not the max. 
		 */
		public int getEffectivePriority() {	
			Lib.assertTrue(Machine.interrupt().disabled());		
			effectivepriority = priority;
			// System.out.println("thread " + thread.getName() + " lotterQueue.waitQueue.size = " +lotteryQueue.waitQueue.size());
			for(int i = 0; i < lotteryQueue.waitQueue.size(); i++) {
				int temp = lotteryQueue.waitQueue.get(i).getEffectivePriority();
				// System.out.println("i = " + i + " thread="+ lotteryQueue.waitQueue.get(i).thread.getName() +" effectivePriority = " + temp);
				effectivepriority = effectivepriority + temp;
				// effectivepriority += lotteryQueue.waitQueue.get(i).getEffectivePriority();
			}
			if (priority != effectivepriority)
			System.out.println("thread " + thread.getName() + " effectivePriority = "+effectivepriority);
			return effectivepriority;
		}

	
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;
			this.priority = priority;	
		}

	
		public void waitForAccess(LotteryQueue lotteryQueue) {
			// add this thread to waitQueue
			lotteryQueue.waitQueue.add(this);
			// add this thread to the target thread's waitQueue
			if (lotteryQueue.threadState != null && lotteryQueue.threadState != this) {
				lotteryQueue.threadState.lotteryQueue.waitForAccess(this.thread);
			}
		}

		public void acquire(LotteryQueue lotteryQueue) {
			lotteryQueue.threadState = this;
		}	

		/** The thread realted with this threadState
		 *  ThreadState describe the shechduling state of this thread
		 */
		protected KThread thread;
		
		/** priority  */
		protected int priority;
		/** effective priority
		 *  class ThreadState calculate the effectivePriority
		 */
		protected int effectivepriority;
		/** lotteryQueue 
		 *  The waitQueue of this thread is envoloped in lotteryQueue
		 */
		protected LotteryQueue lotteryQueue;	
    }
}