/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * A <tt>long</tt> value that may be updated atomically.
 * See the package specification for
 * description of the properties of atomic variables. 
 * @since 1.5
 * @author Doug Lea
 */
public class AtomicLong implements java.io.Serializable { 
    private static final long serialVersionUID = 1927816293512124184L;

    // setup to use Unsafe.compareAndSwapInt for updates
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final long valueOffset;

    static {
      try {
        valueOffset = unsafe.objectFieldOffset
            (AtomicLong.class.getDeclaredField("value"));
      } catch(Exception ex) { throw new Error(ex); }
    }

    private volatile long value;

    /**
     * Create a new AtomicLong with the given initial value.
     *
     * @param initialValue the initial value
     */
    public AtomicLong(long initialValue) {
        value = initialValue;
    }

    /**
     * Create a new AtomicLong with initial value <tt>0</tt>.
     */
    public AtomicLong() {
    }
  
    /**
     * Get the current value.
     *
     * @return the current value
     */
    public final long get() {
        return value;
    }
 
    /**
     * Set to the given value.
     *
     * @param newValue the new value
     */
    public final void set(long newValue) {
        value = newValue;
    }
  
    /**
     * Set to the give value and return the old value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final long getAndSet(long newValue) {
        while (true) {
            long current = get();
            if (compareAndSet(current, newValue))
                return current;
        }
    }
  
    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public final boolean compareAndSet(long expect, long update) {
      return unsafe.compareAndSwapLong(this, valueOffset, expect, update);
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     */
    public final boolean weakCompareAndSet(long expect, long update) {
      return unsafe.compareAndSwapLong(this, valueOffset, expect, update);
    }
  
    /**
     * Atomically increment by one the current value.
     * @return the previous value
     */
    public final long getAndIncrement() {
        while (true) {
            long current = get();
            long next = current + 1;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically decrement by one the current value.
     * @return the previous value
     */
    public final long getAndDecrement() {
        while (true) {
            long current = get();
            long next = current - 1;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically add the given value to current value.
     * @param delta the value to add
     * @return the previous value
     */
    public final long getAndAdd(long delta) {
        while (true) {
            long current = get();
            long next = current + delta;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
    /**
     * Atomically increment by one the current value.
     * @return the updated value
     */
    public final long incrementAndGet() {
        for (;;) {
            long current = get();
            long next = current + 1;
            if (compareAndSet(current, next))
                return next;
        }
    }
    
    /**
     * Atomically decrement by one the current value.
     * @return the updated value
     */
    public final long decrementAndGet() {
        for (;;) {
            long current = get();
            long next = current - 1;
            if (compareAndSet(current, next))
                return next;
        }
    }
  
  
    /**
     * Atomically add the given value to current value.
     * @param delta the value to add
     * @return the updated value
     */
    public final long addAndGet(long delta) {
        for (;;) {
            long current = get();
            long next = current + delta;
            if (compareAndSet(current, next))
                return next;
        }
    }
  
  
}
