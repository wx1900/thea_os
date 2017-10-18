# nachos 

edit - 10.11

3. (10%, 40 lines)

   -  Complete the implementation of the `Alarm` class, by implementing the `waitUntil(long x)` method. 
   -  A thread calls `waitUntil` to suspend its own execution until time has advanced to **at least now + x**. 
   -  This is useful for threads that operate in real-time, for example, for blinking the cursor once per second. 
   -  There is no requirement that threads start running immediately after waking up; just put them on the ready queue *in the timer interrupt handler* after they have waited for at least the right amount of time. 
   -  Do not fork any additional threads to implement `waitUntil()`; **you need only modify `waitUntil()` and the timer interrupt handler.**  
   -  `waitUntil` is not limited to one thread; **any number of threads** may call it and be suspended at any one time.      <u>put a waiting queue in this class</u>

   ​

   > 参考：
   >
   > - https://github.com/viturena/nachos/blob/master/threads/Alarm.java
   > - https://www.javatpoint.com/java-treemap

4. (20%, 40 lines)

   -  Implement synchronous send and receive of one word messages 
     - using condition variables (don't use semaphores!). 
   -  Implement the Communicator class with operations, void speak(int word) and int listen().`speak()` atomically waits until `listen()` is called on the same `Communicator` object, and then transfers the word over to `listen()`. Once the transfer is made, both can return. Similarly, `listen()` waits until `speak()` is called, at which point the transfer is made, and both can return (`listen()` returns the word). This means that **neither thread may return from `listen()` or `speak()` until the word transfer has been made. **
   -  Your solution should work even if there are multiple `speak`ers and `listen`ers for the same `Communicator` (note: this is equivalent to a zero-length bounded buffer; since the buffer has no room, the producer and consumer must interact directly, requiring that they wait for one another). Each communicator should only use exactly **one** lock. If you're using more than one lock, you're making things too complicated.

   > 参考：[deep_keng](http://blog.csdn.net/deep_kang/article/category/6570538)

5. (35%, 125 lines)

   - Implement priority scheduling in Nachos by completing the PriorityScheduler class. 
     - Priority scheduling is a key building block in real-time systems.
     - Note that in order to use your priority scheduler, you will need to change a line in nachos.conf  that specifies the scheduler class to use. The ThreadedKernel.scheduler  key is initially equal to achos.threads.RoundRobinScheduler. You need to change this to nachos.threads.PriorityScheduler  when you're ready to run Nachos with priority scheduling.
     - Note that all scheduler classes extend the abstract class `nachos.threads.Scheduler`.
     -  You must implement the methods `getPriority()`, `getEffectivePriority()`, and `setPriority()`. 
     - You may optionally also implement `increasePriority()` and `decreasePriority()` (these are not required).
     -  In choosing which thread to dequeue, the scheduler should always choose a thread of the highest effective priority. If multiple threads with the same highest priority are waiting, the scheduler should choose the one that has been waiting in the queue the longest.
   - An issue with priority scheduling is *priority inversion*.
     -  If a high priority thread needs to wait for a low priority thread (for instance, for a lock held by a low priority thread), and another high priority thread is on the ready list, then the high priority thread will never get the CPU because the low priority thread will not get any CPU time.
     - A partial fix for this problem is to have the waiting thread *donate* its priority to the low priority thread while it is holding the lock.
     - Implement the priority scheduler so that it donates priority, where possible. Be sure to implement `Scheduler.getEffectivePriority()`, which returns the priority of a thread after taking into account all the donations it is receiving.
     - Note that while solving the priority donation problem, you will find a point where you can easily calculate the effective priority for a thread, but this calculation takes a long time. To receive full credit for the design aspect of this project, you need to speed this up by caching the effective priority and only recalculating a thread's effective priority when it is possible for it to change.
     - It is important that you do not break the abstraction barriers while doing this part -- the Lock class does not need to be modified. Priority donation should be accomplished by creating a subclass of ThreadQueue that will accomplish priority donation when used with the existing Lock class, and still work correctly when used with the existing Semaphore and Condition classes. Priority should also be donated through thread joins.

   Priority Donation Implementation Details:
   1) A thread's effective priority is calculated by taking the max of the donor's and the recipient's priority. If thread A with priority 4 donates to thread B with priority 2, then thread B's effective priority is now 4. Note that thread A's priority is also still 4. A thread that donates priority to another thread does not lose any of its own priority. For these reasons, the term "priority inheritance" is in many ways a more appropriate name than the term "priority donation".
   2) Priority donation is transitive. If thread A donates to thread B and then thread B donates to thread C, thread B will be donating its new effective priority (which it received from thread A) to thread C.

