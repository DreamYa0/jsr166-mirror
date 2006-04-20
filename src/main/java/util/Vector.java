/*
 * @(#)Vector.java	1.103 05/12/06
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util;

/**
 * The <code>Vector</code> class implements a growable array of
 * objects. Like an array, it contains components that can be
 * accessed using an integer index. However, the size of a
 * <code>Vector</code> can grow or shrink as needed to accommodate
 * adding and removing items after the <code>Vector</code> has been created.
 *
 * <p>Each vector tries to optimize storage management by maintaining a
 * <code>capacity</code> and a <code>capacityIncrement</code>. The
 * <code>capacity</code> is always at least as large as the vector
 * size; it is usually larger because as components are added to the
 * vector, the vector's storage increases in chunks the size of
 * <code>capacityIncrement</code>. An application can increase the
 * capacity of a vector before inserting a large number of
 * components; this reduces the amount of incremental reallocation.
 *
 * <p>The Iterators returned by Vector's iterator and listIterator
 * methods are <em>fail-fast</em>: if the Vector is structurally modified
 * at any time after the Iterator is created, in any way except through the
 * Iterator's own remove or add methods, the Iterator will throw a
 * ConcurrentModificationException.  Thus, in the face of concurrent
 * modification, the Iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the future.
 * The Enumerations returned by Vector's elements method are <em>not</em>
 * fail-fast.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw <tt>ConcurrentModificationException</tt> on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:  <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <p>As of the Java 2 platform v1.2, this class was retrofitted to
 * implement the {@link List} interface, making it a member of the
 * <a href="{@docRoot}/../guide/collections/index.html"> Java
 * Collections Framework</a>.  Unlike the new collection
 * implementations, {@code Vector} is synchronized.
 *
 * @author  Lee Boynton
 * @author  Jonathan Payne
 * @version 1.103, 12/06/05
 * @see Collection
 * @see List
 * @see ArrayList
 * @see LinkedList
 * @since   JDK1.0
 */
