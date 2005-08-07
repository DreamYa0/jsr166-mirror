/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class CachedThreadPoolLoops {
    static final AtomicInteger remaining = new AtomicInteger();
    static final int maxIters = 1000000;

    public static void main(String[] args) throws Exception {
        int maxThreads = 100;

        if (args.length > 0) 
            maxThreads = Integer.parseInt(args[0]);

        System.out.print("Warmup:");
        for (int j = 0; j < 2; ++j) {
            int k = 1;
            for (int i = 1; i <= maxThreads;) {
                System.out.print(" " + i);
                oneTest(i, 10000, false);
                Thread.sleep(100);
                if (i == k) {
                    k = i << 1;
                    i = i + (i >>> 1);
                } 
                else 
                    i = k;
            }
        }
        System.out.println();

        int k = 1;
        for (int i = 1; i <= maxThreads;) {
            System.out.println("Threads:" + i);
            oneTest(i, maxIters, true);
            Thread.sleep(100);
            if (i == k) {
                k = i << 1;
                i = i + (i >>> 1);
            } 
            else 
                i = k;
        }
   }

    static void oneTest(int nThreads, int iters, boolean print) throws Exception {
        if (print) System.out.print("SynchronousQueue        ");
        oneRun(new SynchronousQueue<Runnable>(false), nThreads, iters, print);
        if (print) System.out.print("SynchronousQueue(fair)  ");
        oneRun(new SynchronousQueue<Runnable>(true), nThreads, iters, print);
    }

    static final class Task implements Runnable {
        final ThreadPoolExecutor pool;
        final CountDownLatch done;
        Task(ThreadPoolExecutor p, CountDownLatch d) { 
            pool = p; 
            done = d;
        }
        public void run() {
            done.countDown();
            remaining.incrementAndGet();
            int n;
            while (!Thread.interrupted() &&
                   (n = remaining.get()) > 0 && 
                   done.getCount() > 0) {
                if (remaining.compareAndSet(n, n-1)) {
                    try {
                        pool.execute(this);
                    }
                    catch (RuntimeException ex) {
                        System.out.print("*");
                        while (done.getCount() > 0) done.countDown();
                        return;
                    }
                }
            }
        }
    }
    
    static void oneRun(BlockingQueue<Runnable> q, int nThreads, int iters, boolean print) throws Exception {
       
        ThreadPoolExecutor pool = 
            new ThreadPoolExecutor(nThreads+1, Integer.MAX_VALUE,
                                   1L, TimeUnit.SECONDS, q);

        CountDownLatch done = new CountDownLatch(iters);
        remaining.set(nThreads-1);
        pool.prestartAllCoreThreads();
        Task t = new Task(pool, done);
        long start = System.nanoTime();
        pool.execute(t);
        done.await();
        long time = System.nanoTime() - start;
        if (print)
            System.out.println("\t: " + LoopHelpers.rightJustify(time / iters) + " ns per task");
        q.clear();
        Thread.sleep(100);
        pool.shutdown();
        Thread.sleep(100);
        pool.shutdownNow();
    }

}
