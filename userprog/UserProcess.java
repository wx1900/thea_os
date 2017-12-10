package nachos.userprog;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
	
	/*
	 * Allocate a new process.
	 */
	
	public UserProcess () {
		/** this process id is processCounter + 1 */
		pid = ++processCounter;
		processOn++;
		// define the first two openfiles 
		openfile[0] = UserKernel.console.openForReading();
		openfile[1] = UserKernel.console.openForWriting();
		// proj2.3
		System.out.println("Create a new UserProcess pid = " + pid);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess () {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

   /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
	public boolean execute (String name, String[] args) {
		// load executable file into this progress
		if (!load(name, args))
			return false;

		thread = new UThread(this);// set the execute object to UThread.runProgram()
        thread.setName(name).fork();
        // System.out.println("execute() - fork Sucessfully!");
		return true;
	}

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
	private boolean load (String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
 		// proj2 fileSystem = Machine.StubFileSystem
		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	    // open(name, false): name the application to load
        // false: not truncate
        // ture: clear the exist file and if not exist create one
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);			
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0; // each segment's first page in the pagetable

		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}

			numPages += section.getLength();
		}
		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}
		return true;
	}

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
	 protected boolean loadSections() {

		UserKernel.allocateMemoryLock.acquire();

		if (numPages > UserKernel.memoryLinkedList.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			UserKernel.allocateMemoryLock.release();
			return false;
		}
		
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
		{
			int nextPage=UserKernel.memoryLinkedList.remove();			
			pageTable[i] = new TranslationEntry(i, nextPage, true, false, false, false);
			// page structure:
       		// vpn the virtual page number
        	// ppn the physical page number
        	// valid the valid bit
        	// readOnly the read-only bit
        	// used the used bit
        	// dirty the dirty bit
		}
		UserKernel.allocateMemoryLock.release();
		
		// System.out.println("initialzing section ");
		for (int s = 0; s < coff.getNumSections(); s++) {
			// every sections in coff file
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				// move every pages in section to nachos 
				int vpn = section.getFirstVPN() + i;
				pageTable[vpn].readOnly = section.isReadOnly();
				section.loadPage(i, pageTable[vpn].ppn);
				// ~
				// for now, just assume virtual addresses=physical addresses
				// section.loadPage(i, vpn);
				// i : page number
				// VPN: 
				// loadPage(spn, ppn) 
				// spn: the page number within this segment
				// ppn: the physical page to load into.    
			}
		}
		
		return true;
	}
	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}
	
    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
	public String readVirtualMemoryString (int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
                return new String(bytes, 0, length);
            // public String(byte[] bytes, int offset, int length)
            // Parameters:
            // bytes - The bytes to be decoded into characters
            // offset - The index of the first byte to decode
            // length - The number of bytes to decode
		}

		return null;
	}

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
	public int readVirtualMemory (int vaddr, byte[] data, int offset, int length) 
	{
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

        // length is beyond the length of file only read from vaddr to the end
        if(length > (pageSize * numPages - vaddr))
            length = pageSize * numPages - vaddr;

        // length is larger than the length of array
        if(data.length - offset < length)
            length = data.length - offset;
        
        int transferredbyte=0; // the bytes already transferred
        
        do{            
            int pageNum = Processor.pageFromAddress(vaddr + transferredbyte);
            // if pageNum is leagal
            if(pageNum <0 || pageNum >= pageTable.length)
                return 0;
            int pageOffset = Processor.offsetFromAddress(vaddr + transferredbyte);
            int leftByte = pageSize - pageOffset;
            int amount = Math.min(leftByte, length -transferredbyte);        
            // get the physical address in pageTable     
            int physAddress = pageTable[pageNum].ppn * pageSize + pageOffset;
            // copy data from memory to data
            System.arraycopy(memory, physAddress, data, offset + transferredbyte, amount);
            transferredbyte = transferredbyte + amount;	
        } while(transferredbyte < length); // util transferred all bytes
			
	    return transferredbyte;
	}

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
	public int writeVirtualMemory (int vaddr, byte[] data) {
		return writeVirtualMemory (vaddr, data, 0, data.length);
	}

	/**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if(length > (pageSize * numPages - vaddr))
			length = pageSize * numPages - vaddr;
		if(data.length - offset < length)
			length = data.length - offset;
		
		int transferredbyte = 0;
		do{
			int pageNum = Processor.pageFromAddress(vaddr + transferredbyte);
			if(pageNum < 0 || pageNum >= pageTable.length)
				return 0;
			int pageOffset = Processor.offsetFromAddress(vaddr + transferredbyte);
			int leftByte = pageSize - pageOffset;
			int amount = Math.min(leftByte, length - transferredbyte);
			int realAddress = pageTable[pageNum].ppn * pageSize + pageOffset;
			System.arraycopy(data, offset + transferredbyte, memory, realAddress, amount);
			transferredbyte = transferredbyte + amount;	
		} while(transferredbyte < length);
		
        // ~
        // for now, just assume that virtual addresses equal physical addresses
        // if (vaddr < 0 || vaddr >= memory.length)
		//      return 0;
		
		return transferredbyte;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.allocateMemoryLock.acquire();
		
		for(int i=0;i<numPages;i++)
		{
			UserKernel.memoryLinkedList.add(pageTable[i].ppn);
			pageTable[i]=null;
		}

		UserKernel.allocateMemoryLock.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	

	private static final int 
		syscallHalt = 0, 
		syscallExit = 1,
		syscallExec = 2,
		syscallJoin = 3,
		syscallCreate = 4, 
		syscallOpen = 5,
		syscallRead = 6,
		syscallWrite = 7, 
		syscallClose = 8,
		syscallUnlink = 9;
 	/**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
			    System.out.println("Handle Halt");
				return handleHalt();
			
			case syscallCreate:
				/** the file argument is filename, hence a0 */
				return handleCreate(a0);
				
			case syscallExit:
				// System.out.println("Handle Exit");
				/** the file argument is filename, hence a0 */
				return handleExit(a0);
				
			case syscallJoin:
				/** pid statusAddress */
				return handleJoin(a0,a1);
				
			case syscallExec:
				return handleExec(a0,a1,a2);
				
			case syscallOpen:
				/** the file argument is filename, hence a0 */
				return handleOpen(a0);
				
			case syscallRead:
				// System.out.println("syscallRead"); 
				// keep execute syscallRead, read bytes from input
				/** need three argument a0:filename, a1:buf address, a2:buf size */
				return handleRead(a0,a1,a2);
				
			case syscallWrite:
				/** need three argument a0:filename, a1:buf address, a2:buf size */
				return handleWrite(a0,a1,a2);	
				
			case syscallClose:
				/** the file argument is filename, hence a0 */
				return handleClose(a0);
				
			case syscallUnlink:
				/** the file argument is filename, hence a0 */
				return handleUnlink(a0);

			default:
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}
	
	/**
     * Handle the halt() system call.
     */
	private int handleHalt() {
		System.out.println("Halt - process0n = " + processOn);
		/** if it's root process halt
		 *  pid = ++ processCounter
		 *  Hence the root pid = 1
		 */
		if (pid == 1)
			Machine.halt();
        else {
			System.out.println("*Machine.halt() did not halt machine!");
			return 0;
		}
        // Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

    /**
     *  Hanlde the exec system call
     */
	private int handleExec (int fileAddress, int argc, int argvAddress) {
		
		String filename = readVirtualMemoryString(fileAddress, MAX_FILENAME_LENGTH);
		
		if(filename == null || argc < 0 || argvAddress < 0 || argvAddress > numPages * pageSize)
			return -1;
		
		/** get args */
		String[] args = new String[argc];
		for (int i = 0; i < argc; i++) {
			byte[] argsAddress = new byte[4];
			if(readVirtualMemory(argvAddress + i * 4, argsAddress) > 0)
				args[i] = readVirtualMemoryString(Lib.bytesToInt(argsAddress, 0), 256);
		}
		
		/** create a child process to execute the coff file */
		UserProcess process = UserProcess.newUserProcess();
		if(! process.execute(filename, args)) {
			// if this process failed then decrease the counter of process
			processCounter--;
			processOn--;
			return -1;
		}
		/** add this process id to the created process's parent process id */
		process.ppid = this;
		/** add the child process created to this process's childProcess */
		childProcess.add(process);
		return process.pid;
	}

	/**
     *  Handle the exit() system call
     *  it's thread should be terminated immediatedly
     *  clean up process and state
     *  exit abnormally if kernel kills it
     *  The last process to call exit() should cause the machine to halt by calling Kernel.kernel.terminate()
     */
	private int handleExit (int status) {
		// proj2.3
		System.out.println("\nexit -- processOn = " + processOn + " - 1");
		/** close coff file */
        coff.close();
		/** close all the file opened by this process */
		for (int i = 0; i < 16; i++) {
			if(openfile[i] != null)	{
				openfile[i].close();
				openfile[i]=null;
			}
		}
		
		this.status = status;
		/** for join()  */
		normalExit = true;
		
		/** if has parent process, delete this process from childProcess
		 *  if parent process had joined this process, then wake it up
		 */
		if (ppid != null) {
			joinLock.acquire();
			joinCondition.wake();
			joinLock.release();
			ppid.childProcess.remove(this);	
		}
		
		/** unload sections */
		unloadSections();

		/** if it's the last process then terminate the machine */
		if (processOn == 1) {
			System.out.println("The last process - Kernel.kernel.terminate()");
			// Machine.halt();
			Kernel.kernel.terminate();
		}	
		/** decrease the running process */
		processOn--;

		/**
		 *  Finish the thread
		 */
		KThread.finish();

		return 0;
	}

	/**
     *  HandleJoin
	 *  TODO: understand this part
	 *  Only a process's parent can join it
	 *  The child Process's state is entirely private to the parent process
     */
	private int  handleJoin (int pid, int statusAddress) { 
	  
		UserProcess process = null;
	  
		/** check that this process is or not itself's childProcess */
		for(int i = 0;i < childProcess.size(); i++)
	    {
			if(pid == childProcess.get(i).pid) { 
				process = childProcess.get(i);
			    break;
			}
		}
	  
		/** if the process is null or no thread been build return -1 */
		if(process == null||process.thread == null) {
			System.out.println("Join error");
			return -1;
		}
		
		/** acquire join lock */
		process.joinLock.acquire();
		/** this process sleep until its childProcess finished and wake it */
		process.joinCondition.sleep();
		/** release join lock */
		process.joinLock.release();
		
		byte[] childstat = new byte[4];
		/** abtain the state of child process */
		Lib.bytesFromInt(childstat, 0, process.status);
		/** write child process's state to it's memeory */
		int numWriteByte = writeVirtualMemory(statusAddress, childstat);
		/** if the child process exit normally */
		if(process.normalExit && numWriteByte == 4)
			return 1;
		
		return 0;
	}
	
	/**
     * HandleCreate
     */
	private int handleCreate(int fileAddress) {
		
		String filename = readVirtualMemoryString(fileAddress,256);
		// Is this fileName valid?
		if (filename == null || filename.length() == 0) {
            System.out.println("Invalid filename for create()");
            return -1;
		}
		
		int fileDescriptor=findEmpty();
		if (fileDescriptor == -1) {
            System.out.println("we are out of file descriptors.");
            return -1;
        } else {
            final OpenFile file = ThreadedKernel.fileSystem.open(filename,true);
            // was the file successfully created?
            if (file == null) {
                System.out.println("Unable to create file: "+ filename);
            }
            // Add this openFile to OpenFiles
            openfile[fileDescriptor] = file;
            // return the new fileDescriptor
            return fileDescriptor;
        }
	}
	
	/**
     * case syscallOpen : return handleOpen(a0);
     */
	private int handleOpen (int fileAddress) {
		String filename = readVirtualMemoryString(fileAddress,256);
		
		if(filename == null)
			return -1;
		
		int fileDescriptor = findEmpty();

		if (fileDescriptor == -1) {
			System.out.println("we are out of file descriptors.");
            return -1;
        } else {
            OpenFile file = ThreadedKernel.fileSystem.open(filename, false);
            if (file == null) {
                System.out.println("Unable to open file: " + filename);
                // System.out.println("Create a file: "+filename);
				// this.handleCreate(fileAddress);
				/** shouldn't create file here */
                return -1;
            } else {
                // Add this openFile to openFiles
                openfile[fileDescriptor] = file;
                System.out.println("handleOpen - return fileDescriptor="+fileDescriptor);
                // return the new fileDescriptor
		        return fileDescriptor;
            }
            
        }
	}
	
	/**
     *  Handle the read() system call
     */
	private int handleRead (int fileDescriptor,int bufferAddress,int  length) {

        // check length is a valid arg
		if (length < 0) {
			System.out.println("Count must be non-negative for read()");
			return -1;
		}
		// Make sure FD is valid
		if (fileDescriptor < 0 || fileDescriptor >= openfile.length || openfile[fileDescriptor] == null) {
			System.out.println("Invalid file descriptor passed to read()");
			return -1;
		}
        byte tmp[] = new byte[length];
        int numBytesRead = openfile[fileDescriptor].read(tmp, 0, length);
        int numBytesWritten = writeVirtualMemory(bufferAddress, tmp, 0, numBytesRead);
	
		if (numBytesRead != numBytesWritten) {
			System.out.println("Expected to write " + numBytesRead + " bytes into virtual memory, but actually wrote "
					+ numBytesWritten);
			return -1;
		}

		return numBytesRead;
	}
	
	/**
     *  Handle syscall write()
     */
	private int handleWrite(int fileDescriptor,int bufferAddress,int  length) {
		// check if length is a valid arg
		if (length < 0) {
			System.out.println("Count must be non-negative for write().");
			return -1;
		}
		// Make sure FD is valid
		if (fileDescriptor < 0 || fileDescriptor >= openfile.length || openfile[fileDescriptor] == null) {
			System.out.println("Invalid file descriptor passed to write()");
			return -1;
		}

		OpenFile file = openfile[fileDescriptor];

		byte[] tmp = new byte[length];
		int numBytesToWrite = readVirtualMemory(bufferAddress, tmp);

		if (numBytesToWrite != length) {
			System.out.println(
					"Expected to read " + length + " bytes from virtual memory, but actually wrote " + numBytesToWrite);
			return -1;
		}

		int writeNumber = openfile[fileDescriptor].write(tmp, 0, length);
	
		return writeNumber;
	}
	/**
     * case syscallClose : return handleClose(a0);
     */
	private int handleClose (int fileDescriptor) {
		if(fileDescriptor <0 || fileDescriptor >= MAX_OPEN_FILES_PER_PROCESS) {
            System.out.println("Invalid file descriptor passed to close()");
            return -1;
		}
		// abtain openfile
		OpenFile file = openfile[fileDescriptor];
		// check that the file is still open
		if (file == null) {
			System.out.println("There is no open file with the given file descriptors passed to close()");
			return -1;
		}
		// close the file
		openfile[fileDescriptor] = null;
		file.close();
 
	    return 0;
	}

	/**
      *  Handle the unlink() system call
      */
	private int handleUnlink (int fileAddress) {
		System.out.println("handleUnlink()");
		
		String filename = readVirtualMemoryString(fileAddress, MAX_FILE_LENGTH);
		
		if(filename == null) {
			System.out.println("File doesn't exist, no need to unlink.");
			return -1;
		}
			
		if(ThreadedKernel.fileSystem.remove(filename))
			return 0;
		else 
			return -1;
	}
	
	/**
	  *  Find empty position at table - openfile
	  *  return fileDescriptor
      */
	private int findEmpty ()
	{
		for(int i = 0;i < 16; i++)
	 	{
			if(openfile[i] == null)
				return i;
	 	}
		return -1;
	}
	
	
	/**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case Processor.exceptionSyscall:
				int result = handleSyscall(processor.readRegister(Processor.regV0),
						processor.readRegister(Processor.regA0),
						processor.readRegister(Processor.regA1),
						processor.readRegister(Processor.regA2),
						processor.readRegister(Processor.regA3));
				processor.writeRegister(Processor.regV0, result);
				processor.advancePC();
				break;

			default:
				Lib.debug(dbgProcess, "Unexpected exception: "
						+ Processor.exceptionNames[cause]);
				Lib.assertNotReached("Unexpected exception");
		}
	}
	

	
	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';

	/** Custom Defined Parameters */

	/** The files opened by this process */
	OpenFile openfile[] = new OpenFile[16];
	/** The status of this process */
	public int status = 0;
	public boolean normalExit = false;

	private static final int MAX_OPEN_FILES_PER_PROCESS = 16;
	private static final int MAX_FILENAME_LENGTH = 256;
	private static final int MAX_FILE_LENGTH = 1024;

	/** thread associated with this process */
	public UThread thread = null;
	/** process id */
	private int pid = 0;
	/** parent process id */
	public UserProcess  ppid = null;
	
	/** Child process */
	public LinkedList<UserProcess> childProcess = new LinkedList();
	
	/** counter of process */
	private static int processCounter = 0;
	/** counter of running process  */
	private static int processOn = 0;
	
	/** join() wait lock */
	private Lock joinLock = new Lock();
	/** join() condition */
	private Condition joinCondition = new Condition(joinLock);

}
