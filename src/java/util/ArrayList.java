/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.util;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import sun.misc.SharedSecrets;

/**
 * Resizable-array implementation of the <tt>List</tt> interface. Implements all
 * optional list operations, and permits all elements, including <tt>null</tt>.
 * In addition to implementing the <tt>List</tt> interface, this class provides
 * methods to manipulate the size of the array that is used internally to store
 * the list. (This class is roughly equivalent to <tt>Vector</tt>, except that
 * it is unsynchronized.)
 *
 * <p>
 * The <tt>size</tt>, <tt>isEmpty</tt>, <tt>get</tt>, <tt>set</tt>,
 * <tt>iterator</tt>, and <tt>listIterator</tt> operations run in constant time.
 * The <tt>add</tt> operation runs in <i>amortized constant time</i>, that is,
 * adding n elements requires O(n) time. All of the other operations run in
 * linear time (roughly speaking). The constant factor is low compared to that
 * for the <tt>LinkedList</tt> implementation.
 *
 * <p>
 * Each <tt>ArrayList</tt> instance has a <i>capacity</i>. The capacity is the
 * size of the array used to store the elements in the list. It is always at
 * least as large as the list size. As elements are added to an ArrayList, its
 * capacity grows automatically. The details of the growth policy are not
 * specified beyond the fact that adding an element has constant amortized time
 * cost.
 *
 * <p>
 * An application can increase the capacity of an <tt>ArrayList</tt> instance
 * before adding a large number of elements using the <tt>ensureCapacity</tt>
 * operation. This may reduce the amount of incremental reallocation.
 *
 * <p>
 * <strong>Note that this implementation is not synchronized.</strong> If
 * multiple threads access an <tt>ArrayList</tt> instance concurrently, and at
 * least one of the threads modifies the list structurally, it <i>must</i> be
 * synchronized externally. (A structural modification is any operation that
 * adds or deletes one or more elements, or explicitly resizes the backing
 * array; merely setting the value of an element is not a structural
 * modification.) This is typically accomplished by synchronizing on some object
 * that naturally encapsulates the list.
 *
 * If no such object exists, the list should be "wrapped" using the
 * {@link Collections#synchronizedList Collections.synchronizedList} method.
 * This is best done at creation time, to prevent accidental unsynchronized
 * access to the list:
 * 
 * <pre>
 *   List list = Collections.synchronizedList(new ArrayList(...));
 * </pre>
 *
 * <p>
 * <a name="fail-fast"> The iterators returned by this class's
 * {@link #iterator() iterator} and {@link #listIterator(int) listIterator}
 * methods are <em>fail-fast</em>:</a> if the list is structurally modified at
 * any time after the iterator is created, in any way except through the
 * iterator's own {@link ListIterator#remove() remove} or
 * {@link ListIterator#add(Object) add} methods, the iterator will throw a
 * {@link ConcurrentModificationException}. Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the future.
 *
 * <p>
 * Note that the fail-fast behavior of an iterator cannot be guaranteed as it
 * is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification. Fail-fast iterators throw
 * {@code ConcurrentModificationException} on a best-effort basis. Therefore, it
 * would be wrong to write a program that depended on this exception for its
 * correctness: <i>the fail-fast behavior of iterators should be used only to
 * detect bugs.</i>
 *
 * <p>
 * This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html"> Java
 * Collections Framework</a>.
 *
 * @author Josh Bloch
 * @author Neal Gafter
 * @see Collection
 * @see List
 * @see LinkedList
 * @see Vector
 * @since 1.2
 */

