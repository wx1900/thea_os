package nachos.userprog;

import java.util.*;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */

 /**
  * phase2 - ii
  * come up with a way allocate machine's physical memory
  * allocate a fixed number of pages for the process'work
  * 8 pages should be sufficient
  * maintain a global linked list of free physical pages
  * should be effecinet ; not continous but have gaps
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
        // initialize the kernel
        super.initialize(args);
        // create a synchronized console -- Amazing!
        console = new SynchConsole(Machine.console());
        // set the processor's exception handler
        Machine.processor().setExceptionHandler(new Runnable() {
            public void run() { exceptionHandler(); }
            });
        
        // initialize the memoryLinkedList
        memoryLinkedList=new LinkedList();  
        // add memoryPageNum to memoryLinkedList
        // initialize as continous num but change along memory allocate
        // hence memory pages will have gaps 
        for(int i=0;i<Machine.processor().getNumPhysPages();i++)
            memoryLinkedList.add((Integer)i);      
        // initialize memory allocate Lock
        allocateMemoryLock=new Lock();
	}

    /**
     * Test the console device.
     */	
    public void selfTest() {
	    // super.selfTest();
        // disable this to test for the phase2 temporally
/*
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
*/
// diable this part for quick access to shell
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
        // empty method
        super.run(); 
        // The first (ROOT) User Process 
        UserProcess process = UserProcess.newUserProcess();
        String shellProgram = Machine.getShellProgramName();        
        Lib.assertTrue(process.execute(shellProgram, new String[] { }));
        // finish currentThread
        KThread.currentThread().finish();
        System.out.println("KThread.currentThread().finish() -- UserKernel.java run()");	
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	    super.terminate();
    }

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;
    // memory allocate lock
    public static Lock allocateMemoryLock;
    // memory page linklist - store free memory pageNum
    public static LinkedList<Integer> memoryLinkedList;
    
}




