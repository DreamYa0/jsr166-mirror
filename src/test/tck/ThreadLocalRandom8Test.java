/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
import junit.framework.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class ThreadLocalRandom8Test extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(ThreadLocalRandom8Test.class);
    }

    // max sampled int bound
    static final int MAX_INT_BOUND = (1 << 28);

    // max sampled long bound
    static final long MAX_LONG_BOUND = (1L << 42);

    // Number of replications for other checks
    static final int REPS = 20;

    /**
     * Invoking sized ints, long, doubles, with negative sizes throws
     * IllegalArgumentException
     */
    public void testBadStreamSize() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        try {
            java.util.stream.IntStream x = r.ints(-1L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.IntStream x = r.ints(-1L, 2, 3);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.LongStream x = r.longs(-1L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.LongStream x = r.longs(-1L, -1L, 1L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.DoubleStream x = r.doubles(-1L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.DoubleStream x = r.doubles(-1L, .5, .6);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * Invoking bounded ints, long, doubles, with illegal bounds throws
     * IllegalArgumentException
     */
    public void testBadStreamBounds() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        try {
            java.util.stream.IntStream x = r.ints(2, 1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.IntStream x = r.ints(10, 42, 42);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.LongStream x = r.longs(-1L, -1L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.LongStream x = r.longs(10, 1L, -2L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.DoubleStream x = r.doubles(0.0, 0.0);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.DoubleStream x = r.doubles(10, .5, .4);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * A parallel sized stream of ints generates the given number of values
     */
    public void testIntsCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 0;
        for (int reps = 0; reps < REPS; ++reps) {
            counter.reset();
            r.ints(size).parallel().forEach(x -> {counter.increment();});
            assertEquals(size, counter.sum());
            size += 524959;
        }
    }

    /**
     * A parallel sized stream of longs generates the given number of values
     */
    public void testLongsCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 0;
        for (int reps = 0; reps < REPS; ++reps) {
            counter.reset();
            r.longs(size).parallel().forEach(x -> {counter.increment();});
            assertEquals(size, counter.sum());
            size += 524959;
        }
    }

    /**
     * A parallel sized stream of doubles generates the given number of values
     */
    public void testDoublesCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 0;
        for (int reps = 0; reps < REPS; ++reps) {
            counter.reset();
            r.doubles(size).parallel().forEach(x -> {counter.increment();});
            assertEquals(size, counter.sum());
            size += 524959;
        }
    }

    /**
     * Each of a parallel sized stream of bounded ints is within bounds
     */
    public void testBoundedInts() {
        AtomicInteger fails = new AtomicInteger(0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 12345L;
        for (int least = -15485867; least < MAX_INT_BOUND; least += 524959) {
            for (int bound = least + 2; bound > least && bound < MAX_INT_BOUND; bound += 67867967) {
                final int lo = least, hi = bound;
                r.ints(size, lo, hi).parallel().
                    forEach(x -> {if (x < lo || x >= hi)
                                fails.getAndIncrement(); });
            }
        }
        assertEquals(0, fails.get());
    }

    /**
     * Each of a parallel sized stream of bounded longs is within bounds
     */
    public void testBoundedLongs() {
        AtomicInteger fails = new AtomicInteger(0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 123L;
        for (long least = -86028121; least < MAX_LONG_BOUND; least += 1982451653L) {
            for (long bound = least + 2; bound > least && bound < MAX_LONG_BOUND; bound += Math.abs(bound * 7919)) {
                final long lo = least, hi = bound;
                r.longs(size, lo, hi).parallel().
                    forEach(x -> {if (x < lo || x >= hi)
                                fails.getAndIncrement(); });
            }
        }
        assertEquals(0, fails.get());
    }

    /**
     * Each of a parallel sized stream of bounded doubles is within bounds
     */
    public void testBoundedDoubles() {
        AtomicInteger fails = new AtomicInteger(0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 456;
        for (double least = 0.00011; least < 1.0e20; least *= 9) {
            for (double bound = least * 1.0011; bound < 1.0e20; bound *= 17) {
                final double lo = least, hi = bound;
                r.doubles(size, lo, hi).parallel().
                    forEach(x -> {if (x < lo || x >= hi)
                                fails.getAndIncrement(); });
            }
        }
        assertEquals(0, fails.get());
    }

    /**
     * A parallel unsized stream of ints generates at least 100 values
     */
    public void testUnsizedIntsCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.ints().limit(size).parallel().forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

    /**
     * A parallel unsized stream of longs generates at least 100 values
     */
    public void testUnsizedLongsCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.longs().limit(size).parallel().forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

    /**
     * A parallel unsized stream of doubles generates at least 100 values
     */
    public void testUnsizedDoublesCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.doubles().limit(size).parallel().forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

    /**
     * A sequential unsized stream of ints generates at least 100 values
     */
    public void testUnsizedIntsCountSeq() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.ints().limit(size).forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

    /**
     * A sequential unsized stream of longs generates at least 100 values
     */
    public void testUnsizedLongsCountSeq() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.longs().limit(size).forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

    /**
     * A sequential unsized stream of doubles generates at least 100 values
     */
    public void testUnsizedDoublesCountSeq() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.doubles().limit(size).forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

}