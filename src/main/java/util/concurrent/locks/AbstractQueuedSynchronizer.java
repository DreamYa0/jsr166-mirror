/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent.locks;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import sun.misc.Unsafe;

/**
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic <tt>int</tt> value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other public methods in this class
 * carry out all queuing and blocking mechanics using an internal
 * specialized FIFO queue. Subclasses can maintain other state fields,
 * but only the <tt>int</tt> value manipulated using methods {@link
 * #getState}, {@link #setState} and {@link #compareAndSetState} is
 * tracked with respect to synchronization.
 *
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties of 
 * their enclosing class.  
 * <tt>AbstractQueuedSynchronizer</tt> does not
 * implement any synchronization interface.  Instead it defines
 * methods such as {@link #acquireExclusiveUninterruptibly} that can
 * be invoked as appropriate by concrete locks and related
 * synchronizers to implement their public methods. Note that this
 * class does not directly provide non-blocking &quot;trylock&quot; forms, 
 * since the subclass methods for querying and modifying the state can be 
 * used for these purposes, and can be made public by the subclass when 
 * needed.
 *
 * <p>This class supports either or both <em>exclusive</em> and
 * <em>shared</em> modes. When acquired in exclusive mode, attempted
 * acquires by other threads cannot succeed. Shared mode acquires may
 * (but need not) succeed by multiple threads. This class does not
 * &quot;understand&quot; these differences except in the mechanical
 * sense that when a shared mode acquire succeeds, the next waiting
 * thread (if one exists) must also determine whether it can acquire
 * as well. Threads waiting in the different modes share the same FIFO
 * queue. Subclasses that support only exclusive or only shared modes
 * need not redefine the methods for the unused mode.
 *
 * <p>This class defines a nested {@link Condition} class that can be
 * used with subclasses for which method {@link #releaseExclusive}
 * invoked with the current state value fully releases this object, and
 * {@link #acquireExclusiveUninterruptibly}, given this saved state
 * value, eventually restores this object to its previous acquired state. 
 * No <tt>AbstractQueuedSynchronizer</tt> method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * detailed behavior of {@link ConditionObject} depends of course on
 * the semantics of its synchronizer implementation.
 * 
 * <p> This class provides instrumentation and monitoring methods such
 * as {@link #hasQueuedThreads}, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an <tt>AbstractQueuedSynchronizer</tt> for their
 * synchronization mechanics.
 *
 * <p> Serialization of this class stores only the underlying atomic
 * integer maintaining state. Typical subclasses requiring
 * serializability will define a <tt>readObject</tt> method that
 * restores this to a known initial state upon deserialization.
 *
 * <h3>Usage</h3>
 * <p> To use this class as the basis of a synchronizer
 * redefine the following methods, as applicable.
 * Each by default throws {@link UnsupportedOperationException}:
 *
 * <ul>
 * <li> {@link #tryAcquireExclusiveState}
 * <li> {@link #releaseExclusiveState}
 * <li> {@link #tryAcquireSharedState}
 * <li> {@link #releaseSharedState}
 * <li> {@link #checkConditionAccess}
 *</ul>
 *
 * Defining these methods is the <em>only</em> supported means of
 * using this class. All other methods are declared <tt>final</tt>
 * because they cannot be independently varied.
 *
 * <h3>Usage Examples</h3>
 *
 * <p>Here is a non-reentrant mutual exclusion
 * lock class that uses the value zero to represent the unlocked state,
 * and one to represent the locked state:
 *
 * <pre>
 * class Mutex implements Lock, java.io.Serializable {
 *
 *    // Our internal helper class
 *    private static class Sync extends AbstractQueuedSynchronizer {
 *       public boolean isLocked() { return getState() == 1; }
 *
 *       // acquire the lock if state is zero
 *       public boolean tryAcquireExclusiveState(boolean isQueued, int acquires) {
 *         assert acquires == 1; // Otherwise unused
 *         return compareAndSetState(0, 1);
 *       }
 *
 *       // release the lock by setting state to zero
 *       protected boolean releaseExclusiveState(int releases) {
 *         assert releases == 1; // Otherwise unused
 *         setState(0);
 *         return true;
 *       }
 *       
 *       // Allow condition use only when locked
 *       protected void checkConditionAccess(Thread thread, boolean waiting) {
 *         if (!isLocked()) throw new IllegalMonitorStateException();
 *       }
 *
 *       // provide a Condition for our lock
 *       Condition newCondition() { return new ConditionObject(); }
 *
 *       // deserialize properly
 *       private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
 *         s.defaultReadObject();
 *         setState(0); // reset to unlocked state
 *       }
 *    }
 *
 *    // Our sync object does all the hard work. We just forward our
 *    // &quot;acquire&quot; methods to its acquire methods
 *
 *    private final Sync sync = new Sync();
 *    public boolean tryLock() { 
 *       return sync.tryAcquireExclusiveState(false, 1);
 *    }
 *    public void lock() { 
 *       sync.acquireExclusiveUninterruptibly(1);
 *    }
 *    public void lockInterruptibly() throws InterruptedException { 
 *       sync.acquireExclusiveInterruptibly(1);
 *    }
 *    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
 *       return sync.acquireExclusiveTimed(1, unit.toNanos(timeout));
 *    }
 *    public void unlock() { sync.releaseExclusive(1); }
 *    public Condition newCondition() { return sync.newCondition(); }
 *    public boolean isLocked() { return sync.isLockedt(); }
 *    public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
 * }
 * </pre>
 *
 * <p> Here is a latch class that is like {@link CountDownLatch}
 * except that it only requires a single <tt>signal</tt> to
 * fire. Because a latch is non-exclusive, it uses the <tt>shared</tt>
 * acquire and release methods.
 *
 * <pre>
 * class BooleanLatch {
 *    private final Sync sync = new Sync();
 *    private static class Sync extends AbstractQueuedSynchronizer {
 *      boolean isSignalled() { return getState() != 0; }
 *
 *      protected int tryAcquireSharedState(boolean isQueued, int ignore) {
 *         return isSignalled()? 1 : -1;
 *      }
 *        
 *      protected boolean releaseSharedState(int ignore) {
 *         setState(1);
 *         return true;
 *      }
 *    }
 *
 *    public void signal() { sync.releaseShared(1); }
 *    public void await() throws InterruptedException {
 *        sync.acquireSharedInterruptibly(1);
 *    }
 *    public boolean isSignalled() { return sync.isSignalled(); }
 * }
 * </pre>
 * @since 1.5
 * @author Doug Lea
 * 
 */
