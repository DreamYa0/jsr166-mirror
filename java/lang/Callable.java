package java.lang;

/**
 * A callable computes a function.
 * The <tt>Callable</tt> interface plays a role similar to that of
 * <tt>Runnable</tt>, except that it represents methods that
 * return results and (possibly) throw exceptions. 
 * The <tt>Callable</tt> interface may be implemented by 
 * any class with a method computing a function that can be invoked
 * in a "blind" fashion by some other execution agent, for
 * example a java.util.concurrent.Executor.
 * The most common implementations of Callable are inner classes
 * that supply arguments operated on by the function.
 * @see     java.util.concurrent.Executor
 * @see     java.util.concurrent.Future
 **/
public interface Callable {
    Object call() throws Exception;
}

