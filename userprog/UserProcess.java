package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;

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
    /**
     * Allocate a new process.
     */
    public UserProcess() {

        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages]; // size = numPhysPages just envelope of vpn&ppn&... 
        for (int i = 0; i < numPhysPages; i++) {
            // create default pageTable vpn = ppn
            pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
        }

        // assume logical address equal to physical address
        // so logical page number equal to physical page number

        // page structure:
        // vpn the virtual page number
        // ppn the physical page number
        // valid the valid bit
        // readOnly the read-only bit
        // used the used bit
        // dirty the dirty bit

        // Processor.translate() implement the translation from virtual and reality
        // UserProcess.restoreState() pass pageTable to CPU
        // UThread.restoreState()-->UserProcess.restoreState()
        // UThread.runProgram()-->UserProcess.restoreState()
        this.openFiles = new OpenFile[MAX_OPEN_FILES_PER_PROCESS];
        this.openFiles[0] = UserKernel.console.openForReading();
        this.openFiles[1] = UserKernel.console.openForWriting();
        // this.processID = processesCreated++;
        this.pid = processesCreated++;
        this.childrenCreated = new HashSet<Integer>();
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	       return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
      	if (!load(name, args)) // load executable file into this progress
      	    return false;
        System.out.println("execute() - load Successfully!");

      	// new UThread(this).setName(name).fork();
      	UThread UT = new UThread(this); // set the execute object to UThread.runProgram()
        UT.setName(name);
        UT.fork();
        // System.out.println("execute() - fork Sucessfully!");
        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
        // finished in UThread
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
    public String readVirtualMemoryString(int vaddr, int maxLength) {
      	Lib.assertTrue(maxLength >= 0);

      	byte[] bytes = new byte[maxLength+1];

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
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
      	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
        // from thinkhy -- modified by wx
        Processor processor = Machine.processor();
      	byte[] memory = Machine.processor().getMemory(); // return the mainmemory an array

        // calculate virtual page number from the virtual address
        int vpn = processor.pageFromAddress(vaddr);  // return virtual page based on virtual address
        int addressOffset= processor.offsetFromAddress(vaddr); // return addressOffset based on virtual address
        TranslationEntry entry = null;  
        // class <TransplationEntry> is just a envelope of (vpn,ppn,valid,readOnly,used,dirty)
        entry = pageTable[vpn]; // get entry 
        entry.used = true;      // 
        int ppn = entry.ppn;    // return ppn (default as ppn = vpn)
        int paddr = (ppn*pageSize) + addressOffset; 
        // calculate physicall address this just looks like vaddr why ??
        // the virtual_page_size <> physical_page_size
        // and the virtual pagetale size is bigger
        // but it really used can't larger than the physical space?
        // check if physical page number is out of range
        if (ppn < 0 || ppn >= processor.getNumPhysPages()) {
            return 0;
        }
        
        // ~
      	// for now, just assume that virtual addresses equal physical addresses
    	// if (vaddr < 0 || vaddr >= memory.length)
      	//      return 0;
    
      	int amount = Math.min(length, memory.length-paddr);
       	System.arraycopy(memory, paddr, data, offset, amount);
        // offset = 0 ; 
        // vaddr is the starting position means the virtual memory is an array 
        // so  ' mainMemory = new byte[pageSize * numPhysPages]; '
        // public static void arraycopy (Object src, int srcPos, Object dest, int destPos, int length);
            // src - the source array
            // srcPos - starting position in the source array
            // dest - the destination array
            // destPos - starting position in the destination data
            // length - the number of array elements to be copied
        return amount;
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
    public int writeVirtualMemory(int vaddr, byte[] data) {
	       return writeVirtualMemory(vaddr, data, 0, data.length);
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
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
      	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
        
        // from thinkhy
        Processor processor = Machine.processor();
      	byte[] memory = Machine.processor().getMemory();
        // calculate virtual page number from the virtual address
        int vpn = processor.pageFromAddress(vaddr);
        int addressOffset = processor.offsetFromAddress(vaddr);

        TranslationEntry entry = null;
        entry = pageTable[vpn];
        entry.used = true;
        entry.dirty = true; // write - set dirty = true
        int ppn = entry.ppn;
        int paddr = (ppn*pageSize) + addressOffset;

        if(entry.readOnly) {
            return 0;
        }
        if(ppn < 0 || ppn >= processor.getNumPhysPages()) {
            return 0;
        }

        // ~
        // for now, just assume that virtual addresses equal physical addresses
        // if (vaddr < 0 || vaddr >= memory.length)
        //      return 0;
    
      	int amount = Math.min(length, memory.length-paddr);
     	System.arraycopy(data, offset, memory, paddr, amount);
     
       	return amount;
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
    private boolean load(String name, String[] args) {
        // this function is a little complex not understand for now -wx
      	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
        // proj2 fileSystem = Machine.StubFileSystem
        // halt.coff, sh.coff
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
            // if coff legal
            // 
      	}
      	catch (EOFException e) {
      	    executable.close();
      	    Lib.debug(dbgProcess, "\tcoff load failed");
      	    return false;
      	}

      	// make sure the sections are contiguous and start at page 0
      	numPages = 0; // each segment's first page in the pagetable
      	for (int s=0; s<coff.getNumSections(); s++) {
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
      	for (int i=0; i<args.length; i++) {
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
      	initialSP = numPages*pageSize;

      	// and finally reserve 1 page for arguments
      	numPages++;

      	if (!loadSections())
      	    return false;

      	// store arguments in last page
      	int entryOffset = (numPages-1)*pageSize;
      	int stringOffset = entryOffset + args.length*4;

      	this.argc = args.length;
      	this.argv = entryOffset;

      	for (int i=0; i<argv.length; i++) {
      	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
      	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
      	    entryOffset += 4;
      	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
      		       argv[i].length);
      	    stringOffset += argv[i].length;
      	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
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
      	if (numPages > Machine.processor().getNumPhysPages()) {
      	    coff.close();
      	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
      	    return false;
      	}

      	// load sections
        System.out.println("initialzing section ");
      	for (int s=0; s<coff.getNumSections(); s++) {
            // every sections in coff file
      	    CoffSection section = coff.getSection(s);
            
      	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
      		      + " section (" + section.getLength() + " pages)");

      	    for (int i=0; i<section.getLength(); i++) {
                // move every pages in section to nachos 
      		    int vpn = section.getFirstVPN()+i;
                // from thinkhy
                // translate virtual page number from phisical page number
                TranslationEntry entry = pageTable[vpn];
                entry.readOnly = section.isReadOnly();
                int ppn = entry.ppn;
                section.loadPage(i, ppn);
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
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        // from thikhy
        for (int i = 0; i < numPages; i++) {
            UserKernel.addFreePage(pageTable[i].ppn);
            pageTable[i].valid = false;
        }
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
      	Processor processor = Machine.processor();

      	// by default, everything's 0
      	for (int i=0; i<processor.numUserRegisters; i++)
      	    processor.writeRegister(i, 0);

      	// initialize PC and SP according
      	processor.writeRegister(Processor.regPC, initialPC);
      	processor.writeRegister(Processor.regSP, initialSP);

      	// initialize the first two argument registers to argc and argv
      	processor.writeRegister(Processor.regA0, argc);
      	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        System.out.println("handle - Hanlt.");
        if (pid == 0) { Machine.halt(); }
        Lib.assertNotReached("Machine.halt() did not halt machine!");
      	return 0;
    }
    /**
     *  Hanlde the exec system call
     */

    private int handleExec(int a0,int a1, int a2) {
        // from han
        int pFile = a0;
        int argc = a1;
        int pArgv = a2; 
        if (inVaddressSpace(pFile) && argc >= 0) {
			String fileName = readVirtualMemoryString(pFile, MAX_FILENAME_LENGTH);
			byte[] argvBytes = new byte[argc * sizeOfInt];
			// read bytes of the argv array
			if (readVirtualMemory(pArgv, argvBytes, 0, argvBytes.length) == argvBytes.length) {
				// concatenate bytes into an array of addresses
				int[] argvAddrs = new int[argc];
				for (int i = 0; i < (argc * sizeOfInt); i += sizeOfInt) {
					argvAddrs[i / sizeOfInt] = Lib.bytesToInt(argvBytes, i);
				}
				// read the strings from virtual memory
				String[] argvStrings = new String[argc];
				int remainingBytes = Processor.pageSize;
				for (int i = 0; i < argc; ++i) {
					argvStrings[i] = readVirtualMemoryString(argvAddrs[i], Math.min(remainingBytes, 256));
					if (argvStrings[i] == null || argvStrings[i].length() > remainingBytes) {
						return ERROR; // arguments do not fit on one page
					}
					remainingBytes -= argvStrings[i].length();
				}
				UserProcess childProcess = UserProcess.newUserProcess();
				if (childProcess.execute(fileName, argvStrings)) { // tries to load and run program 
					childrenCreated.add(childProcess.pid);
                    System.out.println("handleExec() - chilidProcess.pid="+childProcess.pid);
					return childProcess.pid;
				}
			}
		}
		return ERROR;
    }
    
    /**
     *  Handle the exit() system call
     *  it's thread should be terminated immediatedly
     *  clean up process and state
     *  exit abnormally if kernel kills it
     *  The last process to call exit() should cause the machine to halt by calling Kernel.kernel.terminate()
     */
    private void handleExit(int status){    
        // from han & thinkhy
        coff.close();
        // set any children of the process no longer have a parent process
        while (children != null && !children.isEmpty()) {
            int childPid = children.removeFirst();
            UserProcess childProcess = UserKernel.getProcessByID(childPid);
            childProcess.ppid = 1; //ROOT pid = 1
        }
        
        // close file
        for (OpenFile file: openFiles) {
            if (file != null)
                file.close();
        }

        // unloadSections
        this.unloadSections();
        System.out.println("handle exit");
        // terminate kernel -- the last process
        if (pidThreadMap.size() == 1) {
            System.out.println("handle - exit pidThreadMap.size = 1");
            Kernel.kernel.terminate();
            // Machine.halt();
        } else {
            // remove processId from pidThreadMap
            pidThreadMap.remove(this.pid);
            processStatus = status;
            exitSuccess = true;
            UThread.finish();
        }
        Lib.assertNotReached();
    }
     /**
       *  Handle the unlink() system call
       */
    private int handleUnlink(int a0){
        // from thinkhy
        Lib.debug(dbgProcess, "handleUnlink()");
        System.out.println("handleUnlink()");
        boolean retval = true;

        // a0 is address of filename
        String fileName = readVirtualMemoryString(a0, MAX_FILE_LENGTH);
        
        Lib.debug(dbgProcess, "fileName: " + fileName);
        System.out.println("fileName : "+fileName);
        // int fileHandle = findFileDescriptorByName(fileName);
        for (int i = 0; i < this.openFiles.length; ++i) {
            if(this.openFiles[i] != null && this.openFiles[i].getName().equals(fileName))
                retval = ThreadedKernel.fileSystem.remove(fileName);
        }

        // if(fileHandle < 0) {
        //     // invoke open through stubFilesystem
        //     retval = UserKernel.fileSystem.remove(fd[fileHandle].fileName);
        // }else {
        //     fd[fileHandle].toRemove = true;
        // }
    
        return retval ? 0 : -1;
    }
    /**
     *  Handle the open() system call
     */
    private int handleOpen(int a0) {

        // from han
        
        int pName = a0;
        System.out.println("open - pName file address = "+a0); 
		final String fileName = readVirtualMemoryString(pName, MAX_FILENAME_LENGTH);
		if (fileName == null || fileName.length() == 0) {
			System.out.println("Invalid filename for open()");
			return -1;
		}
        System.out.println("open filename = "+fileName);
        final int fileDescriptor = this.getUnusedFileDescriptor();
        System.out.println("open - get unused file descriptor = "+fileDescriptor);
        if (fileDescriptor == -1) {
            return -1;
        } else {
            System.out.println("open file - filename = "+fileName);
            OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
            System.out.println("open file - file = "+(file!=null));
            if (file == null) {
                System.out.println("Unable to open file: " + fileName);
                System.out.println("Create a file: "+fileName);
                this.handleCreate(a0);
                // return -1;
                return 0;
            } else {
                // Add this openFile to openFiles
                this.openFiles[fileDescriptor] = file;
                System.out.println("handleOpen - return fileDescriptor="+fileDescriptor);
                // return the new fileDescriptor
		        return fileDescriptor;
            }
            
        }

    }
    /**
     *  Handle the read() system call
     */
    // a0:filename, a1:buf address, a2:buf size
    private int handleRead(int a0,int a1, int a2) {
        // from han
        int fileDescriptor = a0;
        int pBuffer = a1;
        int count = a2;
        // check count is a valid arg
		if (count < 0) {
			System.out.println("Count must be non-negative for read()");
			return -1;
		}
		// Make sure FD is valid
		if (fileDescriptor < 0 || fileDescriptor >= this.openFiles.length || openFiles[fileDescriptor] == null) {
			System.out.println("Invalid file descriptor passed to read()");
			return -1;
		}

		final OpenFile file = this.openFiles[fileDescriptor];

		final byte[] tmp = new byte[count];
		final int numBytesRead = file.read(tmp, 0, count);
		final int numBytesWritten = writeVirtualMemory(pBuffer, tmp, 0, numBytesRead);

		if (numBytesRead != numBytesWritten) {
			System.out.println("Expected to write " + numBytesRead + " bytes into virtual memory, but actually wrote "
					+ numBytesWritten);
			return -1;
		}

		return numBytesRead;
    }
    /**
     * handleCreate
     */
    private int handleCreate(final int a0) {
        final String fileName = readVirtualMemoryString(a0, MAX_FILENAME_LENGTH);
        // Is this fileName valid?
        if (fileName == null || fileName.length() == 0) {
            System.out.println("Invalid filename for create()");
            return -1;
        }
        // Do we already have a file descriptor for a file with the same name?
        for (int i = 0; i < this.openFiles.length; ++i) {
            if(this.openFiles[i] != null && this.openFiles[i].getName().equals(fileName))
                return i;
        }
        // find an empty slot in the file descriptor table
        final int fileDescriptor = this.getUnusedFileDescriptor();
        // are we out of file descritptors?
        if (fileDescriptor == -1) {
            System.out.println("we are out of file descriptors.");
            return -1;
        } else {
            final OpenFile file = ThreadedKernel.fileSystem.open(fileName,true);
            // was the file successfully created?
            if (file == null) {
                System.out.println("Unable to create file: "+ fileName);
            }
            // Add this openFile to OpenFiles
            this.openFiles[fileDescriptor] = file;
            // return the new fileDescriptor
            return fileDescriptor;
        }
       
    }
    /**
     * case syscallClose : return handleClose(a0);
     */
    // from han
    private int handleClose(final int fileDescriptor) {
        if(fileDescriptor <0 || fileDescriptor >= MAX_OPEN_FILES_PER_PROCESS) {
            System.out.println("Invalid file descriptor passed to close()");
            return -1;
        }
        // abtain openfile
        OpenFile file = openFiles[fileDescriptor];
        // check that the file is still open
        if(file == null) {
           //  System.out.println("There is no open file with the given file
           //   descriptors passed to close()");
            System.out.println("no open file");
            return -1;
        }
        // close the file
        openFiles[fileDescriptor] = null;
        file.close();

        //  return SUCCESS;
        return 0;
    }

    /**
     *  Handle syscall write()
     */
    private int handleWrite (int a0, int a1,int a2) {
        // from han
        int fileDescriptor = a0;
        int pBuffer = a1;
        int count = a2;
        // check if count is a valid arg
		if (count < 0) {
			System.out.println("Count must be non-negative for write().");
			return -1;
		}
		// Make sure FD is valid
		if (fileDescriptor < 0 || fileDescriptor >= this.openFiles.length || openFiles[fileDescriptor] == null) {
			System.out.println("Invalid file descriptor passed to write()");
			return -1;
		}

		final OpenFile file = this.openFiles[fileDescriptor];

		final byte[] tmp = new byte[count];
		final int numBytesToWrite = readVirtualMemory(pBuffer, tmp);

		if (numBytesToWrite != count) {
			System.out.println(
					"Expected to read " + count + " bytes from virtual memory, but actually wrote " + numBytesToWrite);
			return -1;
		}

		// TODO(amidvidy): need to handle the case that file is actually an
		// instance of SynchConsole.file()...
		return file.write(tmp, 0, numBytesToWrite);
    }
    //  from han
    private int getUnusedFileDescriptor(){
        // implement this
        for (int i = 0; i < this.openFiles.length; ++i) {
			if (this.openFiles[i] == null) {
				return i;
			}
		}
		System.out.println("No file descriptors.");
		return -1;
    }

    // from thikhy
    // Suspend execution of the current process until the child process specified
    // by the processID argument has exited. If the child has already exited by the
    // time of the call, returns immediately. When the current process resumes, it
    // disowns the child process, so that join() cannot be used on that process again.

    // only parents'process can join it
    private int handleJoin(int childpid, int adrStatus) {
        Lib.debug(dbgProcess, "handleJoin()");
        // remove child's pid in parent's children list
        boolean childFlag = false;
        int tmp = 0;
        Iterator<Integer> it = this.children.iterator();
        while(it.hasNext()) {
            tmp = it.next();
            if(tmp == childpid) {
                it.remove();
                childFlag = true;
                break;
            }
        }
        if(childFlag == false) {
            Lib.debug(dbgProcess, "[UserProcess.handleJoin]"
            +"Error: process "+ this.pid+"doesn't have a child with pid = "
            + childpid);
            return -1;
        }
        UserProcess childProcess = UserKernel.getProcessByID(childpid); 
        if(childProcess == null) {
            Lib.debug(dbgProcess, "[UserProcess.handleJoin]"
            + "Error: the child "+ childpid +
            " has already joined by the time of the call");
            return -2;
        }
        childProcess.thread.join();
        UserKernel.unregisterProcess(childpid);
        byte temp[] = new byte[4];
        temp = Lib.bytesFromInt(childProcess.processStatus);
        int cntBytes = writeVirtualMemory(adrStatus, temp);
        if (cntBytes != 4) 
            return 1;
        else
            return 0;
           
           
        // return -1;
    }

    // from han
    private boolean inVaddressSpace(int addr) {
        return (addr>=0 && addr < pageTable.length*pageSize);
    }
    private boolean inPhysAddressSpace(int addr) {
        return (addr>=0 || addr<Machine.processor().getMemory().length);
    }

    // syscall
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
        Lib.debug(dbgProcess, "handleSyscall " + syscall);
        switch (syscall) {
            case syscallHalt:
                return handleHalt(); // already implement
            case syscallExit:
                System.out.println("syscallExit");
                handleExit(a0);
                return 0;
            case syscallExec:
                System.out.println("syscallExec");
                return handleExec(a0,a1,a2);
            case syscallJoin:
                System.out.println("syscallJoin");
                return handleJoin(a0,a1);
            case syscallCreate: //proj2.1
                System.out.println("syscallCreate");
                // the file argument is filename, hence a0
                return handleCreate(a0); // create
            case syscallOpen: //proj2.1
                System.out.println("syscallOpen");
                // the file argument is filename, hence a0
                return handleOpen(a0);
            case syscallRead: //proj2.1
                // System.out.println("syscallRead"); 轮循，不停执行 syscallRead
                // need three argument a0:filename, a1:buf address, a2:buf size
                return handleRead(a0,a1,a2);
            case syscallWrite: //proj2.1
                // System.out.println("syscallWrite");
                return handleWrite(a0,a1,a2);
            case syscallClose: //proj2.1
                System.out.println("syscallClose");
                return handleClose(a0);
            case syscallUnlink: //proj2.1
                System.out.println("syscallUnlink");
                handleUnlink(a0);
                break;
            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
      	}
      	return 0;
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
      				       processor.readRegister(Processor.regA3)
      				       );
      	    processor.writeRegister(Processor.regV0, result);
      	    processor.advancePC();
      	    break;

      	default:
      	    Lib.debug(dbgProcess, "Unexpected exception: " +
      		      Processor.exceptionNames[cause]);
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

    // from hints
    // private final OpenFile[] openFiles;
    private static final int MAX_OPEN_FILES_PER_PROCESS = 16;
    private static final int MAX_FILENAME_LENGTH = 256;
    private static final int MAX_FILE_LENGTH = 1024;
    private static final int ERROR = -1; // not necessary
    private static final int SUCCESS = 0;
    // private final int processID;
    private int pid; // process id
    private int ppid; // parent process's ID
    private final OpenFile[] openFiles;
    private static final int sizeOfInt = 4;
    private static HashSet<Integer> childrenCreated;
    public static int processesCreated = 0;
    private static Map<Integer, UThread> pidThreadMap = new HashMap<Integer, UThread>();
    private int processStatus;
    private boolean exitSuccess;
    private LinkedList<Integer> children = new LinkedList<Integer>(); // child process
    private UThread thread; // user thread that's associated with this process
}