public abstract class AbstractQueuedSynchronizer implements java.io.Serializable {
    /*
     *  General description and notes.
     *
     *  The basic idea, ignoring all sorts of things
     *  (modes, cancellation, timeouts, error checking etc) is:
     *    acquire:
     *      if (atomically set status) // fastpath
     *        return;
     *      node = create and enq a wait node;
     *      for (;;) {
     *        if (node is first on queue) {
     *          if (atomically set status) 
     *             deq(node);
     *             return;
     *           }
     *        }
     *        park(currentThread);
     *      }
     *
     *    release:
     *      atomically set status;
     *      if (is now fully released) {
     *         h = first node on queue;
     *         if (h != null) unpark(h's successor's thread);
     *      }
     *
     *  * The particular atomic actions needed to atomically set
     *    status can vary in subclasses.
     *
     *  * By default, contended acquires use a kind of "greedy" /
     *    "renouncement" / barging / convoy-avoidance strategy:
     *    When a lock is released, a waiting thread is signalled
     *    so that it can (re)contend for the lock. It might lose
     *    and thus need to rewait. This strategy has much higher
     *    throughput than "directed handoff" because it reduces
     *    blocking of running threads, but poorer fairness.  The
     *    wait queue is FIFO, but newly entering threads can barge
     *    ahead and grab lock before woken waiters, so acquires
     *    are not strictly FIFO, and transfer is not
     *    deterministically fair. It is probabilistically fair in
     *    the sense that earlier queued threads are allowed to
     *    recontend before later queued threads, and each
     *    recontention has an unbiased chance to succeed against
     *    any incoming barging threads.
     *
     * * "Fair" variants differ only in that they can disable barging
     *    in the fast path if there is contention (see the "isQueued"
     *    argument to state methods). There can be races in detecting
     *    contention, but it is still FIFO from a definable (although
     *    complicated to describe) single point, so qualifies as a
     *    FIFO lock.
     *
     * * While acquires never "spin" in the usual sense, they perform
     *    multiple test-and-test-and sets interspersed with other
     *    computations before blocking.  This gives most of the
     *    benefits of spins when locks etc are only briefly held, without
     *    most of the liabilities when they aren't.
     *
     * * The wait queue is a variant of a "CLH" (Craig, Landin, and
     *    Hagersten) lock. CLH locks are normally used for spinlocks.
     *    We instead use them for blocking synchronizers, but use the
     *    same basic tactic of holding some of the control information
     *    about a thread in the predecessor of its node.  A "status"
     *    field in each node keeps track of whether a thread is/should
     *    block.  A node is signalled when its predecessor releases
     *    the lock. Each node of the queue otherwise serves as a
     *    specific-notification-style monitor holding a single waiting
     *    thread. The status field does NOT control whether threads
     *    are granted locks though.  A thread may try to acquire lock
     *    if it is first in the queue. But being first does not
     *    guarantee success; it only gives the right to contend.  So
     *    the currently released contender thread may need to rewait.
     *
     *    To enqueue into a CLH lock, you atomically splice it in as new
     *    tail. To dequeue, you just set the head field.  
     *    
     *         +------+  prev +-----+       +-----+
     *    head |      | <---- |     | <---- |     |  tail
     *         +------+       +-----+       +-----+
     *
     *    Insertion into a CLH queue requires only a single atomic
     *    operation on "tail", so there is a simple atomic point of
     *    demarcation from unqueued to queued. Similarly, dequeing
     *    involves only updating the "head". However, it takes a bit
     *    more work for nodes to determine who their successors are,
     *    in part to deal with possible cancellation due to timeouts
     *    and interrupts.
     *
     *    The "prev" links (not used in original CLH locks), are mainly
     *    needed to handle cancellation. If a node is cancelled, its
     *    successor is (normally) relinked to a non-cancelled
     *    predecessor. For explanation of similar mechanics in the case
     *    of spin locks, see the papers by Scott & Scherer at
     *    http://www.cs.rochester.edu/u/scott/synchronization/
     *    
     *    We also use "next" links to implement blocking mechanics.
     *    The thread id for each node is kept in its node, so a
     *    predecessor signals the next node to wake up by traversing
     *    next link to determine which thread it is.  Determination of
     *    successor must avoid races with newly queued nodes to set
     *    the "next" fields of their predecessors.  This is solved
     *    when necessary by checking backwards from the atomically
     *    updated "tail" when a node's successor appears to be null.
     *    (Or, said differently, the next-links are an optimization
     *    so that we don't usually need a backward scan.)
     *
     *    Cancellation introduces some conservatism to the basic
     *    algorithms.  Since we must poll for cancellation of other
     *    nodes, we can miss noticing whether a cancelled node is
     *    ahead or behind us. This is dealt with by always unparking
     *    successors upon cancellation, allowing them to stabilize on
     *    a new predecessor.
     *  
     *  * CLH queues need a dummy header node to get started. But
     *    we don't create them on construction, because it would be wasted
     *    effort if there is never contention. Instead, the node
     *    is constructed and head and tail pointers are set upon first
     *    contention.
     *
     *  * Threads waiting on Conditions use the same nodes, but
     *    use an additional link. Conditions only need to link nodes
     *    in simple (non-concurrent) linked queues because they are
     *    only accessed when exclusively held.  Upon await, a node is
     *    inserted into a condition queue.  Upon signal, the node is
     *    transferred to the main queue.  A special value of status
     *    field is used to mark which queue a node is on.
     *
     * * All suspension and resumption of threads uses LockSupport
     *    park/unpark. 
     *
     *  * Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     *    Scherer and Michael Scott, along with members of JSR-166
     *    expert group, for helpful ideas, discussions, and critiques.
     */

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new <tt>AbstractQueuedSynchronizer</tt> instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() { }

