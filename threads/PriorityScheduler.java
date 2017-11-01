package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    

    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityThreadQueue(transferPriority);
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
		       
		Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
		getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		       
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority+1);

		Machine.interrupt().restore(intStatus);
		return true;
    }

    public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
				
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority-1);

		Machine.interrupt().restore(intStatus);
		return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

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

// for test
  	// private static class PingTest implements Runnable {
    //     PingTest(int which) {
    //         this.which = which;
    //     }
        
    //     public void run() {
    //         for (int i=0; i<5; i++) {
    //         System.out.println("*** thread " + which + " looped "
    //                 + i + " times");
    //         currentThread.yield();
    //         }
    //     }

    //     private int which;
    // }

	// public void selfTest(){
	// 	boolean intStatus = Machine.interrupt().disable();//关中断，setPriority()函数中要求关中断
	// 	System.out.println("PriorityScheduler-selftest-begin");
	// 	KThread t1 = new KThread(new PingTest(1).setName("t1"));
	// 	KThread t2 = new KThread(new PingTest(2).setName("t2"));
	// 	KThread t3 = new KThread(new PingTest(3).setName("t3"));
	// 	new PriorityScheduler().setPriority(t1,2);
	// 	new PriorityScheduler().setPriority(t2,4);
	// 	new PriorityScheduler().setPriority(t3,6);
	// 	t1.fork();
	// 	t2.fork();
	// 	t3.fork();
	// 	System.out.println("PriorityScheduler-selftest-finished");
	// 	Machine.interrupt().restore(intStatus);
	// }

