package nachos.threads;

import java.util.TreeMap;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    private TreeMap<Long, KThread> waitingThread;
    public Alarm() {
        waitingThread = new TreeMap<Long, KThread>();
        Machine.timer().setInterruptHandler(new Runnable() {
                public void run() { timerInterrupt(); }
            });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        boolean intStatus = Machine.interrupt().disable();
        long current = Machine.timer().getTime();
        // System.out.println("timerInterrupt - check - now - "+current);
        // check the key
        // it requests at least x time so this counts
        // if firstKey() as the waketime is up then get ready
        while (!waitingThread.isEmpty() && waitingThread.firstKey() <= current)
            waitingThread.pollFirstEntry().getValue().ready();
        // Removes and returns a key-value mapping associated with the least key in this map, or null if the map is empty.
        Machine.interrupt().restore(intStatus);

        // Preempt current thread as normal
        KThread.yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        // for now, cheat just to get something working (busy waiting is bad)
        boolean intStatus = Machine.interrupt().disable();
        long wakeTime = Machine.timer().getTime() + x;
        // Place current thread on a wait queue and put it to sleep
        waitingThread.put(wakeTime, KThread.currentThread());
        KThread.sleep();
        Machine.interrupt().restore(intStatus);
    }
    

}