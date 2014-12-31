/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.HashSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import junit.framework.Test;
import junit.framework.TestSuite;

public class CountedCompleterTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(CountedCompleterTest.class);
    }

    // Runs with "mainPool" use > 1 thread. singletonPool tests use 1
    static final int mainPoolSize =
        Math.max(2, Runtime.getRuntime().availableProcessors());

    private static ForkJoinPool mainPool() {
        return new ForkJoinPool(mainPoolSize);
    }

    private static ForkJoinPool singletonPool() {
        return new ForkJoinPool(1);
    }

    private static ForkJoinPool asyncSingletonPool() {
        return new ForkJoinPool(1,
                                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                                null, true);
    }

    private void testInvokeOnPool(ForkJoinPool pool, ForkJoinTask a) {
        try {
            assertFalse(a.isDone());
            assertFalse(a.isCompletedNormally());
            assertFalse(a.isCompletedAbnormally());
            assertFalse(a.isCancelled());
            assertNull(a.getException());
            assertNull(a.getRawResult());

            assertNull(pool.invoke(a));

            assertTrue(a.isDone());
            assertTrue(a.isCompletedNormally());
            assertFalse(a.isCompletedAbnormally());
            assertFalse(a.isCancelled());
            assertNull(a.getException());
            assertNull(a.getRawResult());
        } finally {
            joinPool(pool);
        }
    }

    void checkNotDone(CountedCompleter a) {
        assertFalse(a.isDone());
        assertFalse(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertFalse(a.isCancelled());
        assertNull(a.getException());
        assertNull(a.getRawResult());

        try {
            a.get(0L, SECONDS);
            shouldThrow();
        } catch (TimeoutException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCompletedNormally(CountedCompleter<?> a) {
        assertTrue(a.isDone());
        assertFalse(a.isCancelled());
        assertTrue(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertNull(a.getException());
        assertNull(a.getRawResult());

        {
            Thread.currentThread().interrupt();
            long t0 = System.nanoTime();
            assertNull(a.join());
            assertTrue(millisElapsedSince(t0) < SMALL_DELAY_MS);
            Thread.interrupted();
        }

        {
            Thread.currentThread().interrupt();
            long t0 = System.nanoTime();
            a.quietlyJoin();        // should be no-op
            assertTrue(millisElapsedSince(t0) < SMALL_DELAY_MS);
            Thread.interrupted();
        }

        assertFalse(a.cancel(false));
        assertFalse(a.cancel(true));
        try {
            assertNull(a.get());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertNull(a.get(5L, SECONDS));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCancelled(CountedCompleter a) {
        assertTrue(a.isDone());
        assertTrue(a.isCancelled());
        assertFalse(a.isCompletedNormally());
        assertTrue(a.isCompletedAbnormally());
        assertTrue(a.getException() instanceof CancellationException);
        assertNull(a.getRawResult());
        assertTrue(a.cancel(false));
        assertTrue(a.cancel(true));

        try {
            Thread.currentThread().interrupt();
            a.join();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        Thread.interrupted();

        {
            long t0 = System.nanoTime();
            a.quietlyJoin();        // should be no-op
            assertTrue(millisElapsedSince(t0) < SMALL_DELAY_MS);
        }

        try {
            a.get();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.get(5L, SECONDS);
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCompletedAbnormally(CountedCompleter a, Throwable t) {
        assertTrue(a.isDone());
        assertFalse(a.isCancelled());
        assertFalse(a.isCompletedNormally());
        assertTrue(a.isCompletedAbnormally());
        assertSame(t.getClass(), a.getException().getClass());
        assertNull(a.getRawResult());
        assertFalse(a.cancel(false));
        assertFalse(a.cancel(true));

        try {
            Thread.currentThread().interrupt();
            a.join();
            shouldThrow();
        } catch (Throwable expected) {
            assertSame(t.getClass(), expected.getClass());
        }
        Thread.interrupted();

        {
            long t0 = System.nanoTime();
            a.quietlyJoin();        // should be no-op
            assertTrue(millisElapsedSince(t0) < SMALL_DELAY_MS);
        }

        try {
            a.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(t.getClass(), success.getCause().getClass());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.get(5L, SECONDS);
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(t.getClass(), success.getCause().getClass());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.invoke();
            shouldThrow();
        } catch (Throwable ex) {
            assertSame(t, ex);
        }
    }

    public static final class FJException extends RuntimeException {
        FJException() { super(); }
    }

    abstract class CheckedCC extends CountedCompleter<Object> {
        final AtomicInteger computeN = new AtomicInteger(0);
        final AtomicInteger onCompletionN = new AtomicInteger(0);
        final AtomicInteger onExceptionalCompletionN = new AtomicInteger(0);
        final AtomicInteger setRawResultN = new AtomicInteger(0);
        final AtomicReference<Object> rawResult = new AtomicReference<Object>(null);
        int computeN() { return computeN.get(); }
        int onCompletionN() { return onCompletionN.get(); }
        int onExceptionalCompletionN() { return onExceptionalCompletionN.get(); }
        int setRawResultN() { return setRawResultN.get(); }

        CheckedCC() { super(); }
        CheckedCC(CountedCompleter p) { super(p); }
        CheckedCC(CountedCompleter p, int n) { super(p, n); }
        abstract void realCompute();
        public final void compute() {
            computeN.incrementAndGet();
            realCompute();
        }
        public void onCompletion(CountedCompleter caller) {
            onCompletionN.incrementAndGet();
            super.onCompletion(caller);
        }
        public boolean onExceptionalCompletion(Throwable ex,
                                               CountedCompleter caller) {
            onExceptionalCompletionN.incrementAndGet();
            assertNotNull(ex);
            assertTrue(isCompletedAbnormally());
            assertTrue(super.onExceptionalCompletion(ex, caller));
            return true;
        }
        protected void setRawResult(Object t) {
            setRawResultN.incrementAndGet();
            rawResult.set(t);
            super.setRawResult(t);
        }
        void checkIncomplete() {
            assertEquals(0, computeN());
            assertEquals(0, onCompletionN());
            assertEquals(0, onExceptionalCompletionN());
            assertEquals(0, setRawResultN());
            checkNotDone(this);
        }
        void checkCompletes(Object rawResult) {
            checkIncomplete();
            int pendingCount = getPendingCount();
            complete(rawResult);
            assertEquals(pendingCount, getPendingCount());
            assertEquals(0, computeN());
            assertEquals(1, onCompletionN());
            assertEquals(0, onExceptionalCompletionN());
            assertEquals(1, setRawResultN());
            assertSame(rawResult, this.rawResult.get());
            checkCompletedNormally(this);
        }
        void checkCompletesExceptionally(Throwable ex) {
            checkIncomplete();
            completeExceptionally(ex);
            checkCompletedExceptionally(ex);
        }
        void checkCompletedExceptionally(Throwable ex) {
            assertEquals(0, computeN());
            assertEquals(0, onCompletionN());
            assertEquals(1, onExceptionalCompletionN());
            assertEquals(0, setRawResultN());
            assertNull(this.rawResult.get());
            checkCompletedAbnormally(this, ex);
        }
    }

    final class NoopCC extends CheckedCC {
        NoopCC() { super(); }
        NoopCC(CountedCompleter p) { super(p); }
        protected void realCompute() {}
    }

    /**
     * A newly constructed CountedCompleter is not completed;
     * complete() causes completion. pendingCount is ignored.
     */
    public void testComplete() {
        for (Object x : new Object[] { Boolean.TRUE, null }) {
            for (int pendingCount : new int[] { 0, 42 }) {
                testComplete(new NoopCC(), x, pendingCount);
                testComplete(new NoopCC(new NoopCC()), x, pendingCount);
            }
        }
    }
    void testComplete(NoopCC cc, Object x, int pendingCount) {
        cc.setPendingCount(pendingCount);
        cc.checkCompletes(x);
    }

    /**
     * completeExceptionally completes exceptionally
     */
    public void testCompleteExceptionally() {
        new NoopCC()
            .checkCompletesExceptionally(new FJException());
        new NoopCC(new NoopCC())
            .checkCompletesExceptionally(new FJException());
    }

    /**
     * completeExceptionally(null) throws NullPointerException
     */
    public void testCompleteExceptionally_null() {
        try {
            new NoopCC()
                .checkCompletesExceptionally(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * setPendingCount sets the reported pending count
     */
    public void testSetPendingCount() {
        NoopCC a = new NoopCC();
        assertEquals(0, a.getPendingCount());
        a.setPendingCount(1);
        assertEquals(1, a.getPendingCount());
        a.setPendingCount(27);
        assertEquals(27, a.getPendingCount());
    }

    /**
     * addToPendingCount adds to the reported pending count
     */
    public void testAddToPendingCount() {
        NoopCC a = new NoopCC();
        assertEquals(0, a.getPendingCount());
        a.addToPendingCount(1);
        assertEquals(1, a.getPendingCount());
        a.addToPendingCount(27);
        assertEquals(28, a.getPendingCount());
    }

    /**
     * decrementPendingCountUnlessZero decrements reported pending
     * count unless zero
     */
    public void testDecrementPendingCount() {
        NoopCC a = new NoopCC();
        assertEquals(0, a.getPendingCount());
        a.addToPendingCount(1);
        assertEquals(1, a.getPendingCount());
        a.decrementPendingCountUnlessZero();
        assertEquals(0, a.getPendingCount());
        a.decrementPendingCountUnlessZero();
        assertEquals(0, a.getPendingCount());
    }

    /**
     * compareAndSetPendingCount compares and sets the reported
     * pending count
     */
    public void testCompareAndSetPendingCount() {
        NoopCC a = new NoopCC();
        assertEquals(0, a.getPendingCount());
        assertTrue(a.compareAndSetPendingCount(0, 1));
        assertEquals(1, a.getPendingCount());
        assertTrue(a.compareAndSetPendingCount(1, 2));
        assertEquals(2, a.getPendingCount());
        assertFalse(a.compareAndSetPendingCount(1, 3));
        assertEquals(2, a.getPendingCount());
    }

    /**
     * getCompleter returns parent or null if at root
     */
    public void testGetCompleter() {
        NoopCC a = new NoopCC();
        assertNull(a.getCompleter());
        CountedCompleter b = new NoopCC(a);
        assertSame(a, b.getCompleter());
        CountedCompleter c = new NoopCC(b);
        assertSame(b, c.getCompleter());
    }

    /**
     * getRoot returns self if no parent, else parent's root
     */
    public void testGetRoot() {
        NoopCC a = new NoopCC();
        NoopCC b = new NoopCC(a);
        NoopCC c = new NoopCC(b);
        assertSame(a, a.getRoot());
        assertSame(a, b.getRoot());
        assertSame(a, c.getRoot());
    }

    /**
     * tryComplete decrements pending count unless zero, in which case
     * causes completion
     */
    public void testTryComplete() {
        NoopCC a = new NoopCC();
        assertEquals(0, a.getPendingCount());
        int n = 3;
        a.setPendingCount(n);
        for (; n > 0; n--) {
            assertEquals(n, a.getPendingCount());
            a.tryComplete();
            a.checkIncomplete();
            assertEquals(n - 1, a.getPendingCount());
        }
        a.tryComplete();
        assertEquals(0, a.computeN());
        assertEquals(1, a.onCompletionN());
        assertEquals(0, a.onExceptionalCompletionN());
        assertEquals(0, a.setRawResultN());
        checkCompletedNormally(a);
    }

    /**
     * propagateCompletion decrements pending count unless zero, in
     * which case causes completion, without invoking onCompletion
     */
    public void testPropagateCompletion() {
        NoopCC a = new NoopCC();
        assertEquals(0, a.getPendingCount());
        int n = 3;
        a.setPendingCount(n);
        for (; n > 0; n--) {
            assertEquals(n, a.getPendingCount());
            a.propagateCompletion();
            a.checkIncomplete();
            assertEquals(n - 1, a.getPendingCount());
        }
        a.propagateCompletion();
        assertEquals(0, a.computeN());
        assertEquals(0, a.onCompletionN());
        assertEquals(0, a.onExceptionalCompletionN());
        assertEquals(0, a.setRawResultN());
        checkCompletedNormally(a);
    }

    /**
     * firstComplete returns this if pending count is zero else null
     */
    public void testFirstComplete() {
        NoopCC a = new NoopCC();
        a.setPendingCount(1);
        assertNull(a.firstComplete());
        a.checkIncomplete();
        assertSame(a, a.firstComplete());
        a.checkIncomplete();
    }

    /**
     * firstComplete.nextComplete returns parent if pending count is
     * zero else null
     */
    public void testNextComplete() {
        NoopCC a = new NoopCC();
        NoopCC b = new NoopCC(a);
        a.setPendingCount(1);
        b.setPendingCount(1);
        assertNull(b.firstComplete());
        assertSame(b, b.firstComplete());
        assertNull(b.nextComplete());
        a.checkIncomplete();
        b.checkIncomplete();
        assertSame(a, b.nextComplete());
        assertSame(a, b.nextComplete());
        a.checkIncomplete();
        b.checkIncomplete();
        assertNull(a.nextComplete());
        b.checkIncomplete();
        checkCompletedNormally(a);
    }

    /**
     * quietlyCompleteRoot completes root task
     */
    public void testQuietlyCompleteRoot() {
        NoopCC a = new NoopCC();
        NoopCC b = new NoopCC(a);
        NoopCC c = new NoopCC(b);
        a.setPendingCount(1);
        b.setPendingCount(1);
        c.setPendingCount(1);
        c.quietlyCompleteRoot();
        assertTrue(a.isDone());
        assertFalse(b.isDone());
        assertFalse(c.isDone());
    }

    // Invocation tests use some interdependent task classes
    // to better test propagation etc

    /**
     * Version of Fibonacci with different classes for left vs right forks
     */
    abstract class CCF extends CheckedCC {
        int number;
        int rnumber;

        public CCF(CountedCompleter parent, int n) {
            super(parent, 1);
            this.number = n;
        }

        protected final void realCompute() {
            CCF f = this;
            int n = number;
            while (n >= 2) {
                new RCCF(f, n - 2).fork();
                f = new LCCF(f, --n);
            }
            f.complete(null);
        }
    }

    final class LCCF extends CCF {
        public LCCF(int n) { this(null, n); }
        public LCCF(CountedCompleter parent, int n) {
            super(parent, n);
        }
        public final void onCompletion(CountedCompleter caller) {
            super.onCompletion(caller);
            CCF p = (CCF)getCompleter();
            int n = number + rnumber;
            if (p != null)
                p.number = n;
            else
                number = n;
        }
    }
    final class RCCF extends CCF {
        public RCCF(CountedCompleter parent, int n) {
            super(parent, n);
        }
        public final void onCompletion(CountedCompleter caller) {
            super.onCompletion(caller);
            CCF p = (CCF)getCompleter();
            int n = number + rnumber;
            if (p != null)
                p.rnumber = n;
            else
                number = n;
        }
    }

    // Version of CCF with forced failure in left completions
    abstract class FailingCCF extends CheckedCC {
        int number;
        int rnumber;

        public FailingCCF(CountedCompleter parent, int n) {
            super(parent, 1);
            this.number = n;
        }

        protected final void realCompute() {
            FailingCCF f = this;
            int n = number;
            while (n >= 2) {
                new RFCCF(f, n - 2).fork();
                f = new LFCCF(f, --n);
            }
            f.complete(null);
        }
    }

    final class LFCCF extends FailingCCF {
        public LFCCF(int n) { this(null, n); }
        public LFCCF(CountedCompleter parent, int n) {
            super(parent, n);
        }
        public final void onCompletion(CountedCompleter caller) {
            super.onCompletion(caller);
            FailingCCF p = (FailingCCF)getCompleter();
            int n = number + rnumber;
            if (p != null)
                p.number = n;
            else
                number = n;
        }
    }
    final class RFCCF extends FailingCCF {
        public RFCCF(CountedCompleter parent, int n) {
            super(parent, n);
        }
        public final void onCompletion(CountedCompleter caller) {
            super.onCompletion(caller);
            completeExceptionally(new FJException());
        }
    }

    /**
     * invoke returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks; getRawResult returns null.
     */
    public void testInvoke() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertNull(f.invoke());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks
     */
    public void testQuietlyInvoke() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                f.quietlyInvoke();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task returns when task completes
     */
    public void testForkJoin() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertNull(f.join());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task returns when task completes
     */
    public void testForkGet() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertNull(f.get());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task returns when task completes
     */
    public void testForkTimedGet() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertNull(f.get(LONG_DELAY_MS, MILLISECONDS));
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get with null time unit throws NPE
     */
    public void testForkTimedGetNPE() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                try {
                    f.get(5L, null);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes
     */
    public void testForkQuietlyJoin() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * helpQuiesce returns when tasks are complete.
     * getQueuedTaskCount returns 0 when quiescent
     */
    public void testForkHelpQuiesce() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                helpQuiesce();
                assertEquals(21, f.number);
                assertEquals(0, getQueuedTaskCount());
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task throws exception when task completes abnormally
     */
    public void testAbnormalInvoke() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF f = new LFCCF(8);
                try {
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes abnormally
     */
    public void testAbnormalQuietlyInvoke() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF f = new LFCCF(8);
                f.quietlyInvoke();
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkJoin() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF f = new LFCCF(8);
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkGet() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FailingCCF f = new LFCCF(8);
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (ExecutionException success) {
                    Throwable cause = success.getCause();
                    assertTrue(cause instanceof FJException);
                    checkCompletedAbnormally(f, cause);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkTimedGet() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FailingCCF f = new LFCCF(8);
                assertSame(f, f.fork());
                try {
                    f.get(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (ExecutionException success) {
                    Throwable cause = success.getCause();
                    assertTrue(cause instanceof FJException);
                    checkCompletedAbnormally(f, cause);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes abnormally
     */
    public void testAbnormalForkQuietlyJoin() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF f = new LFCCF(8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task throws exception when task cancelled
     */
    public void testCancelledInvoke() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertTrue(f.cancel(true));
                try {
                    f.invoke();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkJoin() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkGet() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                CCF f = new LCCF(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkTimedGet() throws Exception {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                CCF f = new LCCF(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task cancelled
     */
    public void testCancelledForkQuietlyJoin() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                f.quietlyJoin();
                checkCancelled(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * getPool of executing task returns its pool
     */
    public void testGetPool() {
        final ForkJoinPool mainPool = mainPool();
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                assertSame(mainPool, getPool());
            }};
        testInvokeOnPool(mainPool, a);
    }

    /**
     * getPool of non-FJ task returns null
     */
    public void testGetPool2() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                assertNull(getPool());
            }};
        assertNull(a.invoke());
    }

    /**
     * inForkJoinPool of executing task returns true
     */
    public void testInForkJoinPool() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                assertTrue(inForkJoinPool());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * inForkJoinPool of non-FJ task returns false
     */
    public void testInForkJoinPool2() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                assertFalse(inForkJoinPool());
            }};
        assertNull(a.invoke());
    }

    /**
     * setRawResult(null) succeeds
     */
    public void testSetRawResult() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                setRawResult(null);
                assertNull(getRawResult());
            }};
        assertNull(a.invoke());
    }

    /**
     * invoke task throws exception after invoking completeExceptionally
     */
    public void testCompleteExceptionally2() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF n = new LCCF(8);
                CCF f = new LCCF(n, 8);
                FJException ex = new FJException();
                f.completeExceptionally(ex);
                f.checkCompletedExceptionally(ex);
                n.checkCompletedExceptionally(ex);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(t1, t2) invokes all task arguments
     */
    public void testInvokeAll2() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                CCF g = new LCCF(9);
                invokeAll(f, g);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument invokes task
     */
    public void testInvokeAll1() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                invokeAll(f);
                checkCompletedNormally(f);
                assertEquals(21, f.number);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument invokes tasks
     */
    public void testInvokeAll3() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                CCF g = new LCCF(9);
                CCF h = new LCCF(7);
                invokeAll(f, g, h);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                assertEquals(13, h.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(collection) invokes all tasks in the collection
     */
    public void testInvokeAllCollection() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                CCF g = new LCCF(9);
                CCF h = new LCCF(7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                invokeAll(set);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                assertEquals(13, h.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with any null task throws NPE
     */
    public void testInvokeAllNPE() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                CCF g = new LCCF(9);
                CCF h = null;
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(t1, t2) throw exception if any task does
     */
    public void testAbnormalInvokeAll2() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                FailingCCF g = new LFCCF(9);
                try {
                    invokeAll(f, g);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument throws exception if task does
     */
    public void testAbnormalInvokeAll1() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF g = new LFCCF(9);
                try {
                    invokeAll(g);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument throws exception if any task does
     */
    public void testAbnormalInvokeAll3() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                FailingCCF g = new LFCCF(9);
                CCF h = new LCCF(7);
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(collection) throws exception if any task does
     */
    public void testAbnormalInvokeAllCollection() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF f = new LFCCF(8);
                CCF g = new LCCF(9);
                CCF h = new LCCF(7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                try {
                    invokeAll(set);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * tryUnfork returns true for most recent unexecuted task,
     * and suppresses execution
     */
    public void testTryUnfork() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF g = new LCCF(9);
                assertSame(g, g.fork());
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertTrue(f.tryUnfork());
                helpQuiesce();
                checkNotDone(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * getSurplusQueuedTaskCount returns > 0 when
     * there are more tasks than threads
     */
    public void testGetSurplusQueuedTaskCount() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF h = new LCCF(7);
                assertSame(h, h.fork());
                CCF g = new LCCF(9);
                assertSame(g, g.fork());
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertTrue(getSurplusQueuedTaskCount() > 0);
                helpQuiesce();
                assertEquals(0, getSurplusQueuedTaskCount());
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * peekNextLocalTask returns most recent unexecuted task.
     */
    public void testPeekNextLocalTask() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF g = new LCCF(9);
                assertSame(g, g.fork());
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertSame(f, peekNextLocalTask());
                assertNull(f.join());
                checkCompletedNormally(f);
                helpQuiesce();
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * pollNextLocalTask returns most recent unexecuted task without
     * executing it
     */
    public void testPollNextLocalTask() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF g = new LCCF(9);
                assertSame(g, g.fork());
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertSame(f, pollNextLocalTask());
                helpQuiesce();
                checkNotDone(f);
                assertEquals(34, g.number);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * pollTask returns an unexecuted task without executing it
     */
    public void testPollTask() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF g = new LCCF(9);
                assertSame(g, g.fork());
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertSame(f, pollTask());
                helpQuiesce();
                checkNotDone(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * peekNextLocalTask returns least recent unexecuted task in async mode
     */
    public void testPeekNextLocalTaskAsync() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF g = new LCCF(9);
                assertSame(g, g.fork());
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertSame(g, peekNextLocalTask());
                assertNull(f.join());
                helpQuiesce();
                checkCompletedNormally(f);
                assertEquals(34, g.number);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    /**
     * pollNextLocalTask returns least recent unexecuted task without
     * executing it, in async mode
     */
    public void testPollNextLocalTaskAsync() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF g = new LCCF(9);
                assertSame(g, g.fork());
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertSame(g, pollNextLocalTask());
                helpQuiesce();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
                checkNotDone(g);
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    /**
     * pollTask returns an unexecuted task without executing it, in
     * async mode
     */
    public void testPollTaskAsync() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF g = new LCCF(9);
                assertSame(g, g.fork());
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertSame(g, pollTask());
                helpQuiesce();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
                checkNotDone(g);
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    // versions for singleton pools

    /**
     * invoke returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks; getRawResult returns null.
     */
    public void testInvokeSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertNull(f.invoke());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks
     */
    public void testQuietlyInvokeSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                f.quietlyInvoke();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * join of a forked task returns when task completes
     */
    public void testForkJoinSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertNull(f.join());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * get of a forked task returns when task completes
     */
    public void testForkGetSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertNull(f.get());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * timed get of a forked task returns when task completes
     */
    public void testForkTimedGetSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                assertNull(f.get(LONG_DELAY_MS, MILLISECONDS));
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * timed get with null time unit throws NPE
     */
    public void testForkTimedGetNPESingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                try {
                    f.get(5L, null);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes
     */
    public void testForkQuietlyJoinSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * helpQuiesce returns when tasks are complete.
     * getQueuedTaskCount returns 0 when quiescent
     */
    public void testForkHelpQuiesceSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertSame(f, f.fork());
                helpQuiesce();
                assertEquals(0, getQueuedTaskCount());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invoke task throws exception when task completes abnormally
     */
    public void testAbnormalInvokeSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF f = new LFCCF(8);
                try {
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes abnormally
     */
    public void testAbnormalQuietlyInvokeSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF f = new LFCCF(8);
                f.quietlyInvoke();
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkJoinSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF f = new LFCCF(8);
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkGetSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FailingCCF f = new LFCCF(8);
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (ExecutionException success) {
                    Throwable cause = success.getCause();
                    assertTrue(cause instanceof FJException);
                    checkCompletedAbnormally(f, cause);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkTimedGetSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FailingCCF f = new LFCCF(8);
                assertSame(f, f.fork());
                try {
                    f.get(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (ExecutionException success) {
                    Throwable cause = success.getCause();
                    assertTrue(cause instanceof FJException);
                    checkCompletedAbnormally(f, cause);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes abnormally
     */
    public void testAbnormalForkQuietlyJoinSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF f = new LFCCF(8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invoke task throws exception when task cancelled
     */
    public void testCancelledInvokeSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertTrue(f.cancel(true));
                try {
                    f.invoke();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkJoinSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkGetSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                CCF f = new LCCF(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkTimedGetSingleton() throws Exception {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                CCF f = new LCCF(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task cancelled
     */
    public void testCancelledForkQuietlyJoinSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                f.quietlyJoin();
                checkCancelled(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invoke task throws exception after invoking completeExceptionally
     */
    public void testCompleteExceptionallySingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF n = new LCCF(8);
                CCF f = new LCCF(n, 8);
                FJException ex = new FJException();
                f.completeExceptionally(ex);
                f.checkCompletedExceptionally(ex);
                n.checkCompletedExceptionally(ex);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(t1, t2) invokes all task arguments
     */
    public void testInvokeAll2Singleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                CCF g = new LCCF(9);
                invokeAll(f, g);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument invokes task
     */
    public void testInvokeAll1Singleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                invokeAll(f);
                checkCompletedNormally(f);
                assertEquals(21, f.number);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument invokes tasks
     */
    public void testInvokeAll3Singleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                CCF g = new LCCF(9);
                CCF h = new LCCF(7);
                invokeAll(f, g, h);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                assertEquals(13, h.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(collection) invokes all tasks in the collection
     */
    public void testInvokeAllCollectionSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                CCF g = new LCCF(9);
                CCF h = new LCCF(7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                invokeAll(set);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                assertEquals(13, h.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(tasks) with any null task throws NPE
     */
    public void testInvokeAllNPESingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                CCF g = new LCCF(9);
                CCF h = null;
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(t1, t2) throw exception if any task does
     */
    public void testAbnormalInvokeAll2Singleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                FailingCCF g = new LFCCF(9);
                try {
                    invokeAll(f, g);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument throws exception if task does
     */
    public void testAbnormalInvokeAll1Singleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF g = new LFCCF(9);
                try {
                    invokeAll(g);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument throws exception if any task does
     */
    public void testAbnormalInvokeAll3Singleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                CCF f = new LCCF(8);
                FailingCCF g = new LFCCF(9);
                CCF h = new LCCF(7);
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(collection) throws exception if any task does
     */
    public void testAbnormalInvokeAllCollectionSingleton() {
        ForkJoinTask a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingCCF f = new LFCCF(8);
                CCF g = new LCCF(9);
                CCF h = new LCCF(7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                try {
                    invokeAll(set);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

}
