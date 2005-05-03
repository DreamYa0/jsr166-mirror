/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util;

/**
 * A {@link SortedSet} extended with navigation methods reporting
 * closest matches for given search targets. Methods <tt>lower</tt>,
 * <tt>floor</tt>, <tt>ceiling</tt>, and <tt>higher</tt> return keys
 * respectively less than, less than or equal, greater than or equal,
 * and greater than a given key, returning <tt>null</tt> if there is
 * no such key.  A <tt>NavigableSet</tt> may be viewed and traversed
 * in either ascending or descending order.  The <tt>Collection</tt>
 * <tt>iterator</tt> method returns an ascending <tt>Iterator</tt> and
 * the additional method <tt>descendingIterator</tt> returns
 * descending iterator. The performance of ascending traversals is
 * likely to be faster than descending traversals.  This interface
 * additionally defines methods <tt>pollFirst</tt> and
 * <t>pollLast</tt> that return and remove the lowest and highest key,
 * if one exists, else returning <tt>null</tt>.
 * Methods <tt>navigableSubSet</tt>, <tt>navigableHeadSet</tt>, and
 * <tt>navigableTailSet</tt> differ from the similarly named
 * <tt>SortedSet</tt> methods only in that the returned sets
 * are guaranteed to obey the <tt>NavigableSet</tt> interface.
 *
 * <p> The return values of navigation methods may be ambiguous in
 * implementations that permit <tt>null</tt> elements. However, even
 * in this case the result can be disambiguated by checking
 * <tt>contains(null)</tt>. To avoid such issues, implementations of
 * this interface are encouraged <em>not</em> to permit insertion of
 * <tt>null</tt> elements. (Note that sorted sets of {@link
 * Comparable} elements intrinsically do not permit <tt>null</tt>.)
 *
 * <p>This interface is a member of the
 * <a href="{@docRoot}/../guide/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author Doug Lea
 * @param <E> the type of elements maintained by this set
 * @since 1.6
 */
public interface NavigableSet<E> extends SortedSet<E> {
    /**
     * Returns an element greater than or equal to the given element, or
     * <tt>null</tt> if there is no such element.
     *
     * @param e the value to match
     * @return an element greater than or equal to given element, or
     * <tt>null</tt> if there is no such element.
     * @throws ClassCastException if e cannot be compared with the elements
     *            currently in the set.
     * @throws NullPointerException if e is <tt>null</tt>
     * and this set does not permit <tt>null</tt> elements
     */
    E ceiling(E e);

    /**
     * Returns an element strictly less than the given element, or
     * <tt>null</tt> if there is no such element.
     *
     * @param e the value to match
     * @return the greatest element less than the given element, or
     * <tt>null</tt> if there is no such element.
     * @throws ClassCastException if e cannot be compared with the elements
     *            currently in the set.
     * @throws NullPointerException if e is <tt>null</tt>
     * and this set does not permit <tt>null</tt> elements
     */
    E lower(E e);

    /**
     * Returns an element less than or equal to the given element, or
     * <tt>null</tt> if there is no such element.
     *
     * @param e the value to match
     * @return the greatest element less than or equal to given
     * element, or <tt>null</tt> if there is no such element.
     * @throws ClassCastException if e cannot be compared with the elements
     *            currently in the set.
     * @throws NullPointerException if e is <tt>null</tt>.
     * and this set does not permit <tt>null</tt> elements
     */
    E floor(E e);

    /**
     * Returns an element strictly greater than the given element, or
     * <tt>null</tt> if there is no such element.
     *
     * @param e the value to match
     * @return the least element greater than the given element, or
     * <tt>null</tt> if there is no such element.
     * @throws ClassCastException if e cannot be compared with the elements
     *            currently in the set.
     * @throws NullPointerException if e is <tt>null</tt>
     * and this set does not permit <tt>null</tt> elements
     */
    E higher(E e);

    /**
     * Retrieves and removes the first (lowest) element.
     *
     * @return the first element, or <tt>null</tt> if empty.
     */
    E pollFirst();

    /**
     * Retrieves and removes the last (highest) element.
     *
     * @return the last element, or <tt>null</tt> if empty.
     */
    E pollLast();

    /**
     * Returns an iterator over the elements in this collection, in
     * descending order.
     *
     * @return an <tt>Iterator</tt> over the elements in this collection
     */
    Iterator<E> descendingIterator();

    /**
     * Returns a view of the portion of this set whose elements range
     * from <tt>fromElement</tt>, inclusive, to <tt>toElement</tt>,
     * exclusive.  (If <tt>fromElement</tt> and <tt>toElement</tt> are
     * equal, the returned navigable set is empty.)  The returned
     * navigable set is backed by this set, so changes in the returned
     * navigable set are reflected in this set, and vice-versa.
     *
     * @param fromElement low endpoint (inclusive) of the subSet.
     * @param toElement high endpoint (exclusive) of the subSet.
     * @return a view of the portion of this set whose elements range from
     * 	       <tt>fromElement</tt>, inclusive, to <tt>toElement</tt>,
     * 	       exclusive.
     * @throws ClassCastException if <tt>fromElement</tt> and
     *         <tt>toElement</tt> cannot be compared to one another using
     *         this set's comparator (or, if the set has no comparator,
     *         using natural ordering).
     * @throws IllegalArgumentException if <tt>fromElement</tt> is
     * greater than <tt>toElement</tt>.
     * @throws NullPointerException if <tt>fromElement</tt> or
     *	       <tt>toElement</tt> is <tt>null</tt>
     * and this set does not permit <tt>null</tt> elements
     */
    NavigableSet<E> navigableSubSet(E fromElement, E toElement);

    /**
     * Returns a view of the portion of this set whose elements are
     * strictly less than <tt>toElement</tt>.  The returned navigable
     * set is backed by this set, so changes in the returned navigable
     * set are reflected in this set, and vice-versa.
     * @param toElement high endpoint (exclusive) of the headSet.
     * @return a view of the portion of this set whose elements are strictly
     * 	       less than toElement.
     * @throws ClassCastException if <tt>toElement</tt> is not compatible
     *         with this set's comparator (or, if the set has no comparator,
     *         if <tt>toElement</tt> does not implement <tt>Comparable</tt>).
     * @throws NullPointerException if <tt>toElement</tt> is <tt>null</tt>
     * and this set does not permit <tt>null</tt> elements
     */
    NavigableSet<E> navigableHeadSet(E toElement);

    /**
     * Returns a view of the portion of this set whose elements are
     * greater than or equal to <tt>fromElement</tt>.  The returned
     * navigable set is backed by this set, so changes in the returned
     * navigable set are reflected in this set, and vice-versa.
     * @param fromElement low endpoint (inclusive) of the tailSet.
     * @return a view of the portion of this set whose elements are
     * greater than or equal to <tt>fromElement</tt>.
     * @throws ClassCastException if <tt>fromElement</tt> is not
     * compatible with this set's comparator (or, if the set has no
     * comparator, if <tt>fromElement</tt> does not implement
     * <tt>Comparable</tt>).
     * @throws NullPointerException if <tt>fromElement</tt> is <tt>null</tt>
     * and this set does not permit <tt>null</tt> elements
     */
    NavigableSet<E> navigableTailSet(E fromElement);
}
