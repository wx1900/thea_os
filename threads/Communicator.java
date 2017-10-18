package nachos.threads;
 
import nachos.machine.*;
import java.util.LinkedList;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {

    Lock lock;
    
    private int speakerNum;
    private int listenerNum;
    
    private LinkedList<Integer> words;
    
    // use condition2 works fine
    Condition2 listener;
    Condition2 speaker;

    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        // use only one lock for this
        lock = new Lock();
        speakerNum = 0;
        listenerNum = 0;
        words = new LinkedList<Integer>();
        listener = new Condition2(lock);
        speaker = new Condition2(lock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
        // disable interrupt
        boolean preStatus = Machine.interrupt().disable();
        
        // acuquire lock
        lock.acquire();

        // add word
        words.add(word);

        if(listenerNum == 0){
            speakerNum++;
            System.out.println("++++++++++++++No listener for now.  l=" + listenerNum + " s="+speakerNum);
            speaker.sleep();
            System.out.println("++++++++++++++after speaker.sleep.  l=" + listenerNum + " s="+speakerNum);
            listenerNum--;
        }else{
             speakerNum++;
             System.out.println("+++++++++++++++we have listener!    l=" + listenerNum + " s="+speakerNum);
             listener.wake();
             System.out.println("+++++++++++++++after wake listener. l=" + listenerNum + " s="+speakerNum);
             listenerNum--;
        }
        
        
        lock.release();

        Machine.interrupt().restore(preStatus);
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {

        boolean preStatus = Machine.interrupt().disable();

        lock.acquire();

        if(speakerNum==0){
            listenerNum++;
            System.out.println("--------------No speaker for now.   l=" + listenerNum + " s="+speakerNum);
            listener.sleep();
            System.out.println("--------------after listener.sleep. l=" + listenerNum + " s="+speakerNum);
            speakerNum--;
        }else{
            listenerNum++;
            System.out.println("---------------we have speaker!     l=" + listenerNum + " s="+speakerNum );
            speaker.wake();
            System.out.println("---------------after speaker.wake   l=" + listenerNum + " s="+speakerNum);
            speakerNum--;   
        }
        
        lock.release();
        
        Machine.interrupt().restore(preStatus);
        
        return words.removeLast();
	    // return 0;
    }

    private static class Speaker implements Runnable {

        private Communicator c;
        
        Speaker(Communicator c) {
            this.c = c;
        }
        
        public void run() {
            for (int i = 0; i < 10; ++i) {
                System.out.println("speaker speaking *try to put* " + i);
                c.speak(i);
                System.out.println("speaker spoken, word = " + i);
                KThread.yield();
            }
        }
    }
    public static void selfTest() {

        System.out.println("Communicator-selfTest-begin");
        
        Communicator c = new Communicator();
       
        // fork a new thread to speak
        new KThread(new Speaker(c)).setName("Speaker").fork();
        
        // use main thread to listen
        for (int i = 0; i < 10; ++i) {
            System.out.println("listener listening *try to get* " + i);
            int x = c.listen();
            System.out.println("listener listened, word = " + x);
            KThread.yield();
        }

        System.out.println("Communicator-selfTest-finished");
    }
}
