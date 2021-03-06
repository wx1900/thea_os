package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

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
     * @param transferPriority <tt>true</tt> if this queue should
     *     transfer priority from waiting threads
     *     to the owning thread.
     * @return a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
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

        System.out.println("Set priority " + priority + " for " + thread);
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
     * @param thread the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
            this.donationController = new DonationController(queue);
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            Lib.assertTrue(!threadStates.containsKey(getThreadState(thread)));

            threadStates.put(getThreadState(thread), new ThreadWrapper(getThreadState(thread)));
            queue.add(threadStates.get(getThreadState(thread)));

            getThreadState(thread).waitForAccess(this);

            if(transferPriority)
                donationController.transferPriority(getThreadState(thread));
            
            // System.out.println("Inserted: " + thread + ", priority = " + getThreadState(thread).getPriority() + ", effective priority = " + getThreadState(thread).getEffectivePriority() + ", size = " + queue.size());
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            Lib.assertTrue(!threadStates.containsKey(getThreadState(thread)));

            getThreadState(thread).acquire(this);

            if(transferPriority)
                donationController.setTarget(getThreadState(thread));

            // System.out.println("Acquired: " + thread + ", priority = " + getThreadState(thread).getPriority() + ", effective priority = " + getThreadState(thread).getEffectivePriority() + ", size = " + queue.size());
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            if(queue.isEmpty())
                return null;

            ThreadWrapper w = queue.poll();
            threadStates.remove(w.state);
            // if allow transferPriority , give w.state the maximum priority in queue
            if(transferPriority)
                donationController.resetMaximumPriority(w.state);
            // System.out.println("NextThread: " + w.state.thread + ", priority = " + w.state.getPriority() + ", effective priority = " + w.state.getEffectivePriority() + ", size = " + queue.size());
            acquire(w.state.thread);
            return w.state.thread;
        }

        public void updateThreadState(ThreadState s) {
            if(threadStates.containsKey(s)) {
                System.out.println("Updating: " + s.thread + ", priority = " + s.getPriority() + ", effective priority = " + s.getEffectivePriority());
                ThreadWrapper w = threadStates.get(s);
                queue.remove(w);
                queue.add(w);
            }
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would
         *  return.
         */
        protected ThreadState pickNextThread() {
            return queue.peek().state;
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            System.out.println("In queue: " + queue.size() + " total");
            for(Map.Entry<ThreadState, ThreadWrapper> e : threadStates.entrySet()) {
                System.out.println("  " + e.getKey().thread + " priority " + e.getKey().getEffectivePriority());
            }
        }

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;
        public java.util.PriorityQueue<ThreadWrapper> queue = new java.util.PriorityQueue<ThreadWrapper>();
        public HashMap<ThreadState, ThreadWrapper> threadStates = new HashMap<ThreadState, ThreadWrapper>();

        public DonationController donationController;

        protected class ThreadWrapper implements Comparable {

            public ThreadWrapper(ThreadState s) {
                state = s;
                timeInserted = Machine.timer().getTime();
            }

            public int compareTo(Object o) {
                Lib.assertTrue(o instanceof ThreadWrapper);
                ThreadWrapper s = (ThreadWrapper) o;
                // if equal return time difference else return the difference between effective priority 
                return (state.getEffectivePriority() == s.state.getEffectivePriority()) ? (int)(timeInserted - s.timeInserted) : (s.state.getEffectivePriority() - state.getEffectivePriority());
            }

            public ThreadState state;
            public long timeInserted;
        }
    }

    protected class DonationController {

        public DonationController(java.util.PriorityQueue<PriorityQueue.ThreadWrapper> queue) {
            this.target = null;
            this.maximumPriority = priorityMinimum;
            this.queue = queue;
        }

        public void setTarget(ThreadState t) {
            if(target != null)
                target.retractDonatedPriority(this);
            target = t;
            target.donatePriority(this, maximumPriority);
            // System.out.println("Donation target is set to " + t.thread);
        }

        public void resetMaximumPriority(ThreadState t) {
            if(t.getEffectivePriority() == maximumPriority) {
                maximumPriority = priorityMinimum;
                for(PriorityQueue.ThreadWrapper w : queue)
                    maximumPriority = Math.max(maximumPriority, w.state.getEffectivePriority());
                // System.out.println("Reset maximum priority to " + maximumPriority);
            }
        }

        public void transferPriority(ThreadState t) {
            maximumPriority = Math.max(t.getEffectivePriority(), maximumPriority);
            if(target != null && maximumPriority > target.getEffectivePriority()) {
                target.donatePriority(this, maximumPriority);
            // System.out.println("Maximum priority " + maximumPriority + " transferred to " + t.thread);
            }
        }

        protected ThreadState target;
        protected int maximumPriority;

        protected java.util.PriorityQueue<PriorityQueue.ThreadWrapper> queue;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;
            setPriority(priorityDefault);
        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() {
            return effectivePriority.getEffectivePriority();
        }

        public void donatePriority(DonationController q, int donation) {
            effectivePriority.donate(q, donation);
            notifyParents();
            System.out.println("Donated to: " + thread + " with donation " + donation);
        }

        public void retractDonatedPriority(DonationController q) {
            effectivePriority.retract(q);
            notifyParents();
            // System.out.println("Retract donation: " + thread + ", now priority = " + getPriority() + ", effective priority = " + getEffectivePriority());
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param priority the new priority.
         */
        public void setPriority(int priority) {
            if (this.priority == priority)
                return;

            this.priority = priority;
            effectivePriority.setPriority(priority);
            notifyParents();
        }

        public void notifyParents() {
            for(PriorityQueue q : parents) {
                q.updateThreadState(this);
            }
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param waitQueue the queue that the associated thread is
         *    now waiting on.
         *
         * @see nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {
            parents.add(waitQueue);
        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see nachos.threads.ThreadQueue#acquire
         * @see nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            parents.remove(waitQueue);
        }

        /** The thread with which this object is associated. */
        protected KThread thread;
        /** The priority of the associated thread. */
        protected int priority;
        protected EffectivePriorityDesc effectivePriority = new EffectivePriorityDesc(0);
        protected HashSet<PriorityQueue> parents = new HashSet<PriorityQueue>();

        protected class EffectivePriorityDesc {
            EffectivePriorityDesc(int priority) {
                this.priority = priority;
                this.max_donation = 0;
            }

            void setPriority(int priority) {
                this.priority = priority;
            }

            int getEffectivePriority() {
                return Math.max(priority, max_donation);
            }

            void donate(DonationController q, int priority) {
                this.donations.put(q, priority);
                max_donation = Math.max(max_donation, priority);
            }

            void retract(DonationController q) {
                if(donations.containsKey(q)) {
                    int p = donations.get(q);
                    donations.remove(q);
                    if(max_donation == p) {
                        max_donation = 0;
                        for(Map.Entry<DonationController, Integer> e : donations.entrySet()) {
                            max_donation = Math.max(max_donation, e.getValue());
                        }
                    }
                }
            }

            protected int priority, max_donation;
            protected HashMap<PriorityScheduler.DonationController, Integer> donations = new HashMap<DonationController, Integer>();
        }
    }
}
