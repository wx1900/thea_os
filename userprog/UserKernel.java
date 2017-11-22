package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashMap;

// phase2 - ii
// come up with a way allocate machine's physical memory
// allocate a fixed number of pages for the process'work
// 8 pages should be sufficient
// maintain a global linked list of free physical pages
// should be effecinet ; not continous but have gaps

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
	       super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
    	super.initialize(args);

    	console = new SynchConsole(Machine.console());

    	Machine.processor().setExceptionHandler(new Runnable() {
    		public void run() { exceptionHandler(); }
    	    });
        
        // from thinkhy -- modified by wx
        int numPhysPages = Machine.processor().getNumPhysPages(); //return the total number of physical pages
        // add all the physical pages to the physical page table (linked list)
        for (int i = 0; i < numPhysPages; i++) {
            physPageTable.add(i);
        }
    }

    /**
     * Test the console device.
     */
    public void selfTest() {
    	// super.selfTest();
        // disable this to test for the phase2 temporally

    	System.out.println("Testing the console device. Typed characters");
    	System.out.println("will be echoed until q is typed.");

        
    	char c;

    	do {
            // using simaphore P() wait for input
            // if need to wait, using KThread.sleep()
            // main thread went into waiting queue
            // readythread get empty so execute idle
    	    c = (char) console.readByte(true);
    	    console.writeByte(c);
    	}
    	while (c != 'q');

    	System.out.println("");
        
    }

    /**
     * Returns the current process.
     *
     * @return	the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
    	if (!(KThread.currentThread() instanceof UThread))
    	    return null;

    	return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
    	Lib.assertTrue(KThread.currentThread() instanceof UThread);

    	UserProcess process = ((UThread) KThread.currentThread()).process;
    	int cause = Machine.processor().readRegister(Processor.regCause);
    	process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
    	super.run();   // threadedKernel.run() // empty method
        // create user thread
    	UserProcess process = UserProcess.newUserProcess();
        // shellProgram = halt.coff or sh.coff
    	String shellProgram = Machine.getShellProgramName();
        // this user thread create a nachos user thread (UThread),
        // this user thread create a child KThread to run shell through KThread
    	Lib.assertTrue(process.execute(shellProgram, new String[] { }));

    	KThread.currentThread().finish();
        System.out.println("KThread.currentThread().finish() -- UserKernel.java run()");
        // KThread main thread finished, and child thread implement shell
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	       super.terminate();
    }

    // from thinkhy -- modified by wx
    
    public static int getFreePage() {
        int pageNumber = -1;
        Machine.interrupt().disable();
        if (physPageTable.isEmpty() == false)
            pageNumber = physPageTable.removeFirst();
        // get first free physical page by remove it from physPageTable 
        Machine.interrupt().enable();
        return pageNumber;
    }

    public static void addFreePage(int pageNumber) {
        // make sure the pageNumber is leagal
        Lib.assertTrue(pageNumber>=0 && pageNumber < Machine.processor().getNumPhysPages());
        Machine.interrupt().disable();
        // add the pageNumber to the end of physPageTable  
        physPageTable.add(pageNumber);
        Machine.interrupt().enable();
    }

    public static int getNextPid() {
        int retval;
        Machine.interrupt().disable();
        retval = ++ nextPid;
        Machine.interrupt().enabled();
        return nextPid;
    }

    public static UserProcess getProcessByID(int pid) {
        return processMap.get(pid);
    }

    public static UserProcess registerProcess(int pid, UserProcess process) {
        UserProcess insertedProcess;
        Machine.interrupt().disable();
        insertedProcess = processMap.put(pid, process);
        Machine.interrupt().enable();
        return insertedProcess;
    }

    public static UserProcess unregisterProcess(int pid) {
        UserProcess deletedProcess;
        Machine.interrupt().disable();
        deletedProcess = processMap.remove(pid);
        Machine.interrupt().enabled();
        return deletedProcess;
    }
    
    // 

    // public static void deallocatePage(int a0){}
    // public static int allocatePage(){return 0;}
    

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;

    // from thinkhy - modified by wx
    // maintain a global linked list of free physical pages.
    private static LinkedList<Integer> physPageTable = new LinkedList<Integer>();
    private static int nextPid = 0;
    private static HashMap<Integer, UserProcess> processMap = new HashMap<Integer, UserProcess>();
    
}
