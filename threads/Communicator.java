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
    Condition listener;
    Condition speaker;

    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        // use only one lock for this
        lock = new Lock();
        speakerNum = 0;
        listenerNum = 0;
        words = new LinkedList<Integer>();
        listener = new Condition(lock);
        speaker = new Condition(lock);
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
        // add a speaker
        speakerNum++;

        while (!words.isEmpty() || listenerNum == 0)
            speaker.sleep();
        // speaker says a word
        words.addLast(word);
       
        // wake up all listeners
        listener.wakeAll();

        speakerNum--;
        
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
        // increase the number of listener by one
        listenerNum++;
        while (words.isEmpty()) {
            speaker.wakeAll();
            listener.sleep();
        }
        // listener receives the word
        int word = words.removeFirst();
 
        // decrease listener number
        listenerNum--;

        lock.release();
        
        Machine.interrupt().restore(preStatus);
        
        // return words.removeLast();
	    return word;
    }

    private static class Speaker implements Runnable {

        private Communicator c;
        private int base = 0;
        
        Speaker (Communicator c, int base) {
            this.c = c;
            this.base = base;
        }
        
        public void run() {
            for (int i = base; i < 5+base; ++i) {
                // System.out.println("speaker speaking *try to put* " + i);
                c.speak(i);
                System.out.println(KThread.currentThread().getName() + " spoken,    word = " + i);
                // KThread.yield();
            }
            // c.speak(this.word);
        }
    }

    private static class Listener implements Runnable {
        Listener(Communicator c) {
            this.c = c;
        }
        public void run() {
            // int word = c.listen();
            for (int i = 0; i < 10; ++i) {
                // System.out.println("speaker speaking *try to put* " + i);
                int word = c.listen();
                System.out.println(KThread.currentThread().getName() + " listened, word = " + word);
                // KThread.yield();
            }
        }
        private Communicator c;
    }

    public static void selfTest() {

        System.out.println("Communicator-selfTest-begin");
        
        Communicator c = new Communicator();
       
        // fork a new thread to speak
        KThread speaker1 = new KThread(new Speaker(c, 0));
        speaker1.setName("Speaker1").fork();
        KThread.yield();
        KThread speaker2 = new KThread(new Speaker(c, 100));
        speaker2.setName("Speaker2").fork();
        KThread.yield();
        KThread listener1 = new KThread(new Listener(c));
        listener1.setName("listener1").fork();
        KThread.yield();
        listener1.join();
        speaker1.join();
        speaker2.join();
        
        // use main thread to listen
        /**
        for (int i = 0; i < 10; ++i) {
            // System.out.println("listener listening *try to get* " + i);
            int x = c.listen();
            System.out.println("listener listened, word = " + x);
            KThread.yield();
        }
        */
        System.out.println("Communicator-selfTest-finished");
    }
}