    /**
     * Node class for threads waiting for acquires or conditions.  
     */
    static final class Node {
        /** waitStatus value to indicate thread has cancelled */
        static final int CANCELLED =  1;
        /** waitStatus value to indicate thread needs unparking */
        static final int SIGNAL    = -1;
        /** waitStatus value to indicate thread is waiting on condition */
        static final int CONDITION = -2;

        /**
         * Status field, taking on only the values:
         *   SIGNAL:     The successor of this node is (or will soon be) 
         *               blocked (via park), so the current node must 
         *               unpark its successor when it releases or 
         *               cancels.
         *   CANCELLED:  Node is cancelled due to timeout or interrupt
         *               Nodes never leave this state. In particular,
         *               a thread with cancelled node never again blocks.
         *   CONDITION:  Node is currently on a condition queue
         *               It will not be used as a sync queue node until
         *               transferred. (Use of this value here
         *               has nothing to do with the other uses
         *               of the field, but simplifies mechanics.)
         *   0:          None of the above
         *
         * The values are arranged numerically to simplify use.
         * Non-negative values mean that a node doesn't need to
         * signal. So, some code doesn't need to check for particular
         * values, just for sign.
         *
         * The field is initialized to 0 for normal sync nodes, and
         * CONDITION for condition nodes.  It is modified only using
         * CAS.
         */
        volatile int waitStatus;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking waitStatus. Assigned during enqueing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of successful acquire. A
         * cancelled thread never succeeds in acquiring, and a thread only
         * cancels itself, not any other node.
         */
        volatile Node prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon release. Assigned once during enqueuing,
         * and nulled out (for sake of GC) when no longer needed.
         * Upon cancellation, we do NOT adjust this field, but simply
         * traverse through next's until we hit a non-cancelled node,
         * (or null if at end of queue).  The enq operation does not
         * assign next field of a predecessor until after attachment,
         * so seeing a null next field does not necessarily mean that
         * node is at end of queue. However, if a next field appears
         * to be null, we can scan prev's from the tail to
         * double-check.
         */
        volatile Node next;

        /**
         * True if waiting in shared mode.  If so, successful acquires
         * cascade to wake up subsequent nodes.
         */
        final boolean shared;
        
        /** 
         * Link to next node waiting on condition.  Because condition
         * queues are accessed only when holding in exclusive mode, we
         * just need a simple linked queue to hold nodes while they
         * are waiting on conditions. They are then transferred to the
         * queue to re-acquire.
         */
        Node nextWaiter;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.  Note that this need
         * not be declared volatile since it is always accessed after
         * traversing volatile links, and written before writing
         * links.
         */
        Thread thread;

        Node(Thread thread, boolean shared) { 
            this.thread = thread; 
            this.shared = shared;
        }

        Node(Thread thread, boolean shared, int waitStatus) { 
            this.thread = thread; 
            this.shared = shared;
            this.waitStatus = waitStatus;
        }
    }

    /**
     * The synchronization state.
     */
    private volatile int state;

    /** 
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only by a thread upon acquiring.
     * If head exists, its node waitStatus is guaranteed not to be
     * CANCELLED.
     */
    private transient volatile Node head; 