public class Vector<E>
    extends AbstractList<E>
    implements List<E>, RandomAccess, Cloneable, java.io.Serializable
{
    /**
     * The array buffer into which the components of the vector are
     * stored. The capacity of the vector is the length of this array buffer,
     * and is at least large enough to contain all the vector's elements.<p>
     *
     * Any array elements following the last element in the Vector are null.
     *
     * @serial
     */
    protected Object[] elementData;

    /**
     * The number of valid components in this <tt>Vector</tt> object.
     * Components <tt>elementData[0]</tt> through
     * <tt>elementData[elementCount-1]</tt> are the actual items.
     *
     * @serial
     */
    protected int elementCount;

    /**
     * The amount by which the capacity of the vector is automatically
     * incremented when its size becomes greater than its capacity.  If
     * the capacity increment is less than or equal to zero, the capacity
     * of the vector is doubled each time it needs to grow.
     *
     * @serial
     */
    protected int capacityIncrement;

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = -2767605614048989439L;

    /**
     * Constructs an empty vector with the specified initial capacity and
     * capacity increment.
     *
     * @param   initialCapacity     the initial capacity of the vector
     * @param   capacityIncrement   the amount by which the capacity is
     *                              increased when the vector overflows
     * @exception IllegalArgumentException if the specified initial capacity
     *               is negative
     */
    public Vector(int initialCapacity, int capacityIncrement) {
	super();
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Capacity: "+
                                               initialCapacity);
	this.elementData = new Object[initialCapacity];
	this.capacityIncrement = capacityIncrement;
    }

    /**
     * Constructs an empty vector with the specified initial capacity and
     * with its capacity increment equal to zero.
     *
     * @param   initialCapacity   the initial capacity of the vector
     * @exception IllegalArgumentException if the specified initial capacity
     *               is negative
     */
    public Vector(int initialCapacity) {
	this(initialCapacity, 0);
    }

    /**
     * Constructs an empty vector so that its internal data array
     * has size <tt>10</tt> and its standard capacity increment is
     * zero.
     */
    public Vector() {
	this(10);
    }

    /**
     * Constructs a vector containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.
     *
     * @param c the collection whose elements are to be placed into this
     *       vector
     * @throws NullPointerException if the specified collection is null
     * @since   1.2
     */
    public Vector(Collection<? extends E> c) {
	elementData = c.toArray();
	elementCount = elementData.length;
	// c.toArray might (incorrectly) not return Object[] (see 6260652)
	if (elementData.getClass() != Object[].class)
	    elementData = Arrays.copyOf(elementData, elementCount, Object[].class);
    }

    /**
     * Copies the components of this vector into the specified array.
     * The item at index <tt>k</tt> in this vector is copied into
     * component <tt>k</tt> of <tt>anArray</tt>.
     *
     * @param  anArray the array into which the components get copied
     * @throws NullPointerException if the given array is null
     * @throws IndexOutOfBoundsException if the specified array is not
     *         large enough to hold all the components of this vector
     * @throws ArrayStoreException if a component of this vector is not of
     *         a runtime type that can be stored in the specified array
     * @see #toArray(Object[])
     */
    public synchronized void copyInto(Object[] anArray) {
	System.arraycopy(elementData, 0, anArray, 0, elementCount);
    }

    /**
     * Trims the capacity of this vector to be the vector's current
     * size. If the capacity of this vector is larger than its current
     * size, then the capacity is changed to equal the size by replacing
     * its internal data array, kept in the field <tt>elementData</tt>,
     * with a smaller one. An application can use this operation to
     * minimize the storage of a vector.
     */
    public synchronized void trimToSize() {
	modCount++;
	int oldCapacity = elementData.length;
	if (elementCount < oldCapacity) {
            elementData = Arrays.copyOf(elementData, elementCount);
	}
    }

    /**
     * Increases the capacity of this vector, if necessary, to ensure
     * that it can hold at least the number of components specified by
     * the minimum capacity argument.
     *
     * <p>If the current capacity of this vector is less than
     * <tt>minCapacity</tt>, then its capacity is increased by replacing its
     * internal data array, kept in the field <tt>elementData</tt>, with a
     * larger one.  The size of the new data array will be the old size plus
     * <tt>capacityIncrement</tt>, unless the value of
     * <tt>capacityIncrement</tt> is less than or equal to zero, in which case
     * the new capacity will be twice the old capacity; but if this new size
     * is still smaller than <tt>minCapacity</tt>, then the new capacity will
     * be <tt>minCapacity</tt>.
     *
     * @param minCapacity the desired minimum capacity
     */
    public synchronized void ensureCapacity(int minCapacity) {
	modCount++;
	ensureCapacityHelper(minCapacity);
    }

    /**
     * This implements the unsynchronized semantics of ensureCapacity.
     * Synchronized methods in this class can internally call this
     * method for ensuring capacity without incurring the cost of an
     * extra synchronization.
     *
     * @see java.util.Vector#ensureCapacity(int)
     */
    private void ensureCapacityHelper(int minCapacity) {
	int oldCapacity = elementData.length;
	if (minCapacity > oldCapacity) {
	    Object[] oldData = elementData;
	    int newCapacity = (capacityIncrement > 0) ?
		(oldCapacity + capacityIncrement) : (oldCapacity * 2);
    	    if (newCapacity < minCapacity) {
		newCapacity = minCapacity;
	    }
            elementData = Arrays.copyOf(elementData, newCapacity);
	}
    }

    /**
     * Sets the size of this vector. If the new size is greater than the
     * current size, new <code>null</code> items are added to the end of
     * the vector. If the new size is less than the current size, all
     * components at index <code>newSize</code> and greater are discarded.
     *
     * @param   newSize   the new size of this vector
     * @throws  ArrayIndexOutOfBoundsException if new size is negative
     */
    public synchronized void setSize(int newSize) {
	modCount++;
	if (newSize > elementCount) {
	    ensureCapacityHelper(newSize);
	} else {
	    for (int i = newSize ; i < elementCount ; i++) {
		elementData[i] = null;
	    }
	}
	elementCount = newSize;
    }

    /**
     * Returns the current capacity of this vector.
     *
     * @return  the current capacity (the length of its internal
     *          data array, kept in the field <tt>elementData</tt>
     *          of this vector)
     */
    public synchronized int capacity() {
	return elementData.length;
    }

    /**
     * Returns the number of components in this vector.
     *
     * @return  the number of components in this vector
     */
    public synchronized int size() {
	return elementCount;
    }

    /**
     * Tests if this vector has no components.
     *
     * @return  <code>true</code> if and only if this vector has
     *          no components, that is, its size is zero;
     *          <code>false</code> otherwise.
     */
    public synchronized boolean isEmpty() {
	return elementCount == 0;
    }

    /**
     * Returns an enumeration of the components of this vector. The
     * returned <tt>Enumeration</tt> object will generate all items in
     * this vector. The first item generated is the item at index <tt>0</tt>,
     * then the item at index <tt>1</tt>, and so on.
     *
     * @return  an enumeration of the components of this vector
     * @see     Enumeration
     * @see     Iterator
     */
    public Enumeration<E> elements() {
	return new Enumeration<E>() {
	    int count = 0;

	    public boolean hasMoreElements() {
		return count < elementCount;
	    }

	    public E nextElement() {
		synchronized (Vector.this) {
		    if (count < elementCount) {
			return (E)elementData[count++];
		    }
		}
		throw new NoSuchElementException("Vector Enumeration");
	    }
	};
    }

    /**
     * Returns <tt>true</tt> if this vector contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this vector
     * contains at least one element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this vector is to be tested
     * @return <tt>true</tt> if this vector contains the specified element
     */
    public boolean contains(Object o) {
	return indexOf(o, 0) >= 0;
    }

    /**
     * Returns the index of the first occurrence of the specified element
     * in this vector, or -1 if this vector does not contain the element.
     * More formally, returns the lowest index <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>,
     * or -1 if there is no such index.
     *
     * @param o element to search for
     * @return the index of the first occurrence of the specified element in
     *         this vector, or -1 if this vector does not contain the element
     */
    public int indexOf(Object o) {
	return indexOf(o, 0);
    }

    /**
     * Returns the index of the first occurrence of the specified element in
     * this vector, searching forwards from <tt>index</tt>, or returns -1 if
     * the element is not found.
     * More formally, returns the lowest index <tt>i</tt> such that
     * <tt>(i&nbsp;&gt;=&nbsp;index&nbsp;&amp;&amp;&nbsp;(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i))))</tt>,
     * or -1 if there is no such index.
     *
     * @param o element to search for
     * @param index index to start searching from
     * @return the index of the first occurrence of the element in
     *         this vector at position <tt>index</tt> or later in the vector;
     *         <tt>-1</tt> if the element is not found.
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @see     Object#equals(Object)
     */
    public synchronized int indexOf(Object o, int index) {
	if (o == null) {
	    for (int i = index ; i < elementCount ; i++)
		if (elementData[i]==null)
		    return i;
	} else {
	    for (int i = index ; i < elementCount ; i++)
		if (o.equals(elementData[i]))
		    return i;
	}
	return -1;
    }

    /**
     * Returns the index of the last occurrence of the specified element
     * in this vector, or -1 if this vector does not contain the element.
     * More formally, returns the highest index <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>,
     * or -1 if there is no such index.
     *
     * @param o element to search for
     * @return the index of the last occurrence of the specified element in
     *         this vector, or -1 if this vector does not contain the element
     */
    public synchronized int lastIndexOf(Object o) {
	return lastIndexOf(o, elementCount-1);
    }

    /**
     * Returns the index of the last occurrence of the specified element in
     * this vector, searching backwards from <tt>index</tt>, or returns -1 if
     * the element is not found.
     * More formally, returns the highest index <tt>i</tt> such that
     * <tt>(i&nbsp;&lt;=&nbsp;index&nbsp;&amp;&amp;&nbsp;(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i))))</tt>,
     * or -1 if there is no such index.
     *
     * @param o element to search for
     * @param index index to start searching backwards from
     * @return the index of the last occurrence of the element at position
     *         less than or equal to <tt>index</tt> in this vector;
     *         -1 if the element is not found.
     * @throws IndexOutOfBoundsException if the specified index is greater
     *         than or equal to the current size of this vector
     */
    public synchronized int lastIndexOf(Object o, int index) {
        if (index >= elementCount)
            throw new IndexOutOfBoundsException(index + " >= "+ elementCount);

	if (o == null) {
	    for (int i = index; i >= 0; i--)
		if (elementData[i]==null)
		    return i;
	} else {
	    for (int i = index; i >= 0; i--)
		if (o.equals(elementData[i]))
		    return i;
	}
	return -1;
    }

    /**
     * Returns the component at the specified index.<p>
     *
     * This method is identical in functionality to the get method
     * (which is part of the List interface).
     *
     * @param      index   an index into this vector
     * @return     the component at the specified index
     * @exception  ArrayIndexOutOfBoundsException  if the <tt>index</tt>
     *             is negative or not less than the current size of this
     *             <tt>Vector</tt> object.
     * @see	   #get(int)
     * @see	   List
     */
    public synchronized E elementAt(int index) {
	if (index >= elementCount) {
	    throw new ArrayIndexOutOfBoundsException(index + " >= " + elementCount);
	}

        return (E)elementData[index];
    }

    /**
     * Returns the first component (the item at index <tt>0</tt>) of
     * this vector.
     *
     * @return     the first component of this vector
     * @exception  NoSuchElementException  if this vector has no components
     */
    public synchronized E firstElement() {
	if (elementCount == 0) {
	    throw new NoSuchElementException();
	}
	return (E)elementData[0];
    }

    /**
     * Returns the last component of the vector.
     *
     * @return  the last component of the vector, i.e., the component at index
     *          <code>size()&nbsp;-&nbsp;1</code>.
     * @exception  NoSuchElementException  if this vector is empty
     */
    public synchronized E lastElement() {
	if (elementCount == 0) {
	    throw new NoSuchElementException();
	}
	return (E)elementData[elementCount - 1];
    }

    /**
     * Sets the component at the specified <code>index</code> of this
     * vector to be the specified object. The previous component at that
     * position is discarded.<p>
     *
     * The index must be a value greater than or equal to <code>0</code>
     * and less than the current size of the vector. <p>
     *
     * This method is identical in functionality to the set method
     * (which is part of the List interface). Note that the set method reverses
     * the order of the parameters, to more closely match array usage.  Note
     * also that the set method returns the old value that was stored at the
     * specified position.
     *
     * @param      obj     what the component is to be set to
     * @param      index   the specified index
     * @exception  ArrayIndexOutOfBoundsException  if the index was invalid
     * @see        #size()
     * @see        List
     * @see	   #set(int, java.lang.Object)
     */
    public synchronized void setElementAt(E obj, int index) {
	if (index >= elementCount) {
	    throw new ArrayIndexOutOfBoundsException(index + " >= " +
						     elementCount);
	}
	elementData[index] = obj;
    }

    /**
     * Deletes the component at the specified index. Each component in
     * this vector with an index greater or equal to the specified
     * <code>index</code> is shifted downward to have an index one
     * smaller than the value it had previously. The size of this vector
     * is decreased by <tt>1</tt>.<p>
     *
     * The index must be a value greater than or equal to <code>0</code>
     * and less than the current size of the vector. <p>
     *
     * This method is identical in functionality to the remove method
     * (which is part of the List interface).  Note that the remove method
     * returns the old value that was stored at the specified position.
     *
     * @param      index   the index of the object to remove
     * @exception  ArrayIndexOutOfBoundsException  if the index was invalid
     * @see        #size()
     * @see	   #remove(int)
     * @see	   List
     */
    public synchronized void removeElementAt(int index) {
	modCount++;
	if (index >= elementCount) {
	    throw new ArrayIndexOutOfBoundsException(index + " >= " +
						     elementCount);
	}
	else if (index < 0) {
	    throw new ArrayIndexOutOfBoundsException(index);
	}
	int j = elementCount - index - 1;
	if (j > 0) {
	    System.arraycopy(elementData, index + 1, elementData, index, j);
	}
	elementCount--;
	elementData[elementCount] = null; /* to let gc do its work */
    }

    /**
     * Inserts the specified object as a component in this vector at the
     * specified <code>index</code>. Each component in this vector with
     * an index greater or equal to the specified <code>index</code> is
     * shifted upward to have an index one greater than the value it had
     * previously. <p>
     *
     * The index must be a value greater than or equal to <code>0</code>
     * and less than or equal to the current size of the vector. (If the
     * index is equal to the current size of the vector, the new element
     * is appended to the Vector.)<p>
     *
     * This method is identical in functionality to the add(Object, int) method
     * (which is part of the List interface). Note that the add method reverses
     * the order of the parameters, to more closely match array usage.
     *
     * @param      obj     the component to insert
     * @param      index   where to insert the new component
     * @exception  ArrayIndexOutOfBoundsException  if the index was invalid
     * @see        #size()
     * @see	   #add(int, Object)
     * @see	   List
     */
    public synchronized void insertElementAt(E obj, int index) {
	modCount++;
	if (index > elementCount) {
	    throw new ArrayIndexOutOfBoundsException(index
						     + " > " + elementCount);
	}
	ensureCapacityHelper(elementCount + 1);
	System.arraycopy(elementData, index, elementData, index + 1, elementCount - index);
	elementData[index] = obj;
	elementCount++;
    }

    /**
     * Adds the specified component to the end of this vector,
     * increasing its size by one. The capacity of this vector is
     * increased if its size becomes greater than its capacity. <p>
     *
     * This method is identical in functionality to the add(Object) method
     * (which is part of the List interface).
     *
     * @param   obj   the component to be added
     * @see	   #add(Object)
     * @see	   List
     */
    public synchronized void addElement(E obj) {
	modCount++;
	ensureCapacityHelper(elementCount + 1);
	elementData[elementCount++] = obj;
    }

    /**
     * Removes the first (lowest-indexed) occurrence of the argument
     * from this vector. If the object is found in this vector, each
     * component in the vector with an index greater or equal to the
     * object's index is shifted downward to have an index one smaller
     * than the value it had previously.<p>
     *
     * This method is identical in functionality to the remove(Object)
     * method (which is part of the List interface).
     *
     * @param   obj   the component to be removed
     * @return  <code>true</code> if the argument was a component of this
     *          vector; <code>false</code> otherwise.
     * @see	List#remove(Object)
     * @see	List
     */
    public synchronized boolean removeElement(Object obj) {
	modCount++;
	int i = indexOf(obj);
	if (i >= 0) {
	    removeElementAt(i);
	    return true;
	}
	return false;
    }

    /**
     * Removes all components from this vector and sets its size to zero.<p>
     *
     * This method is identical in functionality to the clear method
     * (which is part of the List interface).
     *
     * @see	#clear
     * @see	List
     */
    public synchronized void removeAllElements() {
        modCount++;
	// Let gc do its work
	for (int i = 0; i < elementCount; i++)
	    elementData[i] = null;

	elementCount = 0;
    }

    /**
     * Returns a clone of this vector. The copy will contain a
     * reference to a clone of the internal data array, not a reference
     * to the original internal data array of this <tt>Vector</tt> object.
     *
     * @return  a clone of this vector
     */
    public synchronized Object clone() {
	try {
	    Vector<E> v = (Vector<E>) super.clone();
	    v.elementData = Arrays.copyOf(elementData, elementCount);
	    v.modCount = 0;
	    return v;
	} catch (CloneNotSupportedException e) {
	    // this shouldn't happen, since we are Cloneable
	    throw new InternalError();
	}
    }

    /**
     * Returns an array containing all of the elements in this Vector
     * in the correct order.
     *
     * @since 1.2
     */
    public synchronized Object[] toArray() {
        return Arrays.copyOf(elementData, elementCount);
    }

    /**
     * Returns an array containing all of the elements in this Vector in the
     * correct order; the runtime type of the returned array is that of the
     * specified array.  If the Vector fits in the specified array, it is
     * returned therein.  Otherwise, a new array is allocated with the runtime
     * type of the specified array and the size of this Vector.<p>
     *
     * If the Vector fits in the specified array with room to spare
     * (i.e., the array has more elements than the Vector),
     * the element in the array immediately following the end of the
     * Vector is set to null.  (This is useful in determining the length
     * of the Vector <em>only</em> if the caller knows that the Vector
     * does not contain any null elements.)
     *
     * @param a the array into which the elements of the Vector are to
     *		be stored, if it is big enough; otherwise, a new array of the
     * 		same runtime type is allocated for this purpose.
     * @return an array containing the elements of the Vector
     * @exception ArrayStoreException the runtime type of a is not a supertype
     * of the runtime type of every element in this Vector
     * @throws NullPointerException if the given array is null
     * @since 1.2
     */
    public synchronized <T> T[] toArray(T[] a) {
        if (a.length < elementCount)
            return (T[]) Arrays.copyOf(elementData, elementCount, a.getClass());

	System.arraycopy(elementData, 0, a, 0, elementCount);

        if (a.length > elementCount)
            a[elementCount] = null;

        return a;
    }

    // Positional Access Operations

    /**
     * Returns the element at the specified position in this Vector.
     *
     * @param index index of the element to return
     * @return object at the specified index
     * @exception ArrayIndexOutOfBoundsException index is out of range (index
     * 		  &lt; 0 || index &gt;= size())
     * @since 1.2
     */
    public synchronized E get(int index) {
	if (index >= elementCount)
	    throw new ArrayIndexOutOfBoundsException(index);

	return (E)elementData[index];
    }

    /**
     * Replaces the element at the specified position in this Vector with the
     * specified element.
     *
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @exception ArrayIndexOutOfBoundsException index out of range
     *		  (index &lt; 0 || index &gt;= size())
     * @since 1.2
     */
    public synchronized E set(int index, E element) {
	if (index >= elementCount)
	    throw new ArrayIndexOutOfBoundsException(index);

	Object oldValue = elementData[index];
	elementData[index] = element;
	return (E)oldValue;
    }

    /**
     * Appends the specified element to the end of this Vector.
     *
     * @param e element to be appended to this Vector
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     * @since 1.2
     */
    public synchronized boolean add(E e) {
	modCount++;
	ensureCapacityHelper(elementCount + 1);
	elementData[elementCount++] = e;
        return true;
    }

    /**
     * Removes the first occurrence of the specified element in this Vector
     * If the Vector does not contain the element, it is unchanged.  More
     * formally, removes the element with the lowest index i such that
     * <code>(o==null ? get(i)==null : o.equals(get(i)))</code> (if such
     * an element exists).
     *
     * @param o element to be removed from this Vector, if present
     * @return true if the Vector contained the specified element
     * @since 1.2
     */
    public boolean remove(Object o) {
        return removeElement(o);
    }

    /**
     * Inserts the specified element at the specified position in this Vector.
     * Shifts the element currently at that position (if any) and any
     * subsequent elements to the right (adds one to their indices).
     *
     * @param index index at which the specified element is to be inserted
     * @param element element to be inserted
     * @exception ArrayIndexOutOfBoundsException index is out of range
     *		  (index &lt; 0 || index &gt; size())
     * @since 1.2
     */
    public void add(int index, E element) {
        insertElementAt(element, index);
    }

    /**
     * Removes the element at the specified position in this Vector.
     * Shifts any subsequent elements to the left (subtracts one from their
     * indices).  Returns the element that was removed from the Vector.
     *
     * @exception ArrayIndexOutOfBoundsException index out of range (index
     * 		  &lt; 0 || index &gt;= size())
     * @param index the index of the element to be removed
     * @return element that was removed
     * @since 1.2
     */
    public synchronized E remove(int index) {
	modCount++;
	if (index >= elementCount)
	    throw new ArrayIndexOutOfBoundsException(index);
	Object oldValue = elementData[index];

	int numMoved = elementCount - index - 1;
	if (numMoved > 0)
	    System.arraycopy(elementData, index+1, elementData, index,
			     numMoved);
	elementData[--elementCount] = null; // Let gc do its work

	return (E)oldValue;
    }

    /**
     * Removes all of the elements from this Vector.  The Vector will
     * be empty after this call returns (unless it throws an exception).
     *
     * @since 1.2
     */
    public void clear() {
        removeAllElements();
    }

    // Bulk Operations

    /**
     * Returns true if this Vector contains all of the elements in the
     * specified Collection.
     *
     * @param   c a collection whose elements will be tested for containment
     *          in this Vector
     * @return true if this Vector contains all of the elements in the
     *	       specified collection
     * @throws NullPointerException if the specified collection is null
     */
    public synchronized boolean containsAll(Collection<?> c) {
        return super.containsAll(c);
    }

    /**
     * Appends all of the elements in the specified Collection to the end of
     * this Vector, in the order that they are returned by the specified
     * Collection's Iterator.  The behavior of this operation is undefined if
     * the specified Collection is modified while the operation is in progress.
     * (This implies that the behavior of this call is undefined if the
     * specified Collection is this Vector, and this Vector is nonempty.)
     *
     * @param c elements to be inserted into this Vector
     * @return <tt>true</tt> if this Vector changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     * @since 1.2
     */
    public synchronized boolean addAll(Collection<? extends E> c) {
	modCount++;
        Object[] a = c.toArray();
        int numNew = a.length;
	ensureCapacityHelper(elementCount + numNew);
        System.arraycopy(a, 0, elementData, elementCount, numNew);
        elementCount += numNew;
	return numNew != 0;
    }

    /**
     * Removes from this Vector all of its elements that are contained in the
     * specified Collection.
     *
     * @param c a collection of elements to be removed from the Vector
     * @return true if this Vector changed as a result of the call
     * @throws ClassCastException if the types of one or more elements
     *         in this vector are incompatible with the specified
     *         collection (optional)
     * @throws NullPointerException if this vector contains one or more null
     *         elements and the specified collection does not support null
     *         elements (optional), or if the specified collection is null
     * @since 1.2
     */
    public synchronized boolean removeAll(Collection<?> c) {
        return super.removeAll(c);
    }

    /**
     * Retains only the elements in this Vector that are contained in the
     * specified Collection.  In other words, removes from this Vector all
     * of its elements that are not contained in the specified Collection.
     *
     * @param c a collection of elements to be retained in this Vector
     *          (all other elements are removed)
     * @return true if this Vector changed as a result of the call
     * @throws ClassCastException if the types of one or more elements
     *         in this vector are incompatible with the specified
     *         collection (optional)
     * @throws NullPointerException if this vector contains one or more null
     *         elements and the specified collection does not support null
     *         elements (optional), or if the specified collection is null
     * @since 1.2
     */
    public synchronized boolean retainAll(Collection<?> c)  {
        return super.retainAll(c);
    }

    /**
     * Inserts all of the elements in the specified Collection into this
     * Vector at the specified position.  Shifts the element currently at
     * that position (if any) and any subsequent elements to the right
     * (increases their indices).  The new elements will appear in the Vector
     * in the order that they are returned by the specified Collection's
     * iterator.
     *
     * @param index index at which to insert the first element from the
     *              specified collection
     * @param c elements to be inserted into this Vector
     * @return <tt>true</tt> if this Vector changed as a result of the call
     * @exception ArrayIndexOutOfBoundsException index out of range (index
     *		  &lt; 0 || index &gt; size())
     * @throws NullPointerException if the specified collection is null
     * @since 1.2
     */
    public synchronized boolean addAll(int index, Collection<? extends E> c) {
	modCount++;
	if (index < 0 || index > elementCount)
	    throw new ArrayIndexOutOfBoundsException(index);

        Object[] a = c.toArray();
	int numNew = a.length;
	ensureCapacityHelper(elementCount + numNew);

	int numMoved = elementCount - index;
	if (numMoved > 0)
	    System.arraycopy(elementData, index, elementData, index + numNew,
			     numMoved);

        System.arraycopy(a, 0, elementData, index, numNew);
	elementCount += numNew;
	return numNew != 0;
    }

    /**
     * Compares the specified Object with this Vector for equality.  Returns
     * true if and only if the specified Object is also a List, both Lists
     * have the same size, and all corresponding pairs of elements in the two
     * Lists are <em>equal</em>.  (Two elements <code>e1</code> and
     * <code>e2</code> are <em>equal</em> if <code>(e1==null ? e2==null :
     * e1.equals(e2))</code>.)  In other words, two Lists are defined to be
     * equal if they contain the same elements in the same order.
     *
     * @param o the Object to be compared for equality with this Vector
     * @return true if the specified Object is equal to this Vector
     */
    public synchronized boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * Returns the hash code value for this Vector.
     */
    public synchronized int hashCode() {
        return super.hashCode();
    }

    /**
     * Returns a string representation of this Vector, containing
     * the String representation of each element.
     */
    public synchronized String toString() {
        return super.toString();
    }

    /**
     * Removes from this List all of the elements whose index is between
     * fromIndex, inclusive and toIndex, exclusive.  Shifts any succeeding
     * elements to the left (reduces their index).
     * This call shortens the Vector by (toIndex - fromIndex) elements.  (If
     * toIndex==fromIndex, this operation has no effect.)
     *
     * @param fromIndex index of first element to be removed
     * @param toIndex index after last element to be removed
     */
    protected synchronized void removeRange(int fromIndex, int toIndex) {
	modCount++;
	int numMoved = elementCount - toIndex;
        System.arraycopy(elementData, toIndex, elementData, fromIndex,
                         numMoved);

	// Let gc do its work
	int newElementCount = elementCount - (toIndex-fromIndex);
	while (elementCount != newElementCount)
	    elementData[--elementCount] = null;
    }

    /**
     * Save the state of the <tt>Vector</tt> instance to a stream (that
     * is, serialize it).  This method is present merely for synchronization.
     * It just calls the default writeObject method.
     */
    private synchronized void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException
    {
	s.defaultWriteObject();
    }

    /**
     * Returns a list-iterator of the elements in this list (in proper
     * sequence), starting at the specified position in the list.
     * Obeys the general contract of {@link List#listIterator(int)}.
     *
     * <p>The list-iterator is <i>fail-fast</i>: if the list is structurally
     * modified at any time after the Iterator is created, in any way except
     * through the list-iterator's own {@code remove} or {@code add}
     * methods, the list-iterator will throw a
     * {@code ConcurrentModificationException}.  Thus, in the face of
     * concurrent modification, the iterator fails quickly and cleanly, rather
     * than risking arbitrary, non-deterministic behavior at an undetermined
     * time in the future.
     *
     * @param index index of the first element to be returned from the
     *        list-iterator (by a call to {@link ListIterator#next})
     * @return a list-iterator of the elements in this list (in proper
     *         sequence), starting at the specified position in the list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized ListIterator<E> listIterator(int index) {
	if (index < 0 || index > elementCount)
            throw new IndexOutOfBoundsException("Index: "+index);
	return new VectorIterator(index, elementCount);
    }

    /**
     * {@inheritDoc}
     */
    public synchronized ListIterator<E> listIterator() {
    	return new VectorIterator(0, elementCount);
    }

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    public synchronized Iterator<E> iterator() {
        return new VectorIterator(0, elementCount);
    }

    /**
     * Helper method to access array elements under synchronization by
     * iterators. The caller performs index check with respect to
     * expected bounds, so errors accessing the element are reported
     * as ConcurrentModificationExceptions.
     */
    final synchronized Object iteratorGet(int index, int expectedModCount) {
        if (modCount == expectedModCount) {
            try {
                return elementData[index];
            } catch(IndexOutOfBoundsException fallThrough) {
            }
        }
        throw new ConcurrentModificationException();
    }

    /**
     * Streamlined specialization of AbstractList version of iterator.
     * Locally perfroms bounds checks, but relies on outer Vector
     * to access elements under synchronization.
     */
    private final class VectorIterator implements ListIterator<E> {
	int cursor;              // Index of next element to return;
        int fence;               // Upper bound on cursor (cache of size())
	int lastRet;             // Index of last element, or -1 if no such
	int expectedModCount;    // To check for CME

	VectorIterator(int index, int fence) {
	    this.cursor = index;
            this.fence = fence;
            this.lastRet = -1;
            this.expectedModCount = Vector.this.modCount;
	}

	public boolean hasNext() {
            return cursor < fence;
	}

	public boolean hasPrevious() {
	    return cursor > 0;
	}

	public int nextIndex() {
	    return cursor;
	}

	public int previousIndex() {
	    return cursor - 1;
	}

	public E next() {
            int i = cursor;
            if (i >= fence)
                throw new NoSuchElementException();
            Object next = Vector.this.iteratorGet(i, expectedModCount);
            lastRet = i;
            cursor = i + 1;
            return (E)next;
	}

        public E previous() {
            int i = cursor - 1;
            if (i < 0)
                throw new NoSuchElementException();
            Object prev = Vector.this.iteratorGet(i, expectedModCount);
            lastRet = i;
            cursor = i;
            return (E)prev;
        }

	public void set(E e) {
	    if (lastRet < 0)
		throw new IllegalStateException();
            if (Vector.this.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            try {
                Vector.this.set(lastRet, e);
                expectedModCount = Vector.this.modCount;
	    } catch (IndexOutOfBoundsException ex) {
		throw new ConcurrentModificationException();
	    }
	}

	public void remove() {
            int i = lastRet;
	    if (i < 0)
		throw new IllegalStateException();
            if (Vector.this.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            try {
                Vector.this.remove(i);
                if (i < cursor)
                    cursor--;
                lastRet = -1;
                fence = Vector.this.size();
                expectedModCount = Vector.this.modCount;
	    } catch (IndexOutOfBoundsException ex) {
		throw new ConcurrentModificationException();
	    }
	}

	public void add(E e) {
            if (Vector.this.modCount != expectedModCount)
                throw new ConcurrentModificationException();
	    try {
                int i = cursor;
                Vector.this.add(i, e);
                cursor = i + 1;
                lastRet = -1;
                fence = Vector.this.size();
                expectedModCount = Vector.this.modCount;
	    } catch (IndexOutOfBoundsException ex) {
		throw new ConcurrentModificationException();
	    }
	}
    }

    /**
     * Returns a view of the portion of this List between fromIndex,
     * inclusive, and toIndex, exclusive.  (If fromIndex and toIndex are
     * equal, the returned List is empty.)  The returned List is backed by this
     * List, so changes in the returned List are reflected in this List, and
     * vice-versa.  The returned List supports all of the optional List
     * operations supported by this List.<p>
     *
     * This method eliminates the need for explicit range operations (of
     * the sort that commonly exist for arrays).   Any operation that expects
     * a List can be used as a range operation by operating on a subList view
     * instead of a whole List.  For example, the following idiom
     * removes a range of elements from a List:
     * <pre>
     *	    list.subList(from, to).clear();
     * </pre>
     * Similar idioms may be constructed for indexOf and lastIndexOf,
     * and all of the algorithms in the Collections class can be applied to
     * a subList.<p>
     *
     * The semantics of the List returned by this method become undefined if
     * the backing list (i.e., this List) is <i>structurally modified</i> in
     * any way other than via the returned List.  (Structural modifications are
     * those that change the size of the List, or otherwise perturb it in such
     * a fashion that iterations in progress may yield incorrect results.)
     *
     * @param fromIndex low endpoint (inclusive) of the subList
     * @param toIndex high endpoint (exclusive) of the subList
     * @return a view of the specified range within this List
     * @throws IndexOutOfBoundsException endpoint index value out of range
     *         <code>(fromIndex &lt; 0 || toIndex &gt; size)</code>
     * @throws IllegalArgumentException endpoint indices out of order
     *	       <code>(fromIndex &gt; toIndex)</code>
     */
    public synchronized List<E> subList(int fromIndex, int toIndex) {
        return new VectorSubList(this, this, fromIndex, fromIndex, toIndex);
    }

    /**
     * This class specializes the AbstractList version of SubList to
     * avoid the double-indirection penalty that would arise using a
     * synchronized wrapper, as well as to avoid some unnecessary
     * checks in sublist iterators.
     */
    private static final class VectorSubList<E> extends AbstractList<E> implements RandomAccess {
        final Vector<E> base;             // base list
        final AbstractList<E> parent;     // Creating list
        final int baseOffset;             // index wrt Vector
        final int parentOffset;           // index wrt parent
        int length;                       // length of sublist

        VectorSubList(Vector<E> base, AbstractList<E> parent, int baseOffset,
                     int fromIndex, int toIndex) {
            if (fromIndex < 0)
                throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
            if (toIndex > parent.size())
                throw new IndexOutOfBoundsException("toIndex = " + toIndex);
            if (fromIndex > toIndex)
                throw new IllegalArgumentException("fromIndex(" + fromIndex +
                                                   ") > toIndex(" + toIndex + ")");

            this.base = base;
            this.parent = parent;
            this.baseOffset = baseOffset;
            this.parentOffset = fromIndex;
            this.length = toIndex - fromIndex;
            modCount = base.modCount;
        }

        /**
         * Returns an IndexOutOfBoundsException with nicer message
         */
        private IndexOutOfBoundsException indexError(int index) {
            return new IndexOutOfBoundsException("Index: " + index +
                                                 ", Size: " + length);
        }

        public E set(int index, E element) {
            synchronized(base) {
                if (index < 0 || index >= length)
                    throw indexError(index);
                if (base.modCount != modCount)
                    throw new ConcurrentModificationException();
                return base.set(index + baseOffset, element);
            }
        }

        public E get(int index) {
            synchronized(base) {
                if (index < 0 || index >= length)
                    throw indexError(index);
                if (base.modCount != modCount)
                    throw new ConcurrentModificationException();
                return base.get(index + baseOffset);
            }
        }

        public int size() {
            synchronized(base) {
                if (base.modCount != modCount)
                    throw new ConcurrentModificationException();
                return length;
            }
        }

        public void add(int index, E element) {
            synchronized(base) {
                if (index < 0 || index > length)
                    throw indexError(index);
                if (base.modCount != modCount)
                    throw new ConcurrentModificationException();
                parent.add(index + parentOffset, element);
                length++;
                modCount = base.modCount;
            }
        }

        public E remove(int index) {
            synchronized(base) {
                if (index < 0 || index >= length)
                    throw indexError(index);
                if (base.modCount != modCount)
                    throw new ConcurrentModificationException();
                E result = parent.remove(index + parentOffset);
                length--;
                modCount = base.modCount;
                return result;
            }
        }

        protected void removeRange(int fromIndex, int toIndex) {
            synchronized(base) {
                if (base.modCount != modCount)
                    throw new ConcurrentModificationException();
                parent.removeRange(fromIndex + parentOffset,
                                   toIndex + parentOffset);
                length -= (toIndex-fromIndex);
                modCount = base.modCount;
            }
        }

        public boolean addAll(Collection<? extends E> c) {
            return addAll(length, c);
        }

        public boolean addAll(int index, Collection<? extends E> c) {
            synchronized(base) {
                if (index < 0 || index > length)
                    throw indexError(index);
                int cSize = c.size();
                if (cSize==0)
                    return false;

                if (base.modCount != modCount)
                    throw new ConcurrentModificationException();
                parent.addAll(parentOffset + index, c);
                modCount = base.modCount;
                length += cSize;
                return true;
            }
        }

	public boolean equals(Object o) {
	    synchronized(base) {return super.equals(o);}
        }

	public int hashCode() {
	    synchronized(base) {return super.hashCode();}
        }

	public int indexOf(Object o) {
	    synchronized(base) {return super.indexOf(o);}
        }

	public int lastIndexOf(Object o) {
	    synchronized(base) {return super.lastIndexOf(o);}
        }

        public List<E> subList(int fromIndex, int toIndex) {
            return new VectorSubList(base, this, fromIndex + baseOffset,
                                     fromIndex, toIndex);
        }

        public Iterator<E> iterator() {
            synchronized(base) {
                return new VectorSubListIterator(this, 0);
            }
        }

        public synchronized ListIterator<E> listIterator() {
            synchronized(base) {
                return new VectorSubListIterator(this, 0);
            }
        }

        public ListIterator<E> listIterator(int index) {
            synchronized(base) {
                if (index < 0 || index > length)
                    throw indexError(index);
                return new VectorSubListIterator(this, index);
            }
        }

        /**
         * Same idea as VectorIterator, except routing structural
         * change operations through the sublist.
         */
        private static final class VectorSubListIterator<E> implements ListIterator<E> {
            final Vector<E> base;         // base list
            final VectorSubList<E> outer; // Sublist creating this iteraor
            final int offset;             // cursor offset wrt base
            int cursor;                   // Current index
            int fence;                    // Upper bound on cursor
            int lastRet;                  // Index of returned element, or -1
            int expectedModCount;         // Expected modCount of base Vector

            VectorSubListIterator(VectorSubList<E> list, int index) {
                this.lastRet = -1;
                this.cursor = index;
                this.outer = list;
                this.offset = list.baseOffset;
                this.fence = list.length;
                this.base = list.base;
                this.expectedModCount = base.modCount;
            }

            public boolean hasNext() {
                return cursor < fence;
            }

            public boolean hasPrevious() {
                return cursor > 0;
            }

            public int nextIndex() {
                return cursor;
            }

            public int previousIndex() {
                return cursor - 1;
            }

            public E next() {
                int i = cursor;
                if (cursor >= fence)
                    throw new NoSuchElementException();
                Object next = base.iteratorGet(i + offset, expectedModCount);
                lastRet = i;
                cursor = i + 1;
                return (E)next;
            }

            public E previous() {
                int i = cursor - 1;
                if (i < 0)
                    throw new NoSuchElementException();
                Object prev = base.iteratorGet(i + offset, expectedModCount);
                lastRet = i;
                cursor = i;
                return (E)prev;
            }

            public void set(E e) {
                if (lastRet < 0)
                    throw new IllegalStateException();
                if (base.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                try {
                    outer.set(lastRet, e);
                    expectedModCount = base.modCount;
                } catch (IndexOutOfBoundsException ex) {
                    throw new ConcurrentModificationException();
                }
            }

            public void remove() {
                int i = lastRet;
                if (i < 0)
                    throw new IllegalStateException();
                if (base.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                try {
                    outer.remove(i);
                    if (i < cursor)
                        cursor--;
                    lastRet = -1;
                    fence = outer.length;
                    expectedModCount = base.modCount;
                } catch (IndexOutOfBoundsException ex) {
                    throw new ConcurrentModificationException();
                }
            }

            public void add(E e) {
                if (base.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                try {
                    int i = cursor;
                    outer.add(i, e);
                    cursor = i + 1;
                    lastRet = -1;
                    fence = outer.length;
                    expectedModCount = base.modCount;
                } catch (IndexOutOfBoundsException ex) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }
}