// for test

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityThreadQueue extends ThreadQueue {
		PriorityThreadQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me -- 1
			ThreadState threadState = this.pickNextThread();
			if (threadState != null){
				//System.out.println(threadState.thread.toString());
				//System.out.println(threadState.age);
			}
			priorityQueue.remove(threadState);
			if (transferPriority && threadState != null) {
				this.dequeuedThread.removeQueue(this);
				threadState.waiting = null;
				threadState.addQueue(this);
			}
			this.dequeuedThread = threadState;
			if (threadState == null){
				this.priorityQueue = new PriorityQueue<ThreadState>();
				return null;
			}
			return threadState.thread;
		}

		/**
		* Return the next thread that <tt>nextThread()</tt> would return,
		* without modifying the state of this queue.
		*
		* @return	the next thread that <tt>nextThread()</tt> would
		*		return.
		*/
		protected ThreadState pickNextThread() {
			// implement me -- 2
			boolean intStatus = Machine.interrupt().disable();
			//ensure priorityQueue is properly ordered
			//does this take the old priorityQueue and reorder it? YES!!!
			this.priorityQueue = new PriorityQueue<ThreadState>(priorityQueue);

			Machine.interrupt().restore(intStatus);
			return this.priorityQueue.peek();
		}
		
		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want) -- 3
		}

		/**
		* <tt>true</tt> if this queue should transfer priority from waiting
		* threads to the owning thread.
		*/
		public boolean transferPriority;
		protected PriorityQueue<ThreadState> priorityQueue = new PriorityQueue<ThreadState>();
		/** The most recently dequeued ThreadState. */
		protected ThreadState dequeuedThread = null;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState implements Comparable<ThreadState>{
		/**
		* Allocate a new <tt>ThreadState</tt> object and associate it with the
		* specified thread.
		*
		* @param	thread	the thread this state belongs to.
		*/
		public ThreadState(KThread thread) {
			this.thread = thread;
			setPriority(priorityDefault);
			
			this.Pricue = new LinkedList<PriorityThreadQueue>();
			this.age = Machine.timer().getTime();
			this.effectivePriority = priorityDefault;
			this.waiting = null;
		}

		/**
		* Return the priority of the associated thread.
		*
		* @return	the priority of the associated thread.
		*/
		public int getPriority() {
			return priority;
		}
		/**
		 * Calculate the Effective Priority of a thread and the thread that currently holds the resource
		 * it is waiting on.
		 */
		public void calcEffectivePriority() {
			int initialPriority = this.getPriority();
			int maxEP = -1;
			if (Pricue.size() != 0){
				//System.out.println(this.thread+", EP="+this.getEffectivePriority());
				int size = Pricue.size();
				//System.out.println(size);
				for(int i = 0; i < size; i++){
					PriorityThreadQueue current = Pricue.get(i);
					ThreadState donor = current.pickNextThread();
					if (donor != null){
						//System.out.println(donor.thread+", EP="+donor.getEffectivePriority());
						if ((donor.getEffectivePriority() > maxEP) && current.transferPriority)
							maxEP = donor.getEffectivePriority();
					}
				}
			}
			if (initialPriority > maxEP){
				maxEP = initialPriority;
			}
			this.effectivePriority = maxEP;
			//System.out.println(this.effectivePriority);
			//now that my own effectivePriority Changes I have to recalculate the threads which i am waiting on
			if (this.waiting != null && this.waiting.dequeuedThread != null){
				if (this.effectivePriority != this.waiting.dequeuedThread.effectivePriority){
					this.waiting.dequeuedThread.calcEffectivePriority();
				}
			};
			//System.out.println(this.effectivePriority);
		}
		/**
		* Return the effective priority of the associated thread.
		*
		* @return	the effective priority of the associated thread.
		*/
		public int getEffectivePriority() {
			// implement me -- 4
			return this.effectivePriority;
		}

		/**
		* Set the priority of the associated thread to the specified value.
		*
		* @param	priority	the new priority.
		*/
		public void setPriority(int priority) {
			if (this.priority == priority)
			return;
			
			this.priority = priority;
			
			// implement me -- 5
			//Pretty sure we doon't need following line since nothing should be accessing effectivePriority directly
			//this.effectivePriority = effectivePriority - (this.priority - priority);
			this.calcEffectivePriority();
			if(this.waiting != null && this.waiting.dequeuedThread != null)
				this.waiting.dequeuedThread.calcEffectivePriority();
			//this.waiting.dequeuedThread.calcEffectivePriority();
		}

		/**
		* Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		* the associated thread) is invoked on the specified priority queue.
		* The associated thread is therefore waiting for access to the
		* resource guarded by <tt>waitQueue</tt>. This method is only called
		* if the associated thread cannot immediately obtain access.
		*
		* @param	waitQueue	the queue that the associated thread is
		*				now waiting on.
		*
		* @see	nachos.threads.ThreadQueue#waitForAccess
		*/
		public void waitForAccess(PriorityThreadQueue waitQueue) {
			// implement me -- 6
			Lib.assertTrue(Machine.interrupt().disabled());
			long time = Machine.timer().getTime();
			this.age = time;
			waitQueue.priorityQueue.add(this);
			this.waiting = waitQueue;
			this.calcEffectivePriority();
		}

		/**
		* Called when the associated thread has acquired access to whatever is
		* guarded by <tt>waitQueue</tt>. This can occur either as a result of
		* <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		* <tt>thread</tt> is the associated thread), or as a result of
		* <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		*
		* @see	nachos.threads.ThreadQueue#acquire
		* @see	nachos.threads.ThreadQueue#nextThread
		*/
		public void acquire(PriorityThreadQueue waitQueue) {
			// implement me -- 7
			//Seems good, checks to see if queue is empty, if it is just make it dequeued thread.
			//needs to add waitQueue
			Lib.assertTrue(Machine.interrupt().disabled());
			Lib.assertTrue(waitQueue.priorityQueue.isEmpty());
			waitQueue.dequeuedThread = this;
			this.addQueue(waitQueue);
			this.calcEffectivePriority();
		}	
		public int compareTo(ThreadState threadState){
			//changed first if from > to <
			if (threadState == null)
				return -1;
			if (this.getEffectivePriority() < threadState.getEffectivePriority()){
				return 1;
			}else{ 
				if (this.getEffectivePriority() > threadState.getEffectivePriority()){
					return -1;
				}else{
					if (this.age >= threadState.age)
						return 1;
					else{ return -1; }
				}
			}
		}
		public void removeQueue(PriorityThreadQueue queue){
			Pricue.remove(queue);
			this.calcEffectivePriority();
		}
		public void addQueue(PriorityThreadQueue queue){
			Pricue.add(queue);
			this.calcEffectivePriority();
		}

		public String toString() {
			return "ThreadState thread=" + thread + ", priority=" + getPriority() + ", effective priority=" + getEffectivePriority();
		}
		/** The thread with which this object is associated. */	   
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority = priorityDefault;
		public long age = Machine.timer().getTime();
		protected LinkedList<PriorityThreadQueue> Pricue;
		protected int effectivePriority;
		protected PriorityThreadQueue waiting;

    }
}
