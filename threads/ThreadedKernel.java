package nachos.threads;

import nachos.machine.*;

/**
 * A multi-threaded OS kernel.
 */
public class ThreadedKernel extends Kernel {
    /**
     * Allocate a new multi-threaded kernel.
     */
    public ThreadedKernel() {
	    super();
    }

    /**
     * Initialize this kernel. Creates a scheduler, the first thread, and an
     * alarm, and enables interrupts. Creates a file system if necessary.
     */
    public void initialize(String[] args) {
        System.out.println("ThreadedKernel - intializing");
        // set scheduler
        String schedulerName = Config.getString("ThreadedKernel.scheduler");
        scheduler = (Scheduler) Lib.constructObject(schedulerName);

        // set fileSystem
        String fileSystemName = Config.getString("ThreadedKernel.fileSystem");
        if (fileSystemName != null){
            System.out.println("ThreadedKernel - fileSystem - is ?");
            fileSystem = (FileSystem) Lib.constructObject(fileSystemName);
        } else if (Machine.stubFileSystem() != null) {
            System.out.println("ThreadedKernel - fileSystem - is stubFilesystem");
            fileSystem = Machine.stubFileSystem();
        }
        else
            fileSystem = null;

        // start threading
        new KThread(null);

        alarm  = new Alarm();

        Machine.interrupt().enable();
    }

    /**
     * Test this kernel. Test the <tt>KThread</tt>, <tt>Semaphore</tt>,
     * <tt>SynchList</tt>, and <tt>ElevatorBank</tt> classes. Note that the
     * autograder never calls this method, so it is safe to put additional
     * tests here.
     */
    public void selfTest() {
        System.out.println("\nFor test details, please read README.\n");
        /**  Proj 1 Task 1 
         * & Proj 1 Task 5 
         * & Proj 2 Task 5 
         */
        // KThread.selfTest();

        // Semaphore.selfTest();
        // SynchList.selfTest();
        
        /** Proj1 Task 2 condition2*/
        // Condition2.selfTest();
        
        /** Proj 1 task 3 alarm*/
        // alarmTest(1);
        // alarmTest(100);
        // alarmTest(500);
        // alarmTest(10000);
    
        /** Proj 1 task 4 communicator*/
        // Communicator.selfTest();
        
        if (Machine.bank() != null) {
            ElevatorBank.selfTest();
        }
    }
    public void alarmTest(long x) {
        System.out.println("alarmTest-begin-"+Machine.timer().getTime()+" - test for - "+x);
        alarm.waitUntil(x);
        System.out.println("alarmTest-finished-"+Machine.timer().getTime());
    }
    /**
     * A threaded kernel does not run user programs, so this method does
     * nothing.
     */
    public void run() {
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	    Machine.halt();
    }

    /** Globally accessible reference to the scheduler. */
    public static Scheduler scheduler = null;
    /** Globally accessible reference to the alarm. */
    public static Alarm alarm = null;
    /** Globally accessible reference to the file system. */
    public static FileSystem fileSystem = null;

    // dummy variables to make javac smarter
    private static RoundRobinScheduler dummy1 = null;
    private static PriorityScheduler dummy2 = null;
    private static LotteryScheduler dummy3 = null;
    private static Condition2 dummy4 = null;
    private static Communicator dummy5 = null;
    private static Rider dummy6 = null;
    private static ElevatorController dummy7 = null;
}
