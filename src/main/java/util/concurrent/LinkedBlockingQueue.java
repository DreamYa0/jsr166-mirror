/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.*;

/**
 * An optionally-bounded {@link BlockingQueue blocking queue} based on 
 * linked nodes.
 * This queue orders elements FIFO (first-in-first-out).
 * The <em>head</em> of the queue is that element that has been on the 
 * queue the longest time.
 * The <em>tail</em> of the queue is that element that has been on the
 * queue the shortest time.
 * Linked queues typically have higher throughput than array-based queues but
 * less predictable performance in most concurrent applications.
 * 
 * <p> The optional capacity bound constructor argument serves as a
 * way to prevent excessive queue expansion. The capacity, if unspecified,
 * is equal to {@link Integer#MAX_VALUE}.  Linked nodes are
 * dynamically created upon each insertion unless this would bring the
 * queue above capacity.
 *
 * @since 1.5
 * @author Doug Lea
 * 
 **/
public class LinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    /*
     * A variant of the "two lock queue" algorithm.  The putLock gates
     * entry to put (and offer), and has an associated condition for
     * waiting puts.  Similarly for the takeLock.  The "count" field
     * that they both rely on is maintained as an atomic to avoid
     * needing to get both locks in most cases. Also, to minimize need
     * for puts to get takeLock and vice-versa, cascading notifies are
     * used. When a put notices that it has enabled at least one take,
     * it signals taker. That taker in turn signals others if more
     * items have been entered since the signal. And symmetrically for
     * takes signalling puts. Operations such as remove(Object) and 
     * iterators acquire both locks.
    */

    /**
     * Linked list node class
     */
    static class Node<E> {
        /** The item, volatile to ensure barrier separating write and read */
        volatile E item;
        Node<E> next;
        Node(E x) { item = x; }
    }

    /** The capacity bound, or Integer.MAX_VALUE if none */
    private final int capacity;

    /** Current number of elements */
    private transient final AtomicInteger count = new AtomicInteger(0);

    /** Head of linked list */
    private transient Node<E> head;

    /** Tail of linked list */
    private transient Node<E> last;

    /** Lock held by take, poll, etc */
    private final ReentrantLock takeLock = new ReentrantLock();

    /** Wait queue for waiting takes */
    private final Condition notEmpty = takeLock.newCondition();

    /** Lock held by put, offer, etc */
    private final ReentrantLock putLock = new ReentrantLock();

    /** Wait queue for waiting puts */
    private final Condition notFull = putLock.newCondition();

    /**
     * Signal a waiting take. Called only from put/offer (which do not
     * otherwise ordinarily lock takeLock.)
     */
    private void signalNotEmpty() {
        takeLock.lock();
        try {
            notEmpty.signal();
        }
        finally {
            takeLock.unlock();
        }
    }

    /**
     * Signal a waiting put. Called only from take/poll.
     */
    private void signalNotFull() {
        putLock.lock();
        try {
            notFull.signal();
        }
        finally {
            putLock.unlock();
        }
    }

    /**
     * Create a node and link it at end of queue
     * @param x the item
     */
    private void insert(E x) {
        last = last.next = new Node<E>(x);
    }

    /**
     * Remove a node from head of queue,
     * @return the node
     */
    private E extract() {
        Node<E> first = head.next;
        head = first;
        E x = (E)first.item;
        first.item = null;
        return x;
    }

    /**
     * Lock to prevent both puts and takes. 
     */
    private void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    /**
     * Unlock to allow both puts and takes. 
     */
    private void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }


    /**
     * Create a <tt>LinkedBlockingQueue</tt> with a capacity of
     * {@link Integer#MAX_VALUE}.
     */
    public LinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }

    /**
     * Create a <tt>LinkedBlockingQueue</tt> with the given (fixed) capacity 
     * @param capacity the capacity of this queue.
     * @throws IllegalArgumentException if <tt>capacity</tt> is not greater
     * than zero.
     */
    public LinkedBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        last = head = new Node<E>(null);
    }

    /**
     * Create a <tt>LinkedBlockingQueue</tt> with a capacity of
     * {@link Integer#MAX_VALUE}, initially holding the elements of the 
     * given collection, 
     * added in traversal order of the collection's iterator.
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if <tt>c</tt> or any element within it
     * is <tt>null</tt>
     */
    public LinkedBlockingQueue(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        for (Iterator<E> it = c.iterator(); it.hasNext();) 
            add(it.next());
    }


    // Have to override just to update the javadoc for @throws

    /**
     * @throws IllegalStateException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean add(E o) {
        return super.add(o);
    }

    /**
     * @throws IllegalStateException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean addAll(Collection<? extends E> c) {
        return super.addAll(c);
    }


    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE
    /** 
     * Return the number of elements in this collection. 
     */
    public int size() {
        return count.get();
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.
    /** 
     * Return the number of elements that this queue can ideally (in
     * the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current <tt>size</tt> of this queue.
     * <p>Note that you <em>cannot</em> always tell if
     * an attempt to <tt>add</tt> an element will succeed by
     * inspecting <tt>remainingCapacity</tt> because it may be the
     * case that a waiting consumer is ready to <tt>take</tt> an
     * element out of an otherwise full queue.
     */
    public int remainingCapacity() {
        return capacity - count.get();
    }

    /**
     * Add the specified element to the tail of this queue, waiting if 
     * necessary for space to become available.
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E x) throws InterruptedException {
        if (x == null) throw new NullPointerException();
        // Note: convention in all put/take/etc is to preset
        // local var holding count  negative to indicate failure unless set.
        int c = -1; 
        putLock.lockInterruptibly();
        try {
            /*
             * Note that count is used in wait guard even though it is
             * not protected by lock. This works because count can
             * only decrease at this point (all other puts are shut
             * out by lock), and we (or some other waiting put) are
             * signalled if it ever changes from
             * capacity. Similarly for all other uses of count in
             * other wait guards.
             */
            try {
                while (count.get() == capacity) 
                    notFull.await();
            }
            catch (InterruptedException ie) {
                notFull.signal(); // propagate to a non-interrupted thread
                throw ie;
            }
            insert(x);
            c = count.getAndIncrement();
            if (c + 1 < capacity)
                notFull.signal();
        }
        finally {
            putLock.unlock();
        }
        if (c == 0) 
            signalNotEmpty();
    }

    /**
     * Add the specified element to the tail of this queue, waiting if 
     * necessary up to the specified wait time for space to become available.
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E x, long timeout, TimeUnit unit) 
        throws InterruptedException {
       
        if (x == null) throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        int c = -1;
        putLock.lockInterruptibly();
        try {
            for (;;) {
                if (count.get() < capacity) {
                    insert(x);
                    c = count.getAndIncrement();
                    if (c + 1 < capacity)
                        notFull.signal();
                    break;
                }
                if (nanos <= 0)
                    return false;
                try {
                    nanos = notFull.awaitNanos(nanos);
                }
                catch (InterruptedException ie) {
                    notFull.signal(); // propagate to a non-interrupted thread
                    throw ie;
                }
            }
        }
        finally {
            putLock.unlock();
        }
        if (c == 0) 
            signalNotEmpty();
        return true;
    }

   /** 
    * Add the specified element to the tail of this queue if possible,
    * returning immediately if this queue is full.
    *
    * @throws NullPointerException {@inheritDoc}
    */
    public boolean offer(E x) {
        if (x == null) throw new NullPointerException();
        if (count.get() == capacity)
            return false;
        int c = -1; 
        putLock.lock();
        try {
            if (count.get() < capacity) {
                insert(x);
                c = count.getAndIncrement();
                if (c + 1 < capacity)
                    notFull.signal();
            }
        }
        finally {
            putLock.unlock();
        }
        if (c == 0) 
            signalNotEmpty();
        return c >= 0;
    }


    public E take() throws InterruptedException {
        E x;
        int c = -1;
        takeLock.lockInterruptibly();
        try {
            try {
                while (count.get() == 0) 
                    notEmpty.await();
            }
            catch (InterruptedException ie) {
                notEmpty.signal(); // propagate to a non-interrupted thread
                throw ie;
            }

            x = extract();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        }
        finally {
            takeLock.unlock();
        }
        if (c == capacity) 
            signalNotFull();
        return x;
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E x = null;
        int c = -1;
        long nanos = unit.toNanos(timeout);
        takeLock.lockInterruptibly();
        try {
            for (;;) {
                if (count.get() > 0) {
                    x = extract();
                    c = count.getAndDecrement();
                    if (c > 1)
                        notEmpty.signal();
                    break;
                }
                if (nanos <= 0)
                    return null;
                try {
                    nanos = notEmpty.awaitNanos(nanos);
                }
                catch (InterruptedException ie) {
                    notEmpty.signal(); // propagate to a non-interrupted thread
                    throw ie;
                }
            }
        }
        finally {
            takeLock.unlock();
        }
        if (c == capacity) 
            signalNotFull();
        return x;
    }

    public E poll() {
        if (count.get() == 0)
            return null;
        E x = null;
        int c = -1; 
        takeLock.tryLock();
        try {
            if (count.get() > 0) {
                x = extract();
                c = count.getAndDecrement();
                if (c > 1)
                    notEmpty.signal();
            }
        }
        finally {
            takeLock.unlock();
        }
        if (c == capacity) 
            signalNotFull();
        return x;
    }


    public E peek() {
        if (count.get() == 0)
            return null;
        takeLock.lock();
        try {
            Node<E> first = head.next;
            if (first == null)
                return null;
            else
                return first.item;
        }
        finally {
            takeLock.unlock();
        }
    }

    public boolean remove(Object o) {
        if (o == null) return false;
        boolean removed = false;
        fullyLock();
        try {
            Node<E> trail = head;
            Node<E> p = head.next;
            while (p != null) {
                if (o.equals(p.item)) {
                    removed = true;
                    break;
                }
                trail = p;
                p = p.next;
            }
            if (removed) {
                p.item = null;
                trail.next = p.next;
                if (count.getAndDecrement() == capacity)
                    notFull.signalAll();
            }
        }
        finally {
            fullyUnlock();
        }
        return removed;
    }

    public Object[] toArray() {
        fullyLock();
        try {
            int size = count.get();
            Object[] a = new Object[size];                
            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next) 
                a[k++] = p.item;
            return a;
        }
        finally {
            fullyUnlock();
        }
    }

    public <T> T[] toArray(T[] a) {
        fullyLock();
        try {
            int size = count.get();
            if (a.length < size)
                a = (T[])java.lang.reflect.Array.newInstance
                    (a.getClass().getComponentType(), size);
            
            int k = 0;
            for (Node p = head.next; p != null; p = p.next) 
                a[k++] = (T)p.item;
            return a;
        }
        finally {
            fullyUnlock();
        }
    }

    public String toString() {
        fullyLock();
        try {
            return super.toString();
        }
        finally {
            fullyUnlock();
        }
    }

    public Iterator<E> iterator() {
      return new Itr();
    }

    private class Itr implements Iterator<E> {
        /* 
         * Basic weak-consistent iterator.  At all times hold the next
         * item to hand out so that if hasNext() reports true, we will
         * still have it to return even if lost race with a take etc.
         */
        Node<E> current;
        Node<E> lastRet;
        E currentElement;
        
        Itr() {
            fullyLock();
            try {
                current = head.next;
                if (current != null)
                    currentElement = current.item;
            }
            finally {
                fullyUnlock();
            }
        }
        
	public boolean hasNext() {
            return current != null;
        }

	public E next() {
            fullyLock();
            try {
                if (current == null)
                    throw new NoSuchElementException();
                E x = currentElement;
                lastRet = current;
                current = current.next;
                if (current != null)
                    currentElement = current.item;
                return x;
            }
            finally {
                fullyUnlock();
            }
            
        }

	public void remove() {
            if (lastRet == null)
		throw new IllegalStateException();
            fullyLock();
            try {
                Node<E> node = lastRet;
                lastRet = null;
                Node<E> trail = head;
                Node<E> p = head.next;
                while (p != null && p != node) {
                    trail = p;
                    p = p.next;
                }
                if (p == node) {
                    p.item = null;
                    trail.next = p.next;
                    int c = count.getAndDecrement();
                    if (c == capacity)
                        notFull.signalAll();
                }
            }
            finally {
                fullyUnlock();
            }
        }
    }

    /**
     * Save the state to a stream (that is, serialize it).
     *
     * @serialData The capacity is emitted (int), followed by all of
     * its elements (each an <tt>Object</tt>) in the proper order,
     * followed by a null
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        fullyLock(); 
        try {
            // Write out any hidden stuff, plus capacity
            s.defaultWriteObject();

            // Write out all elements in the proper order.
            for (Node<E> p = head.next; p != null; p = p.next) 
                s.writeObject(p.item);

            // Use trailing null as sentinel
            s.writeObject(null);
        }
        finally {
            fullyUnlock();
        }
    }

    /**
     * Reconstitute this queue instance from a stream (that is,
     * deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
	// Read in capacity, and any hidden stuff
	s.defaultReadObject();

        // Read in all elements and place in queue
        for (;;) {
            E item = (E)s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }
}





