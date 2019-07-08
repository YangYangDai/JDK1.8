package java.util;

import java.util.function.Consumer;


/**
 * 
 * 底层由双向链表实现 线程不安全
 * 适合大量随机删除、插入 
 * 删除和插入 只是指针的移动  与Arraylist(数组的移动)相比是用空间去换取时间
 * 查询 LinkedList会把index与(size/2)作比较 选取是从头还是从尾开始查询O(N/2)    Arraylist是随机访问时间复杂度O(1)
 */
public class LinkedList<E>
    extends AbstractSequentialList<E>
    implements List<E>, Deque<E>, Cloneable, java.io.Serializable
{
	/**
	 * 	集合大小
	 */
	transient int size = 0;
	
	/**
	 * 	头节点指针
	 */
    transient Node<E> first;

    /**
     *	尾结点指针
     */
    transient Node<E> last;

    /**
     *	 无参构造
     */
    public LinkedList() {
    }

    /**
     * 把Collection中的元素加入LinkedList中
     * @param c 集合
     */
    public LinkedList(Collection<? extends E> c) {
        this();//先调用无参构造
        addAll(c);
    }

    /**
     * 	把e作为头节点的数据
     */
    private void linkFirst(E e) {
        final Node<E> f = first;//把f指向头节点
        final Node<E> newNode = new Node<>(null, e, f);//初始化一个新的节点
        first = newNode;//first指向newNode
        if (f == null)//说明first还没初始化 这时候 first==last==null
            last = newNode;//把last也指向newNode
        else
            f.prev = newNode;//之前头节点的prev节点指向newNode
        size++;//集合大小加一
        modCount++;
    }

    /**
     * 	把e作为尾结点
     */
    void linkLast(E e) {
        final Node<E> l = last;//把l指向尾节点
        final Node<E> newNode = new Node<>(l, e, null);//初始化一个新的节点
        last = newNode;//last指向newNode
        if (l == null)//说明last还没初始化 这时候 first==last==null
            first = newNode;//把first也指向newNode
        else
            l.next = newNode;//之前尾节点的next节点指向newNode
        size++;//集合大小加一
        modCount++;
    }

    /**
     * 	在非空节点succ之前插入元素e  succ!=null
     *  succ是头节点或者不是头节点
     * 
     */
    void linkBefore(E e, Node<E> succ) {
        final Node<E> pred = succ.prev;//pred指向succ的前一个节点
        final Node<E> newNode = new Node<>(pred, e, succ);//初始化一个新的节点
        succ.prev = newNode;//succ.prev指向前一个节点
        if (pred == null)//如果succ的pred节点为null 说明succ是头节点
            first = newNode;//first指向newNode
        else
            pred.next = newNode;//succ的pred节点的.next节点指向newNode
        size++;//集合大小加一
        modCount++;
    }

    /**
     *	 移除非空的头节点 f
     */
    private E unlinkFirst(Node<E> f) {
        final E element = f.item;//获取到头节点的数据
        final Node<E> next = f.next;//指向头节点的next节点
        f.item = null;//
        f.next = null; // 有助于 gc
        first = next;//头节点指向前一个头节点的next节点
        if (next == null)// first==last==null
            last = null;
        else
            next.prev = null;//头节点的的前一个节点指向null
        size--;//集合大小减一
        modCount++;
        return element;
    }

    /**
     * 	移除非空的尾节点
     */
    private E unlinkLast(Node<E> l) {
        final E element = l.item;//获取到尾节点的数据
        final Node<E> prev = l.prev;//获取到尾节点的前一个节点
        l.item = null;
        l.prev = null; //有助于 gc
        last = prev;//尾节点指向前一个头节点的上一个节点
        if (prev == null)// first==last==null
            first = null;
        else
            prev.next = null;//尾节点的的后一个节点指向null
        size--;//集合大小减一
        modCount++;
        return element;
    }

    /**
     *	删除当前节点 x
     */
    E unlink(Node<E> x) {
        final E element = x.item;//获取到x节点的数据
        final Node<E> next = x.next;//获取到x的next节点
        final Node<E> prev = x.prev;//获取x的prev节点

        if (prev == null) {//x节点的prev节点为null 说明x是头节点
            first = next;//first指向x节点的next节点
        } else {
            prev.next = next;//x节点的prev节点的next节点指向x节点的next节点
            x.prev = null;//x节点的prev节点指向null 便于gc
        }

        if (next == null) {//x节点的next节点为null说明x是尾结点
            last = prev;//尾结点指向x节点的prev节点
        } else {
            next.prev = prev;//x节点的next节点的prev节点指向当前节点的prev节点
            x.next = null;//x节点的next节点指向null 便于gc
        }

        x.item = null;
        size--;
        modCount++;
        return element;
    }

    /**
     * 	获取头节点数据
     */
    public E getFirst() {
        final Node<E> f = first;
        if (f == null)
            throw new NoSuchElementException();
        return f.item;
    }

    /**
     *	 获取尾结点数据
     *	尾节点为null会抛出异常 
     */
    public E getLast() {
        final Node<E> l = last;
        if (l == null)
            throw new NoSuchElementException();
        return l.item;
    }

    /**
     *	 移除头节点
     *	头节点为null会抛出一样异常
     */
    public E removeFirst() {
        final Node<E> f = first;
        if (f == null)
            throw new NoSuchElementException();
        return unlinkFirst(f);
    }

    /**
     * 	移除尾节点
     * 	尾节点为null会抛出异常 
     */
    public E removeLast() {
        final Node<E> l = last;
        if (l == null)
            throw new NoSuchElementException();
        return unlinkLast(l);
    }

    /**
     * 	插入头节点
     */
    public void addFirst(E e) {
        linkFirst(e);
    }

    /**
     * 	插入尾结点
     */
    public void addLast(E e) {
        linkLast(e);
    }

    /**
     * 	判断集合中是否有包含数据为o的节点
     */
    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    /**
     * 	返回集合大小
     */
    public int size() {
        return size;
    }

    /**
     * 	插入尾结点
     */
    public boolean add(E e) {
        linkLast(e);
        return true;
    }

    /**
     * 	移除数据为o的节点
     * 	如果o为null 用==比较 否则用equals
     */
    public boolean remove(Object o) {
        if (o == null) {
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *	当前LinkedList中加入Collection中的元素
     */
    public boolean addAll(Collection<? extends E> c) {
        return addAll(size, c);
    }

    /**
     *	在下边index位置后插入Collection中的元素
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        checkPositionIndex(index);//校验当前下标可能当你拿到index的时候 LinkedList被别的线程操作了 删除了数据  这时候就会抛出异常  LinkedList并发非安全

        Object[] a = c.toArray();//将集合转化为数组
        int numNew = a.length;//获取数组的大小
        if (numNew == 0)// 空数组的话 直接放回
            return false;

        Node<E> pred, succ;// pred前节点 succ后节点
        if (index == size) {// 当前下标等于集合大小 队尾插入
            succ = null;//succ指向null
            pred = last;// pred指向尾结点 
        } else {// 链表中间插入
            succ = node(index);// succ指向index位置的节点
            pred = succ.prev;// pred指向succ的prev
        }
        //遍历数组添加到链表中
        for (Object o : a) {
            @SuppressWarnings("unchecked") E e = (E) o;//转换数据对象
            Node<E> newNode = new Node<>(pred, e, null);
            if (pred == null)//如果pred为空 说明first==last==null
                first = newNode;// first指向newNode
            else
                pred.next = newNode;//pred节点的next就是newNode
            pred = newNode;//把pred指向newNode
        }

        if (succ == null) {//表示一直是从队尾插入 将last指向pred
            last = pred;
        } else {//表示一直是从链表中间插入
            pred.next = succ;//pred节点的next指向succ
            succ.prev = pred;//succ节点的prev指向pred
        }

        size += numNew;
        modCount++;
        return true;
    }

    /**
     * 	将所有的节点的item、next、prev   并first = last = null;
     */
    public void clear() {
        // Clearing all of the links between nodes is "unnecessary", but:
        // - helps a generational GC if the discarded nodes inhabit
        //   more than one generation
        // - is sure to free memory even if there is a reachable Iterator
        for (Node<E> x = first; x != null; ) {
            Node<E> next = x.next;
            x.item = null;
            x.next = null;
            x.prev = null;
            x = next;
        }
        first = last = null;
        size = 0;
        modCount++;
    }

    /**
     * 	获取index处的节点数据
     */
    public E get(int index) {
        checkElementIndex(index);//校验下标
        return node(index).item;
    }

    /**
     * 	在下标index处设置数据e 返回就值
     */
    public E set(int index, E element) {
        checkElementIndex(index);//校验下标
        Node<E> x = node(index);//获取node
        E oldVal = x.item;// 获取旧值
        x.item = element;// 用新值覆盖旧值
        return oldVal;
    }

    /**
     * 	在下标index处插入数据e 返回就值
     */
    public void add(int index, E element) {
        checkPositionIndex(index);//校验下标

        if (index == size)// 下标等于当前集合大小
            linkLast(element);//插入尾结点
        else
            linkBefore(element, node(index));//在节点succ之前插入元素e succ!=null succ是头节点或者不是头节点
    }

    /**
     * 	移除index处的节点
     */
    public E remove(int index) {
        checkElementIndex(index);//校验下标
        return unlink(node(index));// 删除当前节点 x
    }

    /**
     * 	当前index处是否有节点
     */
    private boolean isElementIndex(int index) {
        return index >= 0 && index < size;
    }

    /**
     * 	当前index处是否有节点
     */
    private boolean isPositionIndex(int index) {
        return index >= 0 && index <= size;
    }

    /**
     * 	数组越界异常 异常信息拼接
     */
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }
    /**
     * 	校验下标index
     */
    private void checkElementIndex(int index) {
        if (!isElementIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }
    /**
     * 	校验下标index
     */
    private void checkPositionIndex(int index) {
        if (!isPositionIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * 	获取index位置的Node 为降低时间复杂度  用了一次二分查找
     */
    Node<E> node(int index) {
        if (index < (size >> 1)) {//下标小于size的一半 就从头节点向后开始遍历
            Node<E> x = first;
            for (int i = 0; i < index; i++)
                x = x.next;
            return x;
        } else {//下标大于等于size的一半 就从尾结点向前开始遍历
            Node<E> x = last;
            for (int i = size - 1; i > index; i--)
                x = x.prev;
            return x;
        }
    }

    // Search Operations

    /**
     * 	找到相等数据项的下标
     * 	从头节点开始遍历遍历节点 匹配相等的数据项  null用== 否者用equals
     * 	有的话 返回下标  没有的话 返回-1
     */
    public int indexOf(Object o) {
        int index = 0;
        if (o == null) {
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null)
                    return index;
                index++;
            }
        } else {
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item))
                    return index;
                index++;
            }
        }
        return -1;
    }

    /**
     * 	找到最后一个相等数据项的下标
     * 	从尾节点开始遍历遍历节点 匹配相等的数据项  null用== 否者用equals
     *	 有的话 返回下标  没有的话 返回-1
     */
    public int lastIndexOf(Object o) {
        int index = size;
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (x.item == null)
                    return index;
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (o.equals(x.item))
                    return index;
            }
        }
        return -1;
    }

    // 队列操作
    /**
     *	 获取队头数据项
     *	 如果头节点为null 返回null
     */
    public E peek() {
        final Node<E> f = first;
        return (f == null) ? null : f.item;
    }

    /**
     * 	获取队头数据项
     *	 如果头节为null 会抛出异常
     */
    public E element() {
        return getFirst();
    }

    /**
     * 	队头出队操作
     * 	如果头节点为null 返回null
     */
    public E poll() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    /**
     * 	队头出队操作
     * 	如果头节点为null 抛出异常
     */
    public E remove() {
        return removeFirst();
    }

    /**
     *	 队尾入队操作
     */
    public boolean offer(E e) {
        return add(e);
    }

    // 双端队列队列
    /**
     * 	从队头入队列
     */
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    /**
     * 	从队尾入队列
     */
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    /**
     *	获取队头数据
     *	如果为空返回null
     */
    public E peekFirst() {
        final Node<E> f = first;
        return (f == null) ? null : f.item;
     }

    /**
     *	获取队尾数据
     *	如果为空 返回null
     */
    public E peekLast() {
        final Node<E> l = last;
        return (l == null) ? null : l.item;
    }

    /**
     * 	从队头出队列
     * 	如果为空 返回null
     */
    public E pollFirst() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    /**
     *	从队尾出队列
     *	如果为空 返回null
     */
    public E pollLast() {
        final Node<E> l = last;
        return (l == null) ? null : unlinkLast(l);
    }

    /**
     * 	从队头入队列
     */
    public void push(E e) {
        addFirst(e);
    }

    /**
     *	移除头节点
     *	头节点为null会抛出一样异常
     */
    public E pop() {
        return removeFirst();
    }

    /**
     * 	移除从队头开始第一个数据为o的节点
     */
    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    /**
     * 	移除从队尾开始的第一个数据为o的节点
     * 	如果o为null 用==比较 否则用equals
     */
    public boolean removeLastOccurrence(Object o) {
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 	 将下标index及以后的数据生成ListIterator
	 * 	ListIterator 继承Iterator  可以向前查询 删除数据
	 *	 而且可以向后查找 添加数据  获取数据的下标
     */
    public ListIterator<E> listIterator(int index) {
        checkPositionIndex(index);//校验下标
        return new ListItr(index);
    }

    private class ListItr implements ListIterator<E> {
        private Node<E> lastReturned;
        private Node<E> next;
        private int nextIndex;
        private int expectedModCount = modCount;

        ListItr(int index) {
            // assert isPositionIndex(index);
            next = (index == size) ? null : node(index);
            nextIndex = index;
        }

        public boolean hasNext() {
            return nextIndex < size;
        }

        public E next() {
            checkForComodification();
            if (!hasNext())
                throw new NoSuchElementException();

            lastReturned = next;
            next = next.next;
            nextIndex++;
            return lastReturned.item;
        }

        public boolean hasPrevious() {
            return nextIndex > 0;
        }

        public E previous() {
            checkForComodification();
            if (!hasPrevious())
                throw new NoSuchElementException();

            lastReturned = next = (next == null) ? last : next.prev;
            nextIndex--;
            return lastReturned.item;
        }

        public int nextIndex() {
            return nextIndex;
        }

        public int previousIndex() {
            return nextIndex - 1;
        }

        public void remove() {
            checkForComodification();
            if (lastReturned == null)
                throw new IllegalStateException();

            Node<E> lastNext = lastReturned.next;
            unlink(lastReturned);
            if (next == lastReturned)
                next = lastNext;
            else
                nextIndex--;
            lastReturned = null;
            expectedModCount++;
        }

        public void set(E e) {
            if (lastReturned == null)
                throw new IllegalStateException();
            checkForComodification();
            lastReturned.item = e;
        }

        public void add(E e) {
            checkForComodification();
            lastReturned = null;
            if (next == null)
                linkLast(e);
            else
                linkBefore(e, next);
            nextIndex++;
            expectedModCount++;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            while (modCount == expectedModCount && nextIndex < size) {
                action.accept(next.item);
                lastReturned = next;
                next = next.next;
                nextIndex++;
            }
            checkForComodification();
        }

        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }
    /**
     * LinkedList最重要的数据结构 Node
     */
    private static class Node<E> {
        E item;//数据
        Node<E> next;//指向下一个节点
        Node<E> prev;//指向前一个节点

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }

    /**
     * @since 1.6
     */
    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    /**
     * 	将生成iterator对象
	 *  Iterator  可以向前查询 删除数据
	 *  	内部使用ListItr实现
     */
    private class DescendingIterator implements Iterator<E> {
        private final ListItr itr = new ListItr(size());
        public boolean hasNext() {
            return itr.hasPrevious();
        }
        public E next() {
            return itr.previous();
        }
        public void remove() {
            itr.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedList<E> superClone() {
        try {
            return (LinkedList<E>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Returns a shallow copy of this {@code LinkedList}. (The elements
     * themselves are not cloned.)
     *
     * @return a shallow copy of this {@code LinkedList} instance
     */
    public Object clone() {
        LinkedList<E> clone = superClone();

        // Put clone into "virgin" state
        clone.first = clone.last = null;
        clone.size = 0;
        clone.modCount = 0;

        // Initialize clone with our elements
        for (Node<E> x = first; x != null; x = x.next)
            clone.add(x.item);

        return clone;
    }

    /**
     * 	转成对象数组
     */
    public Object[] toArray() {
        Object[] result = new Object[size];//new一个想同大小的数组
        int i = 0;
        for (Node<E> x = first; x != null; x = x.next)
            result[i++] = x.item;//赋值
        return result;
    }

    /**
     * 	转成具体类型的数组
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < size)
            a = (T[])java.lang.reflect.Array.newInstance(
                                a.getClass().getComponentType(), size);
        int i = 0;
        Object[] result = a;
        for (Node<E> x = first; x != null; x = x.next)
            result[i++] = x.item;

        if (a.length > size)
            a[size] = null;

        return a;
    }

    private static final long serialVersionUID = 876323262645176354L;

    /**
     * Saves the state of this {@code LinkedList} instance to a stream
     * (that is, serializes it).
     *
     * @serialData The size of the list (the number of elements it
     *             contains) is emitted (int), followed by all of its
     *             elements (each an Object) in the proper order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        // Write out any hidden serialization magic
        s.defaultWriteObject();

        // Write out size
        s.writeInt(size);

        // Write out all elements in the proper order.
        for (Node<E> x = first; x != null; x = x.next)
            s.writeObject(x.item);
    }

    /**
     * Reconstitutes this {@code LinkedList} instance from a stream
     * (that is, deserializes it).
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in any hidden serialization magic
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++)
            linkLast((E)s.readObject());
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * list.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED} and
     * {@link Spliterator#ORDERED}.  Overriding implementations should document
     * the reporting of additional characteristic values.
     *
     * @implNote
     * The {@code Spliterator} additionally reports {@link Spliterator#SUBSIZED}
     * and implements {@code trySplit} to permit limited parallelism..
     *
     * @return a {@code Spliterator} over the elements in this list
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return new LLSpliterator<E>(this, -1, 0);
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    static final class LLSpliterator<E> implements Spliterator<E> {
        static final int BATCH_UNIT = 1 << 10;  // batch array size increment
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final LinkedList<E> list; // null OK unless traversed
        Node<E> current;      // current node; null until initialized
        int est;              // size estimate; -1 until first needed
        int expectedModCount; // initialized when est set
        int batch;            // batch size for splits

        LLSpliterator(LinkedList<E> list, int est, int expectedModCount) {
            this.list = list;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getEst() {
            int s; // force initialization
            final LinkedList<E> lst;
            if ((s = est) < 0) {
                if ((lst = list) == null)
                    s = est = 0;
                else {
                    expectedModCount = lst.modCount;
                    current = lst.first;
                    s = est = lst.size;
                }
            }
            return s;
        }

        public long estimateSize() { return (long) getEst(); }

        public Spliterator<E> trySplit() {
            Node<E> p;
            int s = getEst();
            if (s > 1 && (p = current) != null) {
                int n = batch + BATCH_UNIT;
                if (n > s)
                    n = s;
                if (n > MAX_BATCH)
                    n = MAX_BATCH;
                Object[] a = new Object[n];
                int j = 0;
                do { a[j++] = p.item; } while ((p = p.next) != null && j < n);
                current = p;
                batch = j;
                est = s - j;
                return Spliterators.spliterator(a, 0, j, Spliterator.ORDERED);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Node<E> p; int n;
            if (action == null) throw new NullPointerException();
            if ((n = getEst()) > 0 && (p = current) != null) {
                current = null;
                est = 0;
                do {
                    E e = p.item;
                    p = p.next;
                    action.accept(e);
                } while (p != null && --n > 0);
            }
            if (list.modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Node<E> p;
            if (action == null) throw new NullPointerException();
            if (getEst() > 0 && (p = current) != null) {
                --est;
                E e = p.item;
                current = p.next;
                action.accept(e);
                if (list.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

}
