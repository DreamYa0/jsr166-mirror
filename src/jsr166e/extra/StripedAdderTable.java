/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166e.extra;
import jsr166e.StripedAdder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.io.Serializable;

/**
 * A keyed table of scalable adders, that may be useful in computing
 * frequency counts and histograms, or may be used a form of multiset.
 * A {@link StripedAdder} is associated with each key. Keys may be
 * added to the table explicitly, and are also added implicitly upon
 * any attempt to update.
 *
 * <p>This class shares features and limitations of {@link
 * StripedAdder}: Update throughput is highly scalable, at the price
 * of potentially high space usage and the lack of any guarantees
 * about {@link #sum} returning atomic snapshots.
 *
 * @author Doug Lea
 */
public class StripedAdderTable<K> implements Serializable {
    /** Relies on default serialization */
    private static final long serialVersionUID = 7249369246863182397L;

    /** Concurrency parameter for map -- we assume high contention */
    private static final int MAP_SEGMENTS =
        Math.max(16, Runtime.getRuntime().availableProcessors());

    /** The underlying map */
    private final ConcurrentHashMap<K, StripedAdder> map;

    /**
     * Creates a new empty table.
     */
    public StripedAdderTable() {
        map = new ConcurrentHashMap<K, StripedAdder>(16, 0.75f, MAP_SEGMENTS);
    }

    /**
     * If the given key does not already exist in the table,
     * inserts the key with initial value of zero.
     *
     * @param key the key
     */
    public void add(K key)   { add(key, 0L); }

    /**
     * Increments the sum associated with the given key.  If the key
     * does not already exist in the table, it is inserted.
     *
     * @param key the key
     */
    public void increment(K key) { add(key, 1L); }

    /**
     * Decrements the sum associated with the given key.  If the key
     * does not already exist in the table, it is inserted.
     *
     * @param key the key
     */
    public void decrement(K key) { add(key, -1L); }

    /**
     * Adds the given value to the sum associated with the given
     * key.  If the key does not already exist in the table, it is
     * inserted.
     *
     * @param key the key
     * @param x the value to add
     */
    public void add(K key, long x) {
        StripedAdder a = map.get(key);
        if (a == null) {
            StripedAdder r = new StripedAdder();
            if ((a = map.putIfAbsent(key, r)) == null)
                a = r;
        }
        a.add(x);
    }

    /**
     * Returns the estimated sum associated with the given key, or
     * zero if the key does not currently exist in the table.
     *
     * @param key the key
     * @return the sum associated with the key, or zero if the key is
     * not in the table
     */
    public long sum(K key) {
        StripedAdder a = map.get(key);
        return a == null ? 0L : a.sum();
    }

    /**
     * Resets the sum associated with the given key to zero if the key
     * exists in the table; returning the previous estimated sum.
     * This method does <em>NOT</em> add or remove the key from the
     * table (see {@link #remove}).
     *
     * @param key the key
     * @return the estimated previous sum, or zero if the key is not
     * in the table
     */
    public long reset(K key) {
        StripedAdder a = map.get(key);
        return (a == null) ? 0L : a.reset();
    }

    /**
     * Returns the estimated sum totalled across all keys.
     *
     * @return the estimated sum totalled across all keys.
     */
    public long sumAll() {
        long sum = 0L;
        for (StripedAdder a : map.values())
            sum += a.sum();
        return sum;
    }

    /**
     * Resets the sum associated with each key to zero,
     * returning the estimated previous total.
     *
     * @return the estimated previous total
     */
    public long resetAll() {
        long sum = 0L;
        for (StripedAdder a : map.values())
            sum += a.reset();
        return sum;
    }

    /**
     * Removes the the given key from the table.
     *
     * @param key the key
     */
    public void remove(K key) { map.remove(key); }

    /**
     * Returns the current set of keys.
     *
     * @return the current set of keys
     */
    public Set<K> keys() {
        return map.keySet();
    }

    /**
     * Returns the current set of key-value mappings.
     *
     * @return the current set of key-value mappings
     */
    public Set<Map.Entry<K,StripedAdder>> mappings() {
        return map.entrySet();
    }

}