public class ArrayList<E> extends AbstractList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
	private static final long serialVersionUID = 8683452581122892189L;

	/**
	 * 默认集合的大小 10
	 */
	private static final int DEFAULT_CAPACITY = 10;

	/**
	 * 空数组 用户初始化
	 * EMPTY_ELEMENTDATA 当构造的入参initialCapacity为0时
	 * DEFAULTCAPACITY_EMPTY_ELEMENTDATA 无参构造 程序默认赋值
	 * 
	 */
	private static final Object[] EMPTY_ELEMENTDATA = {};

	/**
	 * 默认的空数组 程序默认初始化的数组
	 */
	private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

	/**
	 * 对象数组 存放数据的结构  
	 * transient 表示序列化时会忽略
	 */
	transient Object[] elementData;

	/**
	 * 集合大小
	 */
	private int size;
	// Arrays.copyOf底层由System.arraycopy实现
	// System.arraycopy 需要了解一下 用的特别多
	//src 源数组；srcPos 源数组复制的起始下标；dest 目标数组；destPos 目标数组起始下标；length 复制的长度
	//System.arraycopy(src, srcPos, dest, destPos, length);
	//一句话解释  从源数组的srcPos下标开始的length个数，每个数据项移动|destPos-srcPos|位
	/**
	 * 
	 * @param initialCapacity 初始化数组的大小
	 */
	public ArrayList(int initialCapacity) {
		if (initialCapacity > 0) {
			// 初始化initialCapacity大小的数组
			this.elementData = new Object[initialCapacity];
		} else if (initialCapacity == 0) {
			// 初始化个空数组
			this.elementData = EMPTY_ELEMENTDATA;
		} else {
			throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
		}
	}

	public ArrayList() {
		// 初始化个空数组
		this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
	}

	public ArrayList(Collection<? extends E> c) {
		elementData = c.toArray();
		if ((size = elementData.length) != 0) {
			// c.toArray might (incorrectly) not return Object[] (see 6260652)
			if (elementData.getClass() != Object[].class)
				elementData = Arrays.copyOf(elementData, size, Object[].class);
		} else {
			// replace with empty array.
			this.elementData = EMPTY_ELEMENTDATA;
		}
	}

	public void trimToSize() {
		modCount++;
		if (size < elementData.length) {
			elementData = (size == 0) ? EMPTY_ELEMENTDATA : Arrays.copyOf(elementData, size);
		}
	}

	/**
	 * 数组扩容
	 */
	public void ensureCapacity(int minCapacity) {

		// 获取到一个最小扩容大小
		// 如果elementData是程序默认初始化的DEFAULTCAPACITY_EMPTY_ELEMENTDATA minExpand=DEFAULT_CAPACITY
		// 否者minExpand=0
		int minExpand = (elementData != DEFAULTCAPACITY_EMPTY_ELEMENTDATA) ? 0 : DEFAULT_CAPACITY;

		if (minCapacity > minExpand) {// 所需的最小容量大于当前数组容量
			ensureExplicitCapacity(minCapacity);
		}
	}

	/**
	 *  根据原数组和所需的最小容量大小计算所需的最小容量大小
	 */
	private static int calculateCapacity(Object[] elementData, int minCapacity) {
		if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {//如果是程序默认初始化的数组
			return Math.max(DEFAULT_CAPACITY, minCapacity);//返回 minCapacity和DEFAULT_CAPACITY中的较大的一个
		}
		return minCapacity;
	}
	/**
	 *  数组扩容
	 */
	private void ensureCapacityInternal(int minCapacity) {//minCapacity 所需的最小容量大小
		ensureExplicitCapacity(calculateCapacity(elementData, minCapacity));
	}

	/**
	 *	当所需的最小容量大小大于当前数组的大小 数组扩容
	 */
	private void ensureExplicitCapacity(int minCapacity) {
		modCount++;//修改次数加一
		
		if (minCapacity - elementData.length > 0)// 所需的最小容量大小大于当前数组的大小
			grow(minCapacity);//数组扩容
	}

	/**
	 * 数组的最大大小 最大可以取到Integer.MAX_VALUE
	 */
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	/**
	 * 真正扩容的地方
	 * @param minCapacity 所需的最小容量
	 */
	private void grow(int minCapacity) {
		int oldCapacity = elementData.length;// 当前容量
		// 相当于 newCapacity = 1.5 * oldCapacity
		int newCapacity = oldCapacity + (oldCapacity >> 1);
		if (newCapacity - minCapacity < 0)
			newCapacity = minCapacity;
		if (newCapacity - MAX_ARRAY_SIZE > 0)//当比MAX_ARRAY_SIZE还要大时
			newCapacity = hugeCapacity(minCapacity);//Integer.MAX_VALUE 
		// 将当前数组中的数据copy到长度为newCapacity的新数组中 elementData指向新的数组
		elementData = Arrays.copyOf(elementData, newCapacity);
	}
	
	/**
	 * 	超大容量的处理
	 */
	private static int hugeCapacity(int minCapacity) {
		if (minCapacity < 0) // overflow
			throw new OutOfMemoryError();
		return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
	}

	/**
	 * 返回集合大小
	 */
	public int size() {
		return size;
	}

	/**
	 *  判断集合是否为空
	 */
	public boolean isEmpty() {
		return size == 0;
	}

	/**
	 *   判断集合中是否存在对象o
	 */
	public boolean contains(Object o) {
		return indexOf(o) >= 0;
	}

	/**
	 * 返回对象在数组中的下标 
	 * 存在 返回对象在数组中的下标 
	 * 不存在 则返回-1
	 */
	public int indexOf(Object o) {
		if (o == null) {
			for (int i = 0; i < size; i++)
				if (elementData[i] == null)
					return i;
		} else {
			for (int i = 0; i < size; i++)
				if (o.equals(elementData[i]))
					return i;
		}
		return -1;
	}

	/**
	 * 返回存在对象在数组中的最大下标 从数组末尾开始遍历 
	 * 存在 返回对象在数组中的最大下标 
	 * 不存在 则返回-1
	 */
	public int lastIndexOf(Object o) {
		if (o == null) {
			for (int i = size - 1; i >= 0; i--)
				if (elementData[i] == null)
					return i;
		} else {
			for (int i = size - 1; i >= 0; i--)
				if (o.equals(elementData[i]))
					return i;
		}
		return -1;
	}

	/**
	 * Returns a shallow copy of this <tt>ArrayList</tt> instance. (The elements
	 * themselves are not copied.)
	 *
	 * @return a clone of this <tt>ArrayList</tt> instance
	 */
	public Object clone() {
		try {
			ArrayList<?> v = (ArrayList<?>) super.clone();
			v.elementData = Arrays.copyOf(elementData, size);
			v.modCount = 0;
			return v;
		} catch (CloneNotSupportedException e) {
			// this shouldn't happen, since we are Cloneable
			throw new InternalError(e);
		}
	}

	/**
	 * 返回一个新数组
	 */
	public Object[] toArray() {
		return Arrays.copyOf(elementData, size);
	}

	/**
	 * 返回一个数据类型为T新数组
	 */
	public <T> T[] toArray(T[] a) {
		if (a.length < size)
			// Make a new array of a's runtime type, but my contents:
			return (T[]) Arrays.copyOf(elementData, size, a.getClass());
		System.arraycopy(elementData, 0, a, 0, size);
		if (a.length > size)
			a[size] = null;
		return a;
	}

	/**
	 * 内部使用 获取下标为index的数据
	 */
	@SuppressWarnings("unchecked")
	E elementData(int index) {
		return (E) elementData[index];
	}

	/**
	 * 获取下标为index的数据
	 */
	public E get(int index) {
		rangeCheck(index);// 数组越界校验

		return elementData(index);// 获取下标为index的数据
	}

	/**
	 * 用element覆盖掉下标为index的数据 并返回旧值
	 */
	public E set(int index, E element) {
		rangeCheck(index);// 数组越界校验

		E oldValue = elementData(index);// 获取到旧值
		elementData[index] = element;// 重新赋值
		return oldValue;// 返回旧值
	}

	/**
	 *	 默认是末尾添加 
	 */
	public boolean add(E e) {
		//数组扩容的处理 需要扩容的话 新数组的大小为原数组的1.5 并将原数组的数据copy到新数组中 elementData指向新数组
		ensureCapacityInternal(size + 1);
		elementData[size++] = e;// 赋值 数组大小加一
		return true;
	}

	/*
	 * 在index位置添加数据
	 */
	public void add(int index, E element) {
		rangeCheckForAdd(index);//数组越界校验 下标不能大于等于当前集合大小且下标不能小于0

		ensureCapacityInternal(size + 1); // 数组扩容处理
		//数组自身移动  从index下标开始的(size - index)个数  每个数据的下标向后移动一位  下标index正好空出来
		System.arraycopy(elementData, index, elementData, index + 1, size - index);
		elementData[index] = element;//赋值
		size++;//数组大小加一
	}

	/**
	 * 删除下标为index的数据
	 */
	public E remove(int index) {
		rangeCheck(index);//数组越界校验 下标不能大于等于当前集合大小

		modCount++;//修改次数加一
		E oldValue = elementData(index);//获取旧值

		int numMoved = size - index - 1;//需要移动数据的个数 
		if (numMoved > 0)//数组自身移动  从(index+1)下标开始的numMoved个数  每个数据的下标向前移动一位  下标(size-1)正好空出来
			System.arraycopy(elementData, index + 1, elementData, index, numMoved);
		elementData[--size] = null; //  size减一  将最后的赋null 之前指向的数据会被垃圾回收器处理

		return oldValue;//返回旧值
	}

	/**
	 * 删除对象o
	 * o为null 用==比较
	 * 否者用equals 
	 * 找到之后相等对象的下标   快速删除 
	 */
	public boolean remove(Object o) {
		if (o == null) {
			for (int index = 0; index < size; index++)
				if (elementData[index] == null) {
					fastRemove(index);
					return true;
				}
		} else {
			for (int index = 0; index < size; index++)
				if (o.equals(elementData[index])) {//找到相等时的数组下标
					fastRemove(index);//快速删除 下标为index的数据
					return true;
				}
		}
		return false;
	}

	/*
	 * 快速删除 下标为index的数据
	 */
	private void fastRemove(int index) {
		modCount++;
		int numMoved = size - index - 1;
		if (numMoved > 0)//数组自身移动  从(index+1)下标开始的numMoved个数  每个数据的下标向前移动一位  下标(size-1)正好空出来
			System.arraycopy(elementData, index + 1, elementData, index, numMoved);
		elementData[--size] = null; // 赋null 有助于gc
	}

	/**
	 * 将数组中的数据都指向null
	 * size 赋值0
	 */
	public void clear() {
		modCount++;

		// 有助于gc
		for (int i = 0; i < size; i++)
			elementData[i] = null;

		size = 0;
	}

	/**
	 * Appends all of the elements in the specified collection to the end of this
	 * list, in the order that they are returned by the specified collection's
	 * Iterator. The behavior of this operation is undefined if the specified
	 * collection is modified while the operation is in progress. (This implies that
	 * the behavior of this call is undefined if the specified collection is this
	 * list, and this list is nonempty.)
	 *
	 * @param c collection containing elements to be added to this list
	 * @return <tt>true</tt> if this list changed as a result of the call
	 * @throws NullPointerException if the specified collection is null
	 */
	public boolean addAll(Collection<? extends E> c) {
		Object[] a = c.toArray();
		int numNew = a.length;
		ensureCapacityInternal(size + numNew); // Increments modCount
		System.arraycopy(a, 0, elementData, size, numNew);
		size += numNew;
		return numNew != 0;
	}

	/**
	 * Inserts all of the elements in the specified collection into this list,
	 * starting at the specified position. Shifts the element currently at that
	 * position (if any) and any subsequent elements to the right (increases their
	 * indices). The new elements will appear in the list in the order that they are
	 * returned by the specified collection's iterator.
	 *
	 * @param index index at which to insert the first element from the specified
	 *              collection
	 * @param c     collection containing elements to be added to this list
	 * @return <tt>true</tt> if this list changed as a result of the call
	 * @throws IndexOutOfBoundsException {@inheritDoc}
	 * @throws NullPointerException      if the specified collection is null
	 */
	public boolean addAll(int index, Collection<? extends E> c) {
		rangeCheckForAdd(index);

		Object[] a = c.toArray();
		int numNew = a.length;
		ensureCapacityInternal(size + numNew); // Increments modCount

		int numMoved = size - index;
		if (numMoved > 0)
			System.arraycopy(elementData, index, elementData, index + numNew, numMoved);

		System.arraycopy(a, 0, elementData, index, numNew);
		size += numNew;
		return numNew != 0;
	}

	/**
	 * 移除一段范围内的数据
	 */
	protected void removeRange(int fromIndex, int toIndex) {
		modCount++;
		int numMoved = size - toIndex;
		System.arraycopy(elementData, toIndex, elementData, fromIndex, numMoved);

		// 有助于gc
		int newSize = size - (toIndex - fromIndex);
		for (int i = newSize; i < size; i++) {
			elementData[i] = null;
		}
		size = newSize;
	}

	/**
	 * 数组越界校验 下标不能大于等于当前集合大小
	 */
	private void rangeCheck(int index) {
		if (index >= size)// 下标不能大于等于当前集合大小
			throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
	}

	/**
	 * 数组越界校验 下标不能大于等于当前集合大小且下标不能小于0
	 */
	private void rangeCheckForAdd(int index) {
		if (index > size || index < 0)
			throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
	}

	/**
	 * 数据越界异常语句
	 */
	private String outOfBoundsMsg(int index) {
		return "Index: " + index + ", Size: " + size;
	}

	/**
	 * 移除数组中存在于集合c中的数据
	 */
	public boolean removeAll(Collection<?> c) {
		Objects.requireNonNull(c);
		return batchRemove(c, false);
	}

	/**
	 * 
	 */
	public boolean retainAll(Collection<?> c) {
		Objects.requireNonNull(c);
		return batchRemove(c, true);
	}

	private boolean batchRemove(Collection<?> c, boolean complement) {
		final Object[] elementData = this.elementData;
		int r = 0, w = 0;
		boolean modified = false;
		try {
			for (; r < size; r++)
				if (c.contains(elementData[r]) == complement)
					elementData[w++] = elementData[r];
		} finally {
			// Preserve behavioral compatibility with AbstractCollection,
			// even if c.contains() throws.
			if (r != size) {
				System.arraycopy(elementData, r, elementData, w, size - r);
				w += size - r;
			}
			if (w != size) {
				// clear to let GC do its work
				for (int i = w; i < size; i++)
					elementData[i] = null;
				modCount += size - w;
				size = w;
				modified = true;
			}
		}
		return modified;
	}

	/**
	 * Save the state of the <tt>ArrayList</tt> instance to a stream (that is,
	 * serialize it).
	 *
	 * @serialData The length of the array backing the <tt>ArrayList</tt> instance
	 *             is emitted (int), followed by all of its elements (each an
	 *             <tt>Object</tt>) in the proper order.
	 */
	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
		// Write out element count, and any hidden stuff
		int expectedModCount = modCount;
		s.defaultWriteObject();

		// Write out size as capacity for behavioural compatibility with clone()
		s.writeInt(size);

		// Write out all elements in the proper order.
		for (int i = 0; i < size; i++) {
			s.writeObject(elementData[i]);
		}

		if (modCount != expectedModCount) {
			throw new ConcurrentModificationException();
		}
	}

	/**
	 * Reconstitute the <tt>ArrayList</tt> instance from a stream (that is,
	 * deserialize it).
	 */
	private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
		elementData = EMPTY_ELEMENTDATA;

		// Read in size, and any hidden stuff
		s.defaultReadObject();

		// Read in capacity
		s.readInt(); // ignored

		if (size > 0) {
			// be like clone(), allocate array based upon size not capacity
			int capacity = calculateCapacity(elementData, size);
			SharedSecrets.getJavaOISAccess().checkArray(s, Object[].class, capacity);
			ensureCapacityInternal(size);

			Object[] a = elementData;
			// Read in all elements in the proper order.
			for (int i = 0; i < size; i++) {
				a[i] = s.readObject();
			}
		}
	}

	/**
	 * 将下标index及以后的数据生成ListIterator对象
	 * ListIterator 继承Iterator  可以向前查询 删除数据
	 * 而且可以向后查找 添加数据  获取数据的下标
	 */
	public ListIterator<E> listIterator(int index) {
		if (index < 0 || index > size)
			throw new IndexOutOfBoundsException("Index: " + index);
		return new ListItr(index);
	}

	/**
	 * 生成ListIterator对象
	 * ListIterator 继承Iterator  可以向前查询 删除数据
	 * 而且可以向后查找 添加数据  获取数据的下标
	 */
	public ListIterator<E> listIterator() {
		return new ListItr(0);
	}

	/**
	 * 将生成iterator对象
	 * Iterator  可以向前查询 删除数据
	 */
	public Iterator<E> iterator() {
		return new Itr();
	}

	/**
	 * 内部实现Iterator
	 */
	private class Itr implements Iterator<E> {
		int cursor; // index of next element to return
		int lastRet = -1; // index of last element returned; -1 if no such
		int expectedModCount = modCount;

		public boolean hasNext() {
			return cursor != size;
		}

		@SuppressWarnings("unchecked")
		public E next() {
			checkForComodification();
			int i = cursor;
			if (i >= size)
				throw new NoSuchElementException();
			Object[] elementData = ArrayList.this.elementData;
			if (i >= elementData.length)
				throw new ConcurrentModificationException();
			cursor = i + 1;
			return (E) elementData[lastRet = i];
		}

		public void remove() {
			if (lastRet < 0)
				throw new IllegalStateException();
			checkForComodification();

			try {
				ArrayList.this.remove(lastRet);
				cursor = lastRet;
				lastRet = -1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		@SuppressWarnings("unchecked")
		public void forEachRemaining(Consumer<? super E> consumer) {
			Objects.requireNonNull(consumer);
			final int size = ArrayList.this.size;
			int i = cursor;
			if (i >= size) {
				return;
			}
			final Object[] elementData = ArrayList.this.elementData;
			if (i >= elementData.length) {
				throw new ConcurrentModificationException();
			}
			while (i != size && modCount == expectedModCount) {
				consumer.accept((E) elementData[i++]);
			}
			// update once at end of iteration to reduce heap write traffic
			cursor = i;
			lastRet = i - 1;
			checkForComodification();
		}

		final void checkForComodification() {
			if (modCount != expectedModCount)
				throw new ConcurrentModificationException();
		}
	}

	/**
	 * 内部实现ListIterator
	 */
	private class ListItr extends Itr implements ListIterator<E> {
		ListItr(int index) {
			super();
			cursor = index;
		}

		public boolean hasPrevious() {
			return cursor != 0;
		}

		public int nextIndex() {
			return cursor;
		}

		public int previousIndex() {
			return cursor - 1;
		}

		@SuppressWarnings("unchecked")
		public E previous() {
			checkForComodification();
			int i = cursor - 1;
			if (i < 0)
				throw new NoSuchElementException();
			Object[] elementData = ArrayList.this.elementData;
			if (i >= elementData.length)
				throw new ConcurrentModificationException();
			cursor = i;
			return (E) elementData[lastRet = i];
		}

		public void set(E e) {
			if (lastRet < 0)
				throw new IllegalStateException();
			checkForComodification();

			try {
				ArrayList.this.set(lastRet, e);
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		public void add(E e) {
			checkForComodification();

			try {
				int i = cursor;
				ArrayList.this.add(i, e);
				cursor = i + 1;
				lastRet = -1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}
	}

	/**
	 * 生成一个SubList对象 当前的Arraylist会被作为一个属性
	 * 阿里java开发手册说道
	 * 2.   【强制】ArrayList的subList结果不可强转成ArrayList，否则会抛出ClassCastException 异常：
	 * java.util.RandomAccessSubList cannot be cast to java.util.ArrayList; 
	 * 说明：subList 返回的是 ArrayList 的内部类 SubList，并不是 ArrayList ，而是 ArrayList 的一个视图，
	 * 对于SubList子列表的所有操作最终会反映到原列表上。
	 * 3.   【强制】在subList场景中，高度注意对原集合元素个数的修改，
	 * 	会导致子列表的遍历、增加、删除均产生ConcurrentModificationException 异常。
	 */
	public List<E> subList(int fromIndex, int toIndex) {
		subListRangeCheck(fromIndex, toIndex, size);
		return new SubList(this, 0, fromIndex, toIndex);
	}

	/**
	 * 数组越界校验  以及fromIndex和toIndex是否非法的校验
	 */
	static void subListRangeCheck(int fromIndex, int toIndex, int size) {
		if (fromIndex < 0)
			throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
		if (toIndex > size)
			throw new IndexOutOfBoundsException("toIndex = " + toIndex);
		if (fromIndex > toIndex)
			throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
	}

	/**
	 * SubList内部持有当前的ArrayList对象
	 *
	 */
	private class SubList extends AbstractList<E> implements RandomAccess {
		private final AbstractList<E> parent;
		private final int parentOffset;
		private final int offset;
		int size;

		SubList(AbstractList<E> parent, int offset, int fromIndex, int toIndex) {
			this.parent = parent;
			this.parentOffset = fromIndex;
			this.offset = offset + fromIndex;
			this.size = toIndex - fromIndex;
			this.modCount = ArrayList.this.modCount;
		}

		public E set(int index, E e) {
			rangeCheck(index);
			checkForComodification();
			E oldValue = ArrayList.this.elementData(offset + index);
			ArrayList.this.elementData[offset + index] = e;
			return oldValue;
		}

		public E get(int index) {
			rangeCheck(index);
			checkForComodification();
			return ArrayList.this.elementData(offset + index);
		}

		public int size() {
			checkForComodification();
			return this.size;
		}

		public void add(int index, E e) {
			rangeCheckForAdd(index);
			checkForComodification();
			parent.add(parentOffset + index, e);
			this.modCount = parent.modCount;
			this.size++;
		}

		public E remove(int index) {
			rangeCheck(index);
			checkForComodification();
			E result = parent.remove(parentOffset + index);
			this.modCount = parent.modCount;
			this.size--;
			return result;
		}

		protected void removeRange(int fromIndex, int toIndex) {
			checkForComodification();
			parent.removeRange(parentOffset + fromIndex, parentOffset + toIndex);
			this.modCount = parent.modCount;
			this.size -= toIndex - fromIndex;
		}

		public boolean addAll(Collection<? extends E> c) {
			return addAll(this.size, c);
		}

		public boolean addAll(int index, Collection<? extends E> c) {
			rangeCheckForAdd(index);
			int cSize = c.size();
			if (cSize == 0)
				return false;

			checkForComodification();
			parent.addAll(parentOffset + index, c);
			this.modCount = parent.modCount;
			this.size += cSize;
			return true;
		}

		public Iterator<E> iterator() {
			return listIterator();
		}

		public ListIterator<E> listIterator(final int index) {
			checkForComodification();
			rangeCheckForAdd(index);
			final int offset = this.offset;

			return new ListIterator<E>() {
				int cursor = index;
				int lastRet = -1;
				int expectedModCount = ArrayList.this.modCount;

				public boolean hasNext() {
					return cursor != SubList.this.size;
				}

				@SuppressWarnings("unchecked")
				public E next() {
					checkForComodification();
					int i = cursor;
					if (i >= SubList.this.size)
						throw new NoSuchElementException();
					Object[] elementData = ArrayList.this.elementData;
					if (offset + i >= elementData.length)
						throw new ConcurrentModificationException();
					cursor = i + 1;
					return (E) elementData[offset + (lastRet = i)];
				}

				public boolean hasPrevious() {
					return cursor != 0;
				}

				@SuppressWarnings("unchecked")
				public E previous() {
					checkForComodification();
					int i = cursor - 1;
					if (i < 0)
						throw new NoSuchElementException();
					Object[] elementData = ArrayList.this.elementData;
					if (offset + i >= elementData.length)
						throw new ConcurrentModificationException();
					cursor = i;
					return (E) elementData[offset + (lastRet = i)];
				}

				@SuppressWarnings("unchecked")
				public void forEachRemaining(Consumer<? super E> consumer) {
					Objects.requireNonNull(consumer);
					final int size = SubList.this.size;
					int i = cursor;
					if (i >= size) {
						return;
					}
					final Object[] elementData = ArrayList.this.elementData;
					if (offset + i >= elementData.length) {
						throw new ConcurrentModificationException();
					}
					while (i != size && modCount == expectedModCount) {
						consumer.accept((E) elementData[offset + (i++)]);
					}
					// update once at end of iteration to reduce heap write traffic
					lastRet = cursor = i;
					checkForComodification();
				}

				public int nextIndex() {
					return cursor;
				}

				public int previousIndex() {
					return cursor - 1;
				}

				public void remove() {
					if (lastRet < 0)
						throw new IllegalStateException();
					checkForComodification();

					try {
						SubList.this.remove(lastRet);
						cursor = lastRet;
						lastRet = -1;
						expectedModCount = ArrayList.this.modCount;
					} catch (IndexOutOfBoundsException ex) {
						throw new ConcurrentModificationException();
					}
				}

				public void set(E e) {
					if (lastRet < 0)
						throw new IllegalStateException();
					checkForComodification();

					try {
						ArrayList.this.set(offset + lastRet, e);
					} catch (IndexOutOfBoundsException ex) {
						throw new ConcurrentModificationException();
					}
				}

				public void add(E e) {
					checkForComodification();

					try {
						int i = cursor;
						SubList.this.add(i, e);
						cursor = i + 1;
						lastRet = -1;
						expectedModCount = ArrayList.this.modCount;
					} catch (IndexOutOfBoundsException ex) {
						throw new ConcurrentModificationException();
					}
				}

				final void checkForComodification() {
					if (expectedModCount != ArrayList.this.modCount)
						throw new ConcurrentModificationException();
				}
			};
		}

		public List<E> subList(int fromIndex, int toIndex) {
			subListRangeCheck(fromIndex, toIndex, size);
			return new SubList(this, offset, fromIndex, toIndex);
		}

		private void rangeCheck(int index) {
			if (index < 0 || index >= this.size)
				throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}

		private void rangeCheckForAdd(int index) {
			if (index < 0 || index > this.size)
				throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}

		private String outOfBoundsMsg(int index) {
			return "Index: " + index + ", Size: " + this.size;
		}

		private void checkForComodification() {
			if (ArrayList.this.modCount != this.modCount)
				throw new ConcurrentModificationException();
		}

		public Spliterator<E> spliterator() {
			checkForComodification();
			return new ArrayListSpliterator<E>(ArrayList.this, offset, offset + this.size, this.modCount);
		}
	}

	/**
	 * 支持lambda表达式
	 */
	@Override
	public void forEach(Consumer<? super E> action) {
		Objects.requireNonNull(action);
		final int expectedModCount = modCount;
		@SuppressWarnings("unchecked")
		final E[] elementData = (E[]) this.elementData;
		final int size = this.size;
		for (int i = 0; modCount == expectedModCount && i < size; i++) {
			action.accept(elementData[i]);
		}
		if (modCount != expectedModCount) {
			throw new ConcurrentModificationException();
		}
	}

	/**
	 * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em> and
	 * <em>fail-fast</em> {@link Spliterator} over the elements in this list.
	 *
	 * <p>
	 * The {@code Spliterator} reports {@link Spliterator#SIZED},
	 * {@link Spliterator#SUBSIZED}, and {@link Spliterator#ORDERED}. Overriding
	 * implementations should document the reporting of additional characteristic
	 * values.
	 *
	 * @return a {@code Spliterator} over the elements in this list
	 * @since 1.8
	 */
	@Override
	public Spliterator<E> spliterator() {
		return new ArrayListSpliterator<>(this, 0, -1, 0);
	}

	/** Index-based split-by-two, lazily initialized Spliterator */
	static final class ArrayListSpliterator<E> implements Spliterator<E> {

		/*
		 * If ArrayLists were immutable, or structurally immutable (no adds, removes,
		 * etc), we could implement their spliterators with Arrays.spliterator. Instead
		 * we detect as much interference during traversal as practical without
		 * sacrificing much performance. We rely primarily on modCounts. These are not
		 * guaranteed to detect concurrency violations, and are sometimes overly
		 * conservative about within-thread interference, but detect enough problems to
		 * be worthwhile in practice. To carry this out, we (1) lazily initialize fence
		 * and expectedModCount until the latest point that we need to commit to the
		 * state we are checking against; thus improving precision. (This doesn't apply
		 * to SubLists, that create spliterators with current non-lazy values). (2) We
		 * perform only a single ConcurrentModificationException check at the end of
		 * forEach (the most performance-sensitive method). When using forEach (as
		 * opposed to iterators), we can normally only detect interference after
		 * actions, not before. Further CME-triggering checks apply to all other
		 * possible violations of assumptions for example null or too-small elementData
		 * array given its size(), that could only have occurred due to interference.
		 * This allows the inner loop of forEach to run without any further checks, and
		 * simplifies lambda-resolution. While this does entail a number of checks, note
		 * that in the common case of list.stream().forEach(a), no checks or other
		 * computation occur anywhere other than inside forEach itself. The other
		 * less-often-used methods cannot take advantage of most of these streamlinings.
		 */

		private final ArrayList<E> list;
		private int index; // current index, modified on advance/split
		private int fence; // -1 until used; then one past last index
		private int expectedModCount; // initialized when fence set

		/** Create new spliterator covering the given range */
		ArrayListSpliterator(ArrayList<E> list, int origin, int fence, int expectedModCount) {
			this.list = list; // OK if null unless traversed
			this.index = origin;
			this.fence = fence;
			this.expectedModCount = expectedModCount;
		}

		private int getFence() { // initialize fence to size on first use
			int hi; // (a specialized variant appears in method forEach)
			ArrayList<E> lst;
			if ((hi = fence) < 0) {
				if ((lst = list) == null)
					hi = fence = 0;
				else {
					expectedModCount = lst.modCount;
					hi = fence = lst.size;
				}
			}
			return hi;
		}

		public ArrayListSpliterator<E> trySplit() {
			int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
			return (lo >= mid) ? null : // divide range in half unless too small
					new ArrayListSpliterator<E>(list, lo, index = mid, expectedModCount);
		}

		public boolean tryAdvance(Consumer<? super E> action) {
			if (action == null)
				throw new NullPointerException();
			int hi = getFence(), i = index;
			if (i < hi) {
				index = i + 1;
				@SuppressWarnings("unchecked")
				E e = (E) list.elementData[i];
				action.accept(e);
				if (list.modCount != expectedModCount)
					throw new ConcurrentModificationException();
				return true;
			}
			return false;
		}

		public void forEachRemaining(Consumer<? super E> action) {
			int i, hi, mc; // hoist accesses and checks from loop
			ArrayList<E> lst;
			Object[] a;
			if (action == null)
				throw new NullPointerException();
			if ((lst = list) != null && (a = lst.elementData) != null) {
				if ((hi = fence) < 0) {
					mc = lst.modCount;
					hi = lst.size;
				} else
					mc = expectedModCount;
				if ((i = index) >= 0 && (index = hi) <= a.length) {
					for (; i < hi; ++i) {
						@SuppressWarnings("unchecked")
						E e = (E) a[i];
						action.accept(e);
					}
					if (lst.modCount == mc)
						return;
				}
			}
			throw new ConcurrentModificationException();
		}

		public long estimateSize() {
			return (long) (getFence() - index);
		}

		public int characteristics() {
			return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
		}
	}

	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		Objects.requireNonNull(filter);
		// figure out which elements are to be removed
		// any exception thrown from the filter predicate at this stage
		// will leave the collection unmodified
		int removeCount = 0;
		final BitSet removeSet = new BitSet(size);
		final int expectedModCount = modCount;
		final int size = this.size;
		for (int i = 0; modCount == expectedModCount && i < size; i++) {
			@SuppressWarnings("unchecked")
			final E element = (E) elementData[i];
			if (filter.test(element)) {
				removeSet.set(i);
				removeCount++;
			}
		}
		if (modCount != expectedModCount) {
			throw new ConcurrentModificationException();
		}

		// shift surviving elements left over the spaces left by removed elements
		final boolean anyToRemove = removeCount > 0;
		if (anyToRemove) {
			final int newSize = size - removeCount;
			for (int i = 0, j = 0; (i < size) && (j < newSize); i++, j++) {
				i = removeSet.nextClearBit(i);
				elementData[j] = elementData[i];
			}
			for (int k = newSize; k < size; k++) {
				elementData[k] = null; // Let gc do its work
			}
			this.size = newSize;
			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
			modCount++;
		}

		return anyToRemove;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void replaceAll(UnaryOperator<E> operator) {
		Objects.requireNonNull(operator);
		final int expectedModCount = modCount;
		final int size = this.size;
		for (int i = 0; modCount == expectedModCount && i < size; i++) {
			elementData[i] = operator.apply((E) elementData[i]);
		}
		if (modCount != expectedModCount) {
			throw new ConcurrentModificationException();
		}
		modCount++;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void sort(Comparator<? super E> c) {
		final int expectedModCount = modCount;
		Arrays.sort((E[]) elementData, 0, size, c);
		if (modCount != expectedModCount) {
			throw new ConcurrentModificationException();
		}
		modCount++;
	}
}