    /** 
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile Node tail; 

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * directly implement. And while we are at it, we do the same for
     * other CASable fields (which could otherwise be done with atomic
     * field updaters).
     */
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("waitStatus"));
            
        } catch(Exception ex) { throw new Error(ex); }
    }

    private boolean compareAndSetHead(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, expect, update);
    }
    
    private boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    private boolean compareAndSetWaitStatus(Node node, int expect, int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a <tt>volatile</tt> read.
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * Set the value of synchronization state
     * This operation has memory semantics of a <tt>volatile</tt> read.
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * Atomically set synchronization state to the given updated value
     * if the current state value <tt>==</tt> the expected value.
     * This operation has memory semantics of a <tt>volatile</tt> read and write.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    protected final boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    /**
     * Attempt to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it if possible.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread until it is signalled by a release from
     * some other thread. With arguments <tt>(false, 1)</tt>, this can
     * be used to implement method {@link Lock#tryLock()}. <p>The
     * default implementation throws {@link
     * UnsupportedOperationException}
     *
     * @param isQueued if <tt>true</tt> the current thread is in the queue 
     * when making this call; if <tt>false</tt> the current thread is not
     * in the queue. Knowing whether the current thread is in the queue may
     * be needed when implementing a fairness policy. The calling acquire
     * method often has this information at hand and can save the need for
     * this method to interrogate the queue directly.
     * @param acquires the number of acquires requested. This value
     * is always the one passed to an acquire method,
     * or is the value saved on entry to a condition wait.
     * The value is otherwise uninterpreted and can represent anything
     * you like.
     * @return true if successful. Upon success, this object has been
     * acquired the requested number of times.
     * @throws IllegalMonitorStateException if acquiring would place
     * this synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryAcquireExclusiveState(boolean isQueued, int acquires) {
        throw new UnsupportedOperationException();
    }

    /**
     * Set the state to reflect a release in exclusive mode.
     * <p>This method is always invoked by the thread performing release.
     * <p>The default implementation throws 
     * {@link UnsupportedOperationException}
     * @param releases the number of releases. This value
     * is always the one passed to a release method,
     * or the current state value upon entry to a condition wait.
     * The value is otherwise uninterpreted and can represent anything
     * you like.
     * @return <tt>true</tt> if this object is now in a fully released state, 
     * so that any waiting threads may attempt to acquire; and <tt>false</tt>
     * otherwise.
     * @throws IllegalMonitorStateException if releasing would place
     * this synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if exclusive mode not supported
     */
    protected boolean releaseExclusiveState(int releases) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempt to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it if possible.  <p>This method is
     * always invoked by the thread performing acquire.  If this
     * method reports failure, the acquire method may queue the thread
     * until it is signalled by a release from some other thread.
     * With arguments <tt>(false, 1)</tt>, this can be used to
     * help implement method {@link Lock#tryLock()}. <p>The default
     * implementation throws {@link UnsupportedOperationException}
     *
     * @param isQueued if <tt>true</tt> the current thread is in the queue 
     * when making this call; if <tt>false</tt> the current thread is not
     * in the queue. Knowing whether the current thread is in the queue may
     * be needed when implementing a fairness policy. The calling acquire
     * method often has this information at hand and can save the need for
     * this method to interrogate the queue directly.
     * @param acquires the number of acquires requested. This value
     * is always the one passed to an acquire method,
     * or is the value saved on entry to a condition wait.
     * The value is otherwise uninterpreted and can represent anything
     * you like.
     * @return a negative value on failure, zero on exclusive success,
     * and a positive value if non-exclusively successful, in which
     * case a subsequent waiting thread must check
     * availability. (Support for three different return values
     * enables this method to be used in contexts where acquires only
     * sometimes act exclusively.)  Upon success, this object has been
     * acquired the requested number of times.
     * @throws IllegalMonitorStateException if acquiring would place
     * this synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if shared mode not supported
     */
    protected int tryAcquireSharedState(boolean isQueued, int acquires) {
        throw new UnsupportedOperationException();
    }

    /**
     * Set the state to reflect a release in shared mode.
     * <p>This method is always invoked by the thread performing release.
     * <p> The default implementation throws 
     * {@link UnsupportedOperationException}
     * @param releases the number of releases. This value
     * is always the one passed to a release method,
     * or the current state value upon entry to a condition wait.
     * The value is otherwise uninterpreted and can represent anything
     * you like.
     * @return <tt>true</tt> if this object is now in a fully released state, 
     * so that any waiting threads may attempt to acquire; and <tt>false</tt>
     * otherwise.
     * @throws IllegalMonitorStateException if releasing would place
     * this synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if shared mode not supported
     */
    protected boolean releaseSharedState(int releases) {
        throw new UnsupportedOperationException();
    }


    /**
     * Throws {@link IllegalMonitorStateException} if the given thread
     * should not access a {@link Condition} method. This method is
     * invoked upon each call to a {@link ConditionObject} method.
     * A typical usage example is to check if the thread has acquired
     * this synchronizer.
     * <p>The default implementation throws 
     * {@link UnsupportedOperationException}
     * @param thread the thread that wants to access the condition
     * @param waiting <tt>true</tt> if the access is for a condition-wait 
     * method, and <tt>false</tt> if any other method 
     * (such as <tt>signal</tt>).
     * @throws IllegalMonitorStateException if access is not permitted
     * @throws UnsupportedOperationException if conditions are not supported
     */
    protected void checkConditionAccess(Thread thread, boolean waiting) {
        throw new UnsupportedOperationException();
    }

    /**
     * Acquire in exclusive mode, ignoring interrupts.  Implemented by
     * first invoking {@link #tryAcquireExclusiveState} with
     * <tt>isQueued</tt> false, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireExclusiveState} until success.  With an
     * argument of 1, this can be used to implement method {@link
     * Lock#lock}
     * @param acquires the number of times to acquire.
     * This value is conveyed to {@link #tryAcquireExclusiveState} but
     * otherwise uninterpreted and can represent anything
     * you like.
     */
    public final void acquireExclusiveUninterruptibly(int acquires) {
        if (!tryAcquireExclusiveState(false, acquires)) 
            doAcquireExclusiveUninterruptibly(acquires);
    }

    /**
     * Acquire in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * {@link #tryAcquireExclusiveState} with <tt>isQueued</tt> false,
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquireExclusiveState} until success or the thread is
     * interrupted.  With an argument of 1, this can be used to
     * implement method {@link Lock#lockInterruptibly}
     * @param acquires the number of times to acquire.
     * This value is conveyed to {@link #tryAcquireExclusiveState} but
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireExclusiveInterruptibly(int acquires) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquireExclusiveState(false, acquires))
            doAcquireExclusiveInterruptibly(acquires);
    }

    /**
     * Acquire in exclusive mode, aborting if interrupted or the given
     * timeout elapses.  Implemented by first checking interrupt
     * status, then invoking {@link #tryAcquireExclusiveState} with
     * <tt>isQueued</tt> false, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireExclusiveState} until success or the
     * thread is interrupted or the timeout elapses.  With an argument
     * of 1, this can be used to implement method {@link
     * Lock#lockInterruptibly}
     * @param acquires the number of times to acquire.
     * This value is conveyed to {@link #tryAcquireExclusiveState} but
     * otherwise uninterpreted and can represent anything
     * you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return true if acquired; false if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
   public final boolean acquireExclusiveTimed(int acquires, long nanosTimeout) throws InterruptedException {
       if (Thread.interrupted())
           throw new InterruptedException();
       if (tryAcquireExclusiveState(false, acquires))
           return true;
       return doAcquireExclusiveTimed(acquires, nanosTimeout);
   }

    /**
     * Release in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #releaseExclusiveState} returns true.
     * With an argument of 1, this method can be used to implement
     * method {@link Lock#unlock}
     * @param releases the number of releases.
     * This value is conveyed to {@link #releaseExclusiveState} but
     * otherwise uninterpreted and can represent anything
     * you like.
     */
    public final void releaseExclusive(int releases) {
        if (releaseExclusiveState(releases)) 
            unparkFirst();
    }

    /**
     * Acquire in shared mode, ignoring interrupts.  Implemented by
     * first invoking {@link #tryAcquireSharedState} with
     * <tt>isQueued</tt> false, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireSharedState} until success.  With an
     * argument of 1, this can be used to implement method {@link
     * Lock#lock}
     * @param acquires the number of times to acquire.
     * This value is conveyed to {@link #tryAcquireSharedState} but
     * otherwise uninterpreted and can represent anything
     * you like.
     */
    public final void acquireSharedUninterruptibly(int acquires) {
        if (tryAcquireSharedState(false, acquires) < 0)
            doAcquireSharedUninterruptibly(acquires);
    }

    /**
     * Acquire in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking {@link
     * #tryAcquireSharedState} with <tt>isQueued</tt> false, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquireSharedState}
     * until success or the thread is interrupted.  With an argument
     * of 1, this can be used to implement method {@link
     * Lock#lockInterruptibly}
     * @param acquires the number of times to acquire.
     * This value is conveyed to {@link #tryAcquireSharedState} but
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int acquires) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (tryAcquireSharedState(false, acquires) < 0)
            doAcquireSharedInterruptibly(acquires);
   }

    /**
     * Acquire in shared mode, aborting if interrupted or the given
     * timeout elapses.  Implemented by first checking interrupt
     * status, then invoking {@link #tryAcquireSharedState} with
     * <tt>isQueued</tt> false, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireSharedState} until success or the
     * thread is interrupted or the timeout elapses.  With an argument
     * of 1, this can be used to implement method {@link
     * Lock#lockInterruptibly}
     * @param acquires the number of times to acquire.
     * This value is conveyed to {@link #tryAcquireSharedState} but
     * otherwise uninterpreted and can represent anything
     * you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return true if acquired; false if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
   public final boolean acquireSharedTimed(int acquires, long nanosTimeout) throws InterruptedException {
       if (Thread.interrupted())
           throw new InterruptedException();
       if (tryAcquireSharedState(false, acquires) >= 0)
           return true;
       return doAcquireSharedTimed(acquires, nanosTimeout);
   }

    /**
     * Release in shared mode.  Implemented by unblocking one or more
     * threads if {@link #releaseSharedState} returns true.  With an
     * argument of 1, this method can be used to implement method
     * {@link Lock#unlock}
     * @param releases the number of releases.
     * This value is conveyed to {@link #releaseSharedState} but
     * is otherwise uninterpreted and can represent anything
     * you like.
     */
    public final void releaseShared(int releases) {
        if (releaseSharedState(releases)) 
            unparkFirst();
    }

    // Queuing utilities

    /**
     * Insert node into queue.
     * @param node the node to insert
     */
    private void enq(Node node) {
        for (;;) {
            Node t = tail;
            if (t == null) {
                initializeQueue(node);
                return;
            }
            node.prev = t;      // Prev field must be valid before/upon CAS
            if (compareAndSetTail(t, node)) {
                t.next = node;  // Next field assignment lags CAS
                return;
            }
        }
    }

    /**
     * Initialize queue with given first node
     * @param node the node that will be first node in queue
     */
    private void initializeQueue(Node node) {
        for (;;) {
            Node t = tail;
            if (t == null) {
                Node h = new Node(null, false); // Dummy header
                if (compareAndSetHead(null, h))
                    tail = t = h;
                else
                    t = tail;
            }
            if (t != null) { // Variant of enq code
                node.prev = t; 
                if (compareAndSetTail(t, node)) {
                    t.next = node;  
                    return;
                }
            }
            // Initialization races can have unbounded spins, so yield
            Thread.yield();
        }
    }

    /**
     * Create and enq node for given thread and mode
     * @param current the thread
     * @param shared the mode
     * @return the new node
     */
    private Node addWaiter(Thread current, boolean shared) {
        Node node = new Node(current, false);
        enq(node);
        return node;
    }

    /**
     * Find the successor of a node, working backwards from the tail
     * @param node the node
     * @return successor, or null if there isn't one. 
     */
    private Node findSuccessorFromTail(Node node) {
        Node s = tail;
        if (s == null || s == node)
            return null;
        Node p = s.prev;
        for (;;) {
            if (p == null || p == node)
                return s;
            if (p.waitStatus != Node.CANCELLED) 
                s = p; 
            p = p.prev;
        }
    }

    /**
     * Set head of queue to be node.
     * Requires that pred == node.prev and that pred was old head
     * @param pred the node holding waitStatus for node
     * @param node the node. 
     */
    private void setHead(Node pred, Node node) {
        head = node;
        // Null out unused fields for GC 
        pred.next = null; 
        node.thread = null;
        node.prev = null; 
    }

    // Blocking support

    /**
     * Wake up node's successor, if one exists.
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        /*
         * Reset waitStatus before unparking. This improves
         * performance when called from a release method to enable
         * next thread: A given head-node can be in effect across
         * multiple releases that acquired by barging. When they do
         * so, and later release, the successor that lost a previous
         * race and re-parked must be re-unparked. But otherwise, we'd
         * like to minimize unnecessary calls to unpark, which may be
         * relatively expensive. We don't loop on failed CAS here
         * though, since this is just a heursitic, and also because
         * the node may legitimately be in CANCELLED state.
         */
        compareAndSetWaitStatus(node, Node.SIGNAL, 0);

        /*
         * Successor is normally just the next node.  But if cancelled
         * or apparently null, traverse backwards from tail to find
         * the actual non-cancelled successor.
         */
        Node s = node.next;
        if ((s != null && s.waitStatus != Node.CANCELLED) ||
            (s = findSuccessorFromTail(node)) != null)
            LockSupport.unpark(s.thread);
    }

    /**
     * Unpark first waiting thread, if one exists and needs signalling
     */
    private void unparkFirst() {
        Node h = head;
        if (h != null && h.waitStatus < 0)
            unparkSuccessor(h);
    }

    /**
     * Utility to park and then check interrupt status
     * @return true if interrupted
     */
    private static boolean parkAndCheckInterrupt() {
        LockSupport.park();
        return Thread.interrupted();
    }

    // Utilities for various versions of acquire

    /**
     * Cancel an ongoing attempt to acquire.
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        node.thread = null;   
        for (;;) {
            int s = node.waitStatus;
            if (compareAndSetWaitStatus(node, s, Node.CANCELLED))
                break;
        }
        unparkSuccessor(node);
    }

    /**
     * Set wait status for a node that failed to acquire.
     * Return true if thread should block.
     * Requires that pred == node.prev
     * @param node the node. 
     * @return true if thread should block
     */
    private boolean shouldParkAfterFailedAcquire(Node node) {
        Node pred = node.prev;
        int s = pred.waitStatus;
        if (s == Node.SIGNAL)
            return true;

        if (s == Node.CANCELLED) 
            node.prev = pred.prev;
        else
            compareAndSetWaitStatus(pred, 0, Node.SIGNAL);

        return false;
    }

    /**
     * Try an exclusive acquire for a node that is in queue.
     * Returns false if not at head of queue or if tryAcquireExclusive
     * fails, cancelling wait and rethrowing if it throws exception. 
     * On success, sets current node as head node.
     *
     * @param node the node. 
     * @param acquires the number of acquires
     * @return true if successful
     */
    private boolean tryAcquireExclusiveQueued(Node node, int acquires) {
        try {
            Node pred = node.prev;
            if (pred != head || !tryAcquireExclusiveState(true, acquires))
                return false;
            setHead(pred, node);
            return true;
        } catch (RuntimeException ex) {
            cancelAcquire(node);
            throw ex;
        }
    }

    /**
     * Try a shared acquire for a node that is in queue.
     * Returns false if not at head of queue or if tryAcquireExclusive
     * fails, cancelling wait and rethrowing if it throws exception. 
     * On success, sets current node as head node and propagates
     * signal to successors.
     *
     * @param node the node. 
     * @param acquires the number of acquires
     * @return true if successful
     */
    private boolean tryAcquireSharedQueued(Node node, int acquires) {
        try {
            Node pred = node.prev;
            if (pred != head)
                return false;
            int a = tryAcquireSharedState(true, acquires);
            if (a < 0)
                return false;
            setHead(pred, node);
            if (a > 0 && node.waitStatus < 0) {
                Node s = node.next; 
                if (s == null || s.shared)
                    unparkSuccessor(node);
            }
            return true;
        } catch (RuntimeException ex) {
            cancelAcquire(node);
            throw ex;
        }
    }

    // Various flavors of acquire varying in exclusive/shared
    // and control modes

    /**
     * Acquire in exclusive uninterruptible mode for thread already
     * in queue. Used by acquireExclusiveUninterruptibly as
     * well as condition wait methods
     * @param current the waiting thread
     * @param node its node
     * @param acquires the number of acquires
     */
    final void acquireExclusiveQueued(Thread current, Node node, int acquires) {
        boolean interrupted = false;
        while (!tryAcquireExclusiveQueued(node, acquires)) {
            if (shouldParkAfterFailedAcquire(node)) {
                if (parkAndCheckInterrupt())
                    interrupted = true;
            }
        }
        if (interrupted)
            current.interrupt();
    }

    /** 
     * Acquire in exclusive uninterruptible mode
     * @param acquires the number of acquires
     */
    private void doAcquireExclusiveUninterruptibly(int acquires) {
        Thread current = Thread.currentThread();
        Node node = addWaiter(current, false);
        acquireExclusiveQueued(current, node, acquires);
    }

    /** 
     * Acquire in exclusive interruptible mode
     * @param acquires the number of acquires
     */
    private void doAcquireExclusiveInterruptibly(int acquires) throws InterruptedException {

        Thread current = Thread.currentThread();
        Node node = addWaiter(current, false);
        while (!tryAcquireExclusiveQueued(node, acquires)) {
            if (shouldParkAfterFailedAcquire(node)) {
                if (parkAndCheckInterrupt()) {
                    cancelAcquire(node);
                    throw new InterruptedException();
                }
            }
        }
    }

    /** 
     * Acquire in exclusive timed mode
     * @param acquires the number of acquires
     * @param nanosTimeout max wait time
     * @return true if acquired
     */
    private boolean doAcquireExclusiveTimed(int acquires, long nanosTimeout) throws InterruptedException {
        long lastTime = System.nanoTime();
        Thread current = Thread.currentThread();
        Node node = addWaiter(current, false);

        while (!tryAcquireExclusiveQueued(node, acquires)) {
            if (shouldParkAfterFailedAcquire(node)) {
                if (nanosTimeout <= 0) {
                    cancelAcquire(node);
                    return false;
                }
                LockSupport.parkNanos(nanosTimeout);
                if (Thread.interrupted()) {
                    cancelAcquire(node);
                    throw new InterruptedException();
                }
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }
        }
        return true;
    }


    /** 
     * Acquire in shared uninterruptible mode
     * @param acquires the number of acquires
     */
    private void doAcquireSharedUninterruptibly(int acquires) {
        Thread current = Thread.currentThread();
        Node node = addWaiter(current, true);
        boolean interrupted = false;
        while (!tryAcquireSharedQueued(node, acquires)) {
            if (shouldParkAfterFailedAcquire(node)) {
                if (parkAndCheckInterrupt())
                    interrupted = true;
            }
        }
        if (interrupted)
            current.interrupt();
    }

    /** 
     * Acquire in shared interruptible mode
     * @param acquires the number of acquires
     */
    private void doAcquireSharedInterruptibly(int acquires) throws InterruptedException {

        Thread current = Thread.currentThread();
        Node node = addWaiter(current, true);
        while (!tryAcquireSharedQueued(node, acquires)) {
            if (shouldParkAfterFailedAcquire(node)) {
                if (parkAndCheckInterrupt()) {
                    cancelAcquire(node);
                    throw new InterruptedException();
                }
            }
        }
    }

    /** 
     * Acquire in shared timed mode
     * @param acquires the number of acquires
     * @param nanosTimeout max wait time
     * @return true if acquired
     */
    private boolean doAcquireSharedTimed(int acquires, long nanosTimeout) throws InterruptedException {
        long lastTime = System.nanoTime();
        Thread current = Thread.currentThread();
        Node node = addWaiter(current, true);
        while (!tryAcquireSharedQueued(node, acquires)) {
            if (shouldParkAfterFailedAcquire(node)) {
                if (nanosTimeout <= 0) {
                    cancelAcquire(node);
                    return false;
                }
                LockSupport.parkNanos(nanosTimeout);
                if (Thread.interrupted()) {
                    cancelAcquire(node);
                    throw new InterruptedException();
                }
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }
        }
        return true;
    }

    // Instrumentation and monitoring methods

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations may occur at any time, a <tt>true</tt>
     * return does not guarantee that any other thread will ever
     * acquire.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @return true if there may be other threads waiting to acquire
     * the lock.
     */
    public final boolean hasQueuedThreads() { 
        return head != tail; 
    }

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring of the system state, not for synchronization
     * control.
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null && p != head; p = p.prev)
            ++n;
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire with given mode.
     * @param shared true if shared mode, else exclusive
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads(boolean shared) {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.shared == shared) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }


    // Internal support methods for Conditions

    /**
     * Return true if a node, always one that was initially placed on
     * a condition queue, is now waiting to reacquire on sync queue.
     * @param node the node
     * @return true if is reacquiring
     */
    final boolean isOnSyncQueue(Node node) {
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        if (node.next != null) // If has successor, it must be on queue
            return true;
        /*
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         */
        Node t = tail; 
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    } 

    /**
     * Transfer a node from a condition queue onto sync queue. 
     * Return true if successful.
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal).
     */
    final boolean transferForSignal(Node node) {
        /*
         * If cannot change waitStatus, the node has been cancelled.
         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            return false;

        /*
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently/harmlessly wrong).
         */
        enq(node);
        Node p = node.prev;
        int c = p.waitStatus;
        if (c == Node.CANCELLED || 
            !compareAndSetWaitStatus(p, c, Node.SIGNAL))
            LockSupport.unpark(node.thread);

        return true;
    }

    /**
     * Transfer node, if necessary, to sync queue after a cancelled
     * wait. Return true if thread was cancelled before being
     * signalled.
     * @param current the waiting thread
     * @param node its node
     * @return true if cancelled before the node was signalled.
     */
    final boolean transferAfterCancelledWait(Thread current, Node node) {
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        /*
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         */
        while (!isOnSyncQueue(node)) 
            Thread.yield();
        return false;
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject 
     * uses this synchronizer as its lock.
     * @param condition the condition
     * @return <tt>true</tt> if owned
     * @throws NullPointerException if condition null
     */
    public final boolean owns(ConditionObject condition) {
        if (condition == null)
            throw new NullPointerException();
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a <tt>true</tt> return
     * does not guarantee that a future <tt>signal</tt> will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     * @param condition the condition
     * @return <tt>true</tt> if there are any waiting threads.
     * @throws IllegalMonitorStateException if exclusive synchronization 
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if condition null
     */ 
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     * @param condition the condition
     * @return the estimated number of waiting threads.
     * @throws IllegalMonitorStateException if exclusive synchronization 
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if condition null
     */ 
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.  
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization 
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if condition null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     *
     * <p> Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * <tt>AbstractQueuedSynchronizer</tt>.
     * 
     * <p> This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /** First node of condition queue. */
        private transient Node firstWaiter;
        /** Last node of condition queue. */
        private transient Node lastWaiter;

        /**
         * Creates a new <tt>ConditionObject</tt> instance.
         */
        public ConditionObject() { }

        // Internal methods

        /**
         * Add a new waiter to wait queue
         * @param current the thread that will be waiting
         * @return its new wait node
         */
        private Node addConditionWaiter(Thread current) {
            Node w = new Node(current, false, Node.CONDITION);
            Node t = lastWaiter;
            if (t == null) 
                firstWaiter = w;
            else 
                t.nextWaiter = w;
            lastWaiter = w;
            return w;
        }

        /**
         * Remove and transfer nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                if ( (firstWaiter = first.nextWaiter) == null) 
                    lastWaiter = null;
                first.nextWaiter = null;
            } while (!transferForSignal(first) &&
                     (first = firstWaiter) != null);
        }

        /**
         * Remove and transfer all nodes.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter  = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         */
        public final void signal() {
            checkConditionAccess(Thread.currentThread(), false);
            Node w = firstWaiter;
            if (w != null)
                doSignal(w);
        }
         
        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         */
        public final void signalAll() {
            checkConditionAccess(Thread.currentThread(), false);
            Node w = firstWaiter;
            if (w != null) 
                doSignalAll(w);
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Invoke {@link #checkConditionAccess} 
         * <li> Save lock state returned by {@link #getState} 
         * <li> Release by invoking {@link #releaseExclusive} with 
         *      saved state as argument
         * <li> Block until signalled
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquireExclusiveUninterruptibly} with
         *      saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            Thread current = Thread.currentThread();
            checkConditionAccess(current, true);
            Node w = addConditionWaiter(current);
            int savedState = getState();
            releaseExclusive(savedState);
            boolean interrupted = false;

            while (!isOnSyncQueue(w)) {
                LockSupport.park();
                if (Thread.interrupted()) 
                    interrupted = true;
            }

            acquireExclusiveQueued(current, w, savedState);
            if (interrupted)
                current.interrupt();
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         * <li> Invoke {@link #checkConditionAccess} 
         * <li> If current thread is interrupted, throw InterruptedException
         * <li> Save lock state returned by {@link #getState} 
         * <li> Release by invoking {@link #releaseExclusive} with 
         *      saved state as argument
         * <li> Block until signalled or interrupted
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquireExclusiveUninterruptibly} with
         *      saved state as argument.
         * <li> If interrupted while blocked, throw exception
         * </ol>
         */
        public final void await() throws InterruptedException {
            Thread current = Thread.currentThread();
            checkConditionAccess(current, true);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int savedState = getState();
            releaseExclusive(savedState);
            boolean throwIE = false;
            boolean interrupted = false;

            for (;;) {
                if (Thread.interrupted()) {
                    if (transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (isOnSyncQueue(w)) 
                    break;
                LockSupport.park();
            }

            acquireExclusiveQueued(current, w, savedState);
            if (throwIE)
                throw new InterruptedException();
            if (interrupted)
                current.interrupt();
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> Invoke {@link #checkConditionAccess} 
         * <li> If current thread is interrupted, throw InterruptedException
         * <li> Save lock state returned by {@link #getState} 
         * <li> Release by invoking {@link #releaseExclusive} with 
         *      saved state as argument
         * <li> Block until signalled, interrupted, or timed out
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquireExclusiveUninterruptibly} with
         *      saved state as argument.
         * <li> If interrupted while blocked, throw InterruptedException
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout) throws InterruptedException {
            Thread current = Thread.currentThread();
            checkConditionAccess(current, true);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int savedState = getState();
            releaseExclusive(savedState);
            long lastTime = System.nanoTime();
            boolean throwIE = false;
            boolean interrupted = false;

            for (;;) {
                if (Thread.interrupted()) {
                    if (transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(current, w); 
                    break;
                }
                if (isOnSyncQueue(w)) 
                    break;
                LockSupport.parkNanos(nanosTimeout);
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }

            acquireExclusiveQueued(current, w, savedState);
            if (throwIE)
                throw new InterruptedException();
            if (interrupted)
                current.interrupt();
            return nanosTimeout - (System.nanoTime() - lastTime);
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> Invoke {@link #checkConditionAccess} 
         * <li> If current thread is interrupted, throw InterruptedException
         * <li> Save lock state returned by {@link #getState} 
         * <li> Release by invoking {@link #releaseExclusive} with 
         *      saved state as argument
         * <li> Block until signalled, interrupted, or timed out
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquireExclusiveUninterruptibly} with
         *      saved state as argument.
         * <li> If interrupted while blocked, throw InterruptedException
         * <li> If timed out while blocked, return false, else true
         * </ol>
         */
        public final boolean awaitUntil(Date deadline) throws InterruptedException {
            if (deadline == null)
                throw new NullPointerException();
            Thread current = Thread.currentThread();
            checkConditionAccess(current, true);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int savedState = getState();
            releaseExclusive(savedState);
            long abstime = deadline.getTime();
            boolean timedout = false;
            boolean throwIE = false;
            boolean interrupted = false;
            
            for (;;) {
                if (Thread.interrupted()) {
                    if (transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(current, w); 
                    break;
                }
                if (isOnSyncQueue(w)) 
                    break;
                LockSupport.parkUntil(abstime);
            }

            acquireExclusiveQueued(current, w, savedState);
            if (throwIE)
                throw new InterruptedException();
            if (interrupted)
                current.interrupt();
            return !timedout;
        }
        
        /**
         * Implements timed condition wait. 
         * <ol>
         * <li> Invoke {@link #checkConditionAccess} 
         * <li> If current thread is interrupted, throw InterruptedException
         * <li> Save lock state returned by {@link #getState} 
         * <li> Release by invoking {@link #releaseExclusive} with 
         *      saved state as argument
         * <li> Block until signalled, interrupted, or timed out
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquireExclusiveUninterruptibly} with
         *      saved state as argument.
         * <li> If interrupted while blocked, throw InterruptedException
         * <li> If timed out while blocked, return false, else true
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();

            long nanosTimeout = unit.toNanos(time);
            Thread current = Thread.currentThread();
            checkConditionAccess(current, true);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int savedState = getState();
            releaseExclusive(savedState);
            long lastTime = System.nanoTime();
            boolean timedout = false;
            boolean throwIE = false;
            boolean interrupted = false;

            for (;;) {
                if (Thread.interrupted()) {
                    if (transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(current, w); 
                    break;
                }
                if (isOnSyncQueue(w)) 
                    break;
                LockSupport.parkNanos(nanosTimeout);
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }

            acquireExclusiveQueued(current, w, savedState);
            if (throwIE)
                throw new InterruptedException();
            if (interrupted)
                current.interrupt();
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object
         * @return true if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters}
         * @return <tt>true</tt> if there are any waiting threads.
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         */ 
        final boolean hasWaiters() {
            checkConditionAccess(Thread.currentThread(), false);
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition. 
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength}
         * @return the estimated number of waiting threads.
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         */ 
        final int getWaitQueueLength() {
            checkConditionAccess(Thread.currentThread(), false);
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.  
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads}
         * @return the collection of threads
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         */
        final Collection<Thread> getWaitingThreads() {
            checkConditionAccess(Thread.currentThread(), false);
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }
}
