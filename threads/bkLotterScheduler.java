package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

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

public class LotteryScheduler extends PriorityScheduler {
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

	@Override
    public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
		       
		return getThreadState(thread).getPriority();
	}
	
	@Override
    public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
		       
		return getThreadState(thread).getEffectivePriority();
    }

	@Override
    public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());
		       
		Lib.assertTrue(priority >= priorityMinimum &&
			   priority <= priorityMaximum);
	
		getThreadState(thread).setPriority(priority);
    }

	@Override
    public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
		    return false;

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
    }

	@Override
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

    
    public static final int priorityDefault = 1;    
    
    public static final int priorityMinimum = 0;  
    
    public static final int priorityMaximum = 7;    

    @Override
    protected LotteryThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}    
	
    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class LotteryQueue extends PriorityScheduler.PriorityQueue {
	
    	LotteryQueue(boolean transferPriority) {
		    this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {//传入等待队列的线程
			Lib.assertTrue(Machine.interrupt().disabled());
			
			
			getThreadState(thread).waitForAccess(this);
			
		}

	
		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			
			if(!thread.getName().equals("main"))
			{
				getThreadState(thread).acquire(this);
			}
		}

		public KThread nextThread() {
		
			Lib.assertTrue(Machine.interrupt().disabled());
	    
	   		if(waitQueue.isEmpty())
	    		return null;
	    
	  	    int alltickets=0;
	    
			for(int i=0;i<waitQueue.size();i++)//计算队列中所有彩票的总数
			{  ThreadState thread=waitQueue.get(i);
			alltickets=alltickets+thread.getEffectivePriority();
			
			}
	    
	    
			int numOfWin=Lib.random(alltickets+1);//产生中奖的数目
			
			int nowtickets=0;
			KThread winThread=null;
			ThreadState thread=null;
			for(int i=0;i<waitQueue.size();i++)//得到获胜的线程
			{  thread=waitQueue.get(i);
			nowtickets=nowtickets+thread.getEffectivePriority();
				if(nowtickets>=numOfWin)
				{winThread=thread.thread;
				break;
				}
				
			}
		
			if(winThread!=null)
				waitQueue.remove(thread);
			
		
			return winThread;	    
	    }

		@Override
		protected LotteryThreadState pickNextThread() {
			
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
		public LinkedList<LotteryThreadState> waitQueue = new LinkedList<LotteryThreadState>();
		public LotteryThreadState linkedthread=null;
		private int index;
    }

    
	protected class LotteryThreadState extends PriorityScheduler.ThreadState {
	
    	
	public LotteryThreadState (KThread thread) {
	    this.thread = thread;
	    setPriority(priorityDefault);
        waitQueue=new LotteryQueue(true);
	}

	
	public int getPriority() {
	    return priority;
	}

	@Override
	public int getEffectivePriority() {
	    // implement me
		
         effectivepriority=priority;
		
		for(int i=0;i<waitQueue.waitQueue.size();i++)
		
		effectivepriority=effectivepriority+waitQueue.waitQueue.get(i).getEffectivePriority();//把等待线程持有的彩票的总数给这个线程
		
		
			
		return effectivepriority;
		
	}

	
	public void setPriority(int priority) {//优先级传递
		if (this.priority == priority)
			return;
		    this.priority = priority;
		    
	    
	    // implement me
	}

	
	public void waitForAccess(LotteryQueue waitQueue) {//将此线程状态存入传入的等待队列
	    // implement me
    waitQueue.waitQueue.add(this);
		
		if(waitQueue.linkedthread!=null&&waitQueue.linkedthread!=this)
		{
		   waitQueue.linkedthread.waitQueue.waitForAccess(this.thread);
		}
        
	}

	
	public void acquire(LotteryQueue waitQueue) {//相当于一个线程持有的队列锁
	   
		waitQueue.linkedthread=this;
	}	

	
	protected KThread thread;//这个对象关联的线程
	
	protected int priority;//关联线程的优先级

	protected int effectivepriority;//有效优先级
	
	protected LotteryQueue waitQueue;
                            	
	
	
    }
}