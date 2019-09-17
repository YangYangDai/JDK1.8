package java.util.concurrent;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * 	双端阻塞队列 线程安全的
 * 	实现原理：双向链表 + 1个ReentrantLock + 2个Condition
 * 	生产者和消费者同一把独占锁,同一时刻只有一个线程能获取到锁生产者在生产数据或者一个消费者在消费数据
 * 	生产的数据不能为null
 * 	生产者生产数据、消费者消费数据可以从队头或者队尾
 */
public class LinkedBlockingDeque<E>
    extends AbstractQueue<E>
    implements BlockingDeque<E>, java.io.Serializable {

    private static final long serialVersionUID = -387911632671998426L;

    /**
     * 	双向链表节点数据结构
     */
    static final class Node<E> {
        /**
         * 	数据项
         */
        E item;

        /**
         * 	前驱
         */
        Node<E> prev;

        /**
         * 	后继
         */
        Node<E> next;

        Node(E x) {
            item = x;
        }
    }

    /**
     * 	头节点指针
     */
    transient Node<E> first;

    /**
     * 	尾节点指针
     */
    transient Node<E> last;

    /**
     * 	数据个数
     */
    private transient int count;

    /**
     * 	容量
     */
    private final int capacity;

    /**
     *	消费者和生产者用同一把锁
     *	可重入的独占锁
     */
    final ReentrantLock lock = new ReentrantLock();

    /**
     * 	数据量不为空的Condition 消费者等待生产者唤醒其notEmpty条件队列中的一个阻塞线程
     * notEmpty Condition
     */
    private final Condition notEmpty = lock.newCondition();

    /**
     * 	数据量不为满的Condition 生产者等待消费者唤醒其notFull条件队列的一个阻塞线程
     */
    private final Condition notFull = lock.newCondition();

    /**
     *	默认容量为Integer.MAX_VALUE
     */
    public LinkedBlockingDeque() {
        this(Integer.MAX_VALUE);
    }

    /**
     * 	容量为capacity
     */
    public LinkedBlockingDeque(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
    }

    /**
     *	初始化容量为Integer.MAX_VALUE
     *	集合c不为空的 把集合中的数据放入链表里
     * @param c 集合
     */
    public LinkedBlockingDeque(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        final ReentrantLock lock = this.lock;
        lock.lock(); 
        try {
            for (E e : c) {
            	//集合c中如果有null直接抛出异常
                if (e == null)
                    throw new NullPointerException();
                //链表尾部添加node 添加的数据量大于容量时 抛出异常 
                if (!linkLast(new Node<E>(e)))
                    throw new IllegalStateException("Deque full");
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     *	从头节点添加节点 
     *	@return true 添加成功 false 数据量大于等于容量时
     */
    private boolean linkFirst(Node<E> node) {
    	//数据量大于等于容量时 返回false
        if (count >= capacity)
            return false;
        Node<E> f = first;
        node.next = f;
        first = node;
        if (last == null)
            last = node;
        else
            f.prev = node;
        //数据量+1
        ++count;
        //生产者唤醒消费者的notEmpty条件队列中的一个阻塞线程
        notEmpty.signal();
        return true;
    }

    /**
     * 	链表尾部添加node
     * 	@return true 添加成功 false 数据量大于等于容量时
     */
    private boolean linkLast(Node<E> node) {
    	//如果当前数据量大于等于容量
        if (count >= capacity)
            return false;
        Node<E> l = last;
        node.prev = l;
        last = node;
        if (first == null)
            first = node;
        else
            l.next = node;
        //数据量+1
        ++count;
        //生产者唤醒消费者的notEmpty条件队列中的一个阻塞线程
        notEmpty.signal();
        return true;
    }

    /**
     * 	获取头节点数据并且删除头节点
     * @return 可能会返回null
     */
    private E unlinkFirst() {
        Node<E> f = first;
        //头节点为null直接返回null
        if (f == null)
            return null;
        //后面就是节点操作了 
        //看下应该能懂的
        Node<E> n = f.next;
        E item = f.item;
        f.item = null;
        f.next = f; // help GC
        first = n;
        if (n == null)
            last = null;
        else
            n.prev = null;
        //数据量-1
        --count;
        //消费者唤醒生产者的notFull条件队列中的一个阻塞线程
        notFull.signal();
        return item;
    }

    /**
     *	 获取尾节点数据并且删除尾节点
     *	@return 可能会返回null
     */
    private E unlinkLast() {
        Node<E> l = last;
        //尾节点为null直接返回null
        if (l == null)
            return null;
        //后面就是节点操作了 
        //看下应该能懂的
        Node<E> p = l.prev;
        E item = l.item;
        l.item = null;
        l.prev = l; // help GC
        last = p;
        if (p == null)
            first = null;
        else
            p.next = null;
        //数据量-1
        --count;
        //消费者唤醒生产者的notFull条件队列中的一个阻塞线程
        notFull.signal();
        return item;
    }

    /**
     * 	删除节点x
     */
    void unlink(Node<E> x) {
        Node<E> p = x.prev;
        Node<E> n = x.next;
        //x的前驱为null说明是头节点
        if (p == null) {
        	//获取头节点数据并且删除头节点
            unlinkFirst();
        } else if (n == null) {//x的后继为null说明是尾结点
        	//获取尾节点数据并且删除尾节点
            unlinkLast();
        } else {
        	//都不为null
        	//把x的前驱、后继相链接
            p.next = n;
            n.prev = p;
            x.item = null;
            //数据量-1
            --count; 
            //消费者唤醒生产者的notFull条件队列中的一个阻塞线程
            notFull.signal();
        }
    }
    /**
     * 	从头节点添加数据 
     */
    public void addFirst(E e) {
    	//从头节点添加节点 添加失败说明队列已满 直接抛出异常 
        if (!offerFirst(e))
            throw new IllegalStateException("Deque full");
    }

    /**
     *	 从尾节点添加数据 
     */
    public void addLast(E e) {
    	//从尾节点添加节点 添加失败队列已满 直接抛出异常 
        if (!offerLast(e))
            throw new IllegalStateException("Deque full");
    }

    /**
     * 	从头节点添加数据
     * 	@return true 添加成功 false 添加失败
     */
    public boolean offerFirst(E e) {
    	//数据为null直接抛出异常
        if (e == null) throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return linkFirst(node);//从头节点添加节点 
        } finally {
            lock.unlock();
        }
    }

    /**
     * 	从尾节点添加数据
     * 	@return true 添加成功 false 添加失败
     */
    public boolean offerLast(E e) {
    	//数据为null直接抛出异常
        if (e == null) throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return linkLast(node);//从尾节点添加节点 
        } finally {
            lock.unlock();
        }
    }

    /**
     * 	从头节点添加节点  可中断的
     */
    public void putFirst(E e) throws InterruptedException {
    	//e为null 直接异常
        if (e == null) throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
        	//从头节点添加数据 队列满了 添加不成功
            while (!linkFirst(node))
            	//消费者唤醒生产者的notFull条件队列中的一个阻塞线程
                notFull.await();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 	从尾节点添加节点 
     */
    public void putLast(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
        	//链表尾部添加node 队列满了 添加不成功
            while (!linkLast(node))
            	//消费者唤醒生产者的notFull条件队列中的一个阻塞线程
                notFull.await();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 从头节点添加节点 
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true 添加成功 false 添加失败
     */
    public boolean offerFirst(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (e == null) throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        //换算成纳秒
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
        	//从头节点添加节点失败
            while (!linkFirst(node)) {
            	//表示超时了 直接返回false
                if (nanos <= 0)
                    return false;
                //挂起固定的时间nanos
                //超时返回小于等于0 
                nanos = notFull.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     *    从尾节点添加节点 有超时时间 可中断的
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true 添加成功 false 添加失败
     */
    public boolean offerLast(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (e == null) throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        //换算成纳秒
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
        	//链表尾部添加node
            while (!linkLast(node)) {
            	//表示超时了 直接返回false
                if (nanos <= 0)
                    return false;
                //挂起固定的时间nanos
                //超时返回小于等于0 
                nanos = notFull.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     *  返回头节点的数据项并移除头结点 
     * @return 数据项 null的话会抛出异常
     */
    public E removeFirst() {
    	//获取队头数据并删除头节点
        E x = pollFirst();
        if (x == null) throw new NoSuchElementException();
        return x;
    }

    /**
     * 返回尾节点的数据项并移除尾结点 
     * @return 数据项 null的话会抛出异常
     */
    public E removeLast() {
    	//获取队尾数据并删除尾节点
        E x = pollLast();
        if (x == null) throw new NoSuchElementException();
        return x;
    }
    /**
     * 	队头出栈 获取队头数据并删除头节点
     */
    public E pollFirst() {
        final ReentrantLock lock = this.lock;
        //获取锁
        lock.lock();
        try {
        	//获取头节点数据并且删除头节点
            return unlinkFirst();
        } finally {
        	//释放锁
            lock.unlock();
        }
    }
    /**
     * 	队尾出栈 获取队尾数据并删除尾节点
     */
    public E pollLast() {
        final ReentrantLock lock = this.lock;
        //获取锁
        lock.lock();
        try {
        	//获取尾节点数据并且删除尾节点
            return unlinkLast();
        } finally {
        	//释放锁
            lock.unlock();
        }
    }	
    /**
     * 获取头节点数据并删除头节点 可中断的
     */
    public E takeFirst() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            E x;
            //获取头结点为null的话
            while ( (x = unlinkFirst()) == null)
            	//消费者等待生产者唤醒其notEmpty条件队列中的一个阻塞线程
                notEmpty.await();
            return x;
        } finally {
            lock.unlock();
        }
    }
    /**
     * 获取尾节点数据并且删除尾节点 可中断的
     */
    public E takeLast() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            E x;
            //获取尾结点为null的话
            while ( (x = unlinkLast()) == null)
            	//消费者等待生产者唤醒其notEmpty条件队列中的一个阻塞线程
                notEmpty.await();
            return x;
        } finally {
            lock.unlock();
        }
    }
    /**
     * 	队头出栈 获取队头数据并删除头节点  有超时时间 可中断的
     */
    public E pollFirst(long timeout, TimeUnit unit)
        throws InterruptedException {
    	//转成纳秒
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        //获取锁
        lock.lockInterruptibly();
        try {
            E x;
            //获取头节点数据并且删除头节点 等于null
            //说明队列里面没有数据了
            while ( (x = unlinkFirst()) == null) {
            	//纳秒数小于等于0 超时 直接返回null
            	if (nanos <= 0)
                    return null;
                //返回负数 说明超时了 返回正数说明没有超时
                nanos = notEmpty.awaitNanos(nanos);
            }
            return x;
        } finally {
        	//释放锁
            lock.unlock();
        }
    }
    /**
     * 	队尾出栈 获取队头数据并删除头节点  有超时时间 可中断的
     */
    public E pollLast(long timeout, TimeUnit unit)
        throws InterruptedException {
    	//转成纳秒
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            E x;
            //获取头节点数据并且删除头节点 等于null
            //说明队列里面没有数据了
            while ( (x = unlinkLast()) == null) {
            	//纳秒数小于等于0 超时 直接返回null
                if (nanos <= 0)
                    return null;
                //返回负数 说明超时了 返回正数说明没有超时
                nanos = notEmpty.awaitNanos(nanos);
            }
            return x;
        } finally {
            lock.unlock();
        }
    }

    /**
     *	获取队头数据 如果数据项为null 抛出异常
     */
    public E getFirst() {
        E x = peekFirst();
        if (x == null) throw new NoSuchElementException();
        return x;
    }

    /**
     * 	获取队尾数据 如果数据项为null 抛出异常
     */
    public E getLast() {
        E x = peekLast();
        if (x == null) throw new NoSuchElementException();
        return x;
    }
    
    /**
     * 	获取队尾数据 
     * 	返回的数据可以为null
     */
    public E peekFirst() {
        final ReentrantLock lock = this.lock;
        //获取锁
        lock.lock();
        try {
            return (first == null) ? null : first.item;
        } finally {
        	//释放锁
            lock.unlock();
        }
    }
    /**
     *	 获取队尾数据
     *	 返回的数据可以为null
     */
    public E peekLast() {
    	final ReentrantLock lock = this.lock;
    	//获取锁
        lock.lock();
        try {
            return (last == null) ? null : last.item;
        } finally {
        	//释放锁
            lock.unlock();
        }
    }
    /**
     * 移除有与o相等的数据项节点 从头节点开始查找
     * @return true 移除成功 false 没有o这个数据项的节点
     */
    public boolean removeFirstOccurrence(Object o) {
    	//o为null直接返回false
        if (o == null) return false;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
        	//从头节点向后遍历判断
            for (Node<E> p = first; p != null; p = p.next) {
            	//找等相等数据项的节点 删除掉
                if (o.equals(p.item)) {
                	//删除节点p
                    unlink(p);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    /**
     * 移除有与o相等的数据项节点 从尾节点开始查找
     * @return true 移除成功 false 没有o这个数据项的节点
     */
    public boolean removeLastOccurrence(Object o) {
        if (o == null) return false;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
        	//从尾节点向后遍历判断
            for (Node<E> p = last; p != null; p = p.prev) {
            	//找等相等数据项的节点 删除掉
                if (o.equals(p.item)) {
                	//删除节点p
                    unlink(p);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     *	添加数据 从头节点添加
     *@return true 添加成功 false 添加失败
     */
    public boolean add(E e) {
    	//从尾节点添加数据 
        addLast(e);
        return true;
    }

    /**
     * 添加数据 从尾节点添加
     * @return true 添加成功 false 添加失败
     */
    public boolean offer(E e) {
        return offerLast(e);
    }

    /**
     * 	从尾节点添加节点  可中断的
     */
    public void put(E e) throws InterruptedException {
        putLast(e);
    }

    /**
     * 从尾节点添加节点 有超时时间 可中断的
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        return offerLast(e, timeout, unit);
    }

    /**
     *	移除头结点 
     * @return 
     */
    public E remove() {
        return removeFirst();
    }
    /**
     * 	从头节点获取数据并移除头节点
     */
    public E poll() {
        return pollFirst();
    }
    /**
     * 从头节点获取数据并移除头节点 可中断的
     */
    public E take() throws InterruptedException {
        return takeFirst();
    }
    /**
     * 	从头节点获取数据并移除头节点 有超时时间 可中断
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return pollFirst(timeout, unit);
    }

    /**
     *	获取队头数据 如果数据项为null 抛出异常
     */
    public E element() {
        return getFirst();
    }
    /**
     * 	获取队尾数据
     */
    public E peek() {
        return peekFirst();
    }

    /*
     * 获取队列还可以添加的数据量(capacity - count)
     */
    public int remainingCapacity() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return capacity - count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = Math.min(maxElements, count);
            for (int i = 0; i < n; i++) {
                c.add(first.item);   // In this order, in case add() throws.
                unlinkFirst();
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    // 栈的方法

    /**
     * 从头节点添加数据 
     */
    public void push(E e) {
        addFirst(e);
    }

    /**
     * 返回头节点的数据项并移除头结点 
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E pop() {
        return removeFirst();
    }

    // 集合方法

    /**
     *	移除有与o相等的数据项节点 从头节点开始查找
     */
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    /**
     * 数据量
     */
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 判断是否包含对象o
     * @return true 包含 false 不包含
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
        	//从头节点开始遍历
            for (Node<E> p = first; p != null; p = p.next)
                if (o.equals(p.item))
                    return true;
            return false;
        } finally {
            lock.unlock();
        }
    }
    /**
     *	转成数组返回
     * @return an array containing all of the elements in this deque
     */
    @SuppressWarnings("unchecked")
    public Object[] toArray() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] a = new Object[count];
            int k = 0;
            for (Node<E> p = first; p != null; p = p.next)
                a[k++] = p.item;
            return a;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 指定数组的类型
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (a.length < count)
                a = (T[])java.lang.reflect.Array.newInstance
                    (a.getClass().getComponentType(), count);

            int k = 0;
            for (Node<E> p = first; p != null; p = p.next)
                a[k++] = (T)p.item;
            if (a.length > k)
                a[k] = null;
            return a;
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Node<E> p = first;
            if (p == null)
                return "[]";

            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (;;) {
                E e = p.item;
                sb.append(e == this ? "(this Collection)" : e);
                p = p.next;
                if (p == null)
                    return sb.append(']').toString();
                sb.append(',').append(' ');
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清空链表
     */
    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (Node<E> f = first; f != null; ) {
                f.item = null;
                Node<E> n = f.next;
                f.prev = null;
                f.next = null;
                f = n;
            }
            first = last = null;
            count = 0;
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取迭代器
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * Returns an iterator over the elements in this deque in reverse
     * sequential order.  The elements will be returned in order from
     * last (tail) to first (head).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this deque in reverse order
     */
    public Iterator<E> descendingIterator() {
        return new DescendingItr();
    }

    /**
     * Base class for Iterators for LinkedBlockingDeque
     */
    private abstract class AbstractItr implements Iterator<E> {
        /**
         * The next node to return in next()
         */
        Node<E> next;

        /**
         * nextItem holds on to item fields because once we claim that
         * an element exists in hasNext(), we must return item read
         * under lock (in advance()) even if it was in the process of
         * being removed when hasNext() was called.
         */
        E nextItem;

        /**
         * Node returned by most recent call to next. Needed by remove.
         * Reset to null if this element is deleted by a call to remove.
         */
        private Node<E> lastRet;

        abstract Node<E> firstNode();
        abstract Node<E> nextNode(Node<E> n);

        AbstractItr() {
            // set to initial position
            final ReentrantLock lock = LinkedBlockingDeque.this.lock;
            lock.lock();
            try {
                next = firstNode();
                nextItem = (next == null) ? null : next.item;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Returns the successor node of the given non-null, but
         * possibly previously deleted, node.
         */
        private Node<E> succ(Node<E> n) {
            // Chains of deleted nodes ending in null or self-links
            // are possible if multiple interior nodes are removed.
            for (;;) {
                Node<E> s = nextNode(n);
                if (s == null)
                    return null;
                else if (s.item != null)
                    return s;
                else if (s == n)
                    return firstNode();
                else
                    n = s;
            }
        }

        /**
         * Advances next.
         */
        void advance() {
            final ReentrantLock lock = LinkedBlockingDeque.this.lock;
            lock.lock();
            try {
                // assert next != null;
                next = succ(next);
                nextItem = (next == null) ? null : next.item;
            } finally {
                lock.unlock();
            }
        }

        public boolean hasNext() {
            return next != null;
        }

        public E next() {
            if (next == null)
                throw new NoSuchElementException();
            lastRet = next;
            E x = nextItem;
            advance();
            return x;
        }

        public void remove() {
            Node<E> n = lastRet;
            if (n == null)
                throw new IllegalStateException();
            lastRet = null;
            final ReentrantLock lock = LinkedBlockingDeque.this.lock;
            lock.lock();
            try {
                if (n.item != null)
                    unlink(n);
            } finally {
                lock.unlock();
            }
        }
    }

    /** Forward iterator */
    private class Itr extends AbstractItr {
        Node<E> firstNode() { return first; }
        Node<E> nextNode(Node<E> n) { return n.next; }
    }

    /** Descending iterator */
    private class DescendingItr extends AbstractItr {
        Node<E> firstNode() { return last; }
        Node<E> nextNode(Node<E> n) { return n.prev; }
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    static final class LBDSpliterator<E> implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final LinkedBlockingDeque<E> queue;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        long est;           // size estimate
        LBDSpliterator(LinkedBlockingDeque<E> queue) {
            this.queue = queue;
            this.est = queue.size();
        }

        public long estimateSize() { return est; }

        public Spliterator<E> trySplit() {
            Node<E> h;
            final LinkedBlockingDeque<E> q = this.queue;
            int b = batch;
            int n = (b <= 0) ? 1 : (b >= MAX_BATCH) ? MAX_BATCH : b + 1;
            if (!exhausted &&
                ((h = current) != null || (h = q.first) != null) &&
                h.next != null) {
                Object[] a = new Object[n];
                final ReentrantLock lock = q.lock;
                int i = 0;
                Node<E> p = current;
                lock.lock();
                try {
                    if (p != null || (p = q.first) != null) {
                        do {
                            if ((a[i] = p.item) != null)
                                ++i;
                        } while ((p = p.next) != null && i < n);
                    }
                } finally {
                    lock.unlock();
                }
                if ((current = p) == null) {
                    est = 0L;
                    exhausted = true;
                }
                else if ((est -= i) < 0L)
                    est = 0L;
                if (i > 0) {
                    batch = i;
                    return Spliterators.spliterator
                        (a, 0, i, Spliterator.ORDERED | Spliterator.NONNULL |
                         Spliterator.CONCURRENT);
                }
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            if (action == null) throw new NullPointerException();
            final LinkedBlockingDeque<E> q = this.queue;
            final ReentrantLock lock = q.lock;
            if (!exhausted) {
                exhausted = true;
                Node<E> p = current;
                do {
                    E e = null;
                    lock.lock();
                    try {
                        if (p == null)
                            p = q.first;
                        while (p != null) {
                            e = p.item;
                            p = p.next;
                            if (e != null)
                                break;
                        }
                    } finally {
                        lock.unlock();
                    }
                    if (e != null)
                        action.accept(e);
                } while (p != null);
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null) throw new NullPointerException();
            final LinkedBlockingDeque<E> q = this.queue;
            final ReentrantLock lock = q.lock;
            if (!exhausted) {
                E e = null;
                lock.lock();
                try {
                    if (current == null)
                        current = q.first;
                    while (current != null) {
                        e = current.item;
                        current = current.next;
                        if (e != null)
                            break;
                    }
                } finally {
                    lock.unlock();
                }
                if (current == null)
                    exhausted = true;
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.NONNULL |
                Spliterator.CONCURRENT;
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this deque.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
     *
     * @implNote
     * The {@code Spliterator} implements {@code trySplit} to permit limited
     * parallelism.
     *
     * @return a {@code Spliterator} over the elements in this deque
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new LBDSpliterator<E>(this);
    }

    /**
     * Saves this deque to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData The capacity (int), followed by elements (each an
     * {@code Object}) in the proper order, followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // Write out capacity and any hidden stuff
            s.defaultWriteObject();
            // Write out all elements in the proper order.
            for (Node<E> p = first; p != null; p = p.next)
                s.writeObject(p.item);
            // Use trailing null as sentinel
            s.writeObject(null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reconstitutes this deque from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        count = 0;
        first = null;
        last = null;
        // Read in all elements and place in queue
        for (;;) {
            @SuppressWarnings("unchecked")
            E item = (E)s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }

}
