package java.util.concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 *  线程安全	
 *  底层实现原理：单项链表 + 2个ReentrantLock + 2个Condition 
 * 	生产和消费数据用的不是同一独占锁 ，生产和消费是可以同时进行
 * 	生产者从队尾添加数据 ，消费者从队头的下一个节点开始获取数据
 * 	生产数据达到了容量则生产线程等待，消费者消费完了数据则消费者线程等待
 * 	头节点的数据项为null，不允许添加的数据项为null
 */
public class LinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -6903933977591709194L;

    /**
     *	单向链表节点数据结构
     */
    static class Node<E> {
    	//数据
        E item;

        /**
         * 	后继节点
         */
        Node<E> next;

        Node(E x) { item = x; }
    }
    /**
     * 	容量
     */
    private final int capacity;

    /**
     * 	当前的数据量
     * 	AtomicInteger 线程安全的
     */
    private final AtomicInteger count = new AtomicInteger();

    /**
     * 	头指针
     * 	头节点是不存放数据
     */
    transient Node<E> head;

    /**
     * 	尾指针
     */
    private transient Node<E> last;

    /**
     * 	消费者消费数据时要获取的可重入独占锁
     */
    private final ReentrantLock takeLock = new ReentrantLock();

    /**
     *	消费数据不为空的等待条件：用于控制消费者线程
     */
    private final Condition notEmpty = takeLock.newCondition();
    
    /**
     *	生产者生产数据时要获取的可重入独占锁
     *	put, offer, etc
     */
    private final ReentrantLock putLock = new ReentrantLock();

    /**
     * 	生产数据不为满的等待条件：用于控制生产者线程
     */
    private final Condition notFull = putLock.newCondition();

    /**
     * 	唤醒消费者不为空条件队列中的一个阻塞线程 从条件队列的头节点开始
     */
    private void signalNotEmpty() {
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * 	唤醒生产者不为满条件队列中的一个阻塞线程  从条件队列的头节点开始
     */
    private void signalNotFull() {
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            notFull.signal();
        } finally {
            putLock.unlock();
        }
    }

    /**
     * 	链表尾部添加数据
     * @param node the node
     */
    private void enqueue(Node<E> node) {
        last = last.next = node;
    }

    /**
     *	获取队头的下个节点的数据并移除之前的头节点 
     *	把下个节点作为头节点 并置空数据项
     *	 头节点是不存放数据项的
     * @return the node
     */
    private E dequeue() {
        Node<E> h = head;
        Node<E> first = h.next;
        h.next = h; // 利于gc
        head = first;
        E x = first.item;
        first.item = null;
        return x;
    }

    /**
     * 	锁住生产者锁和消费者锁
     */
    void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    /**
     * 	释放生产者锁和消费者锁
     */
    void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }
    
    /**
     * 	默认：容量为Integer.MAX_VALUE
     */
    public LinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }

    /**
     *	固定容量的 
     * @param capacity 容量
     */
    public LinkedBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        //初始化好头节点 不存放数据项
        last = head = new Node<E>(null);
    }

    /**
     *	初始化容量为Integer.MAX_VALUE
     *	集合c不为空的 把集合中的数据放入链表里
     * @param c 集合  
     */
    public LinkedBlockingQueue(Collection<? extends E> c) {
        //初始化容量为Integer.MAX_VALUE
    	this(Integer.MAX_VALUE);
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
        	//用于统计集合c中的元素个数
        	//集合c中的元素个数大于等于链表容量直接抛出异常
            int n = 0;
            for (E e : c) {
            	//集合c中如果有null直接抛出异常
                if (e == null)
                    throw new NullPointerException();
                if (n == capacity)
                    throw new IllegalStateException("Queue full");
                //链表尾部添加数据
                enqueue(new Node<E>(e));
                ++n;
            }
            count.set(n);
        } finally {
            putLock.unlock();
        }
    }
    /**
     * 	获取当前数据的个数
     */
    public int size() {
        return count.get();
    }
    /**
     * 	获取当前还可以添加的数据个数
     */
    public int remainingCapacity() {
        return capacity - count.get();
    }

    /**
     * 	从队尾插入数据 可中断的
     */
    public void put(E e) throws InterruptedException {
    	//添加的数据为null 直接抛出异常
        if (e == null) throw new NullPointerException();
        int c = -1;
        //new一个node
        Node<E> node = new Node<E>(e);
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        //获取生产者锁
        putLock.lockInterruptibly();
        try {
        	//如果当前链表已满  数据量等于容量
            while (count.get() == capacity) {
            	//挂起当前线程到不为满的条件队列中
            	//等待消费者线程唤醒
                notFull.await();
            }
            //链表尾部添加数据
            enqueue(node);
            //获取当前数据量 然后当前数据量+1
            c = count.getAndIncrement();
            //添加数据后的数据量小于容量
            if (c + 1 < capacity)
            	//唤醒生产者锁中不为满的条件队列中的一个线程
                notFull.signal();
        } finally {
        	//释放生产者的锁
            putLock.unlock();
        }
        //c为0 添加后的数据量不会0
        //唤醒消费者不为空条件队列中的一个阻塞线程 从条件队列的头节点开始
        if (c == 0)
            signalNotEmpty();
    }

    /**
     * 	入队列  有超时时间的	 可中断的
     * @return true 入队列成功  false 入队列失败
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
    	//对添加的数据项进行null校验 为null直接抛出异常
        if (e == null) throw new NullPointerException();
        //转成纳秒
        long nanos = unit.toNanos(timeout);
        int c = -1;
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        //获取生产者锁
        putLock.lockInterruptibly();
        try {
        	//如果当前数据量等于容量
            while (count.get() == capacity) {
                if (nanos <= 0)//纳秒数小于等于0
                    return false;
                //不为满条件队列阻塞 阻塞一定时间
                //指定时间内没有被唤醒 返回负数 指定时间内被唤醒 返回正数
                nanos = notFull.awaitNanos(nanos);
            }
            //链表尾部添加数据
            enqueue(new Node<E>(e));
            //获取当前数据量  然后当前数据量+1
            c = count.getAndIncrement();
            //添加数据后的数据量小于容量
            if (c + 1 < capacity)
            	//唤醒生产者锁中不为满的条件队列中的一个线程
                notFull.signal();
        } finally {
        	//释放生产者锁
            putLock.unlock();
        }
        //c为0 添加后的数据量不会0
        //唤醒消费者不为空条件队列中的一个阻塞线程 从条件队列的头节点开始
        if (c == 0)
            signalNotEmpty();
        return true;
    }

    /**
     *	入队列 
     *	@return true 入队列成功  false 入队列失败
     */
    public boolean offer(E e) {
    	//对添加的数据项进行null校验 为null直接抛出异常
        if (e == null) throw new NullPointerException();
        final AtomicInteger count = this.count;
        //如果当前数据量等于容量 直接返回false
        if (count.get() == capacity)
            return false;
        int c = -1;
        Node<E> node = new Node<E>(e);
        final ReentrantLock putLock = this.putLock;
        //获取消费者锁
        putLock.lock();
        try {
        	//当前数据量小于容量
            if (count.get() < capacity) {
            	//链表尾部添加数据
                enqueue(node);
                //获取当前数据量  然后当前数据量+1
                c = count.getAndIncrement();
                //添加数据后的数据量小于容量
                if (c + 1 < capacity)
                	//唤醒生产者锁中不为满的条件队列中的一个线程
                    notFull.signal();
            }
        } finally {
        	//释放消费者锁
            putLock.unlock();
        }
        //c为0 添加后的数据量不会0
        //唤醒消费者不为空条件队列中的一个阻塞线程 从条件队列的头节点开始
        if (c == 0)
            signalNotEmpty();
        return c >= 0;
    }
    /**
     * 	消费数据 可中断的 会移除队头数据
     *  @return 数据项
     */
    public E take() throws InterruptedException {
        E x;
        int c = -1;
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        //获取消费者锁
        takeLock.lockInterruptibly();
        try {
        	//如果当前数据量等于0
            while (count.get() == 0) {
            	//当前线程阻塞 加入消费者不为空条件队列
                notEmpty.await();
            }
            //获取头节点的下个节点的数据
            x = dequeue();
            //获取当前数据量  然后当前数据量-1
            c = count.getAndDecrement();
            //消费后的数据量大于1 说明还可以消费
            if (c > 1)
            	//唤醒消费者锁中不为满的条件队列中的一个线程
                notEmpty.signal();
        } finally {
        	//释放消费者锁
            takeLock.unlock();
        }
        //如果c等于容量 消费了一个之后 队列肯定不满了
        if (c == capacity)
        	//唤醒生产者锁不为满条件队列中的一个阻塞线程
            signalNotFull();
        return x;
    }
    /**
     * 出队列 会移除队头数据
     * @param timeout 超时时间
     * @param unit	时间单位
     * @return 数据项 可能会为null
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E x = null;
        int c = -1;
        //转成纳秒
        long nanos = unit.toNanos(timeout);
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        //获取消费者锁
        takeLock.lockInterruptibly();
        try {
        	//如果当前数据量等于0
            while (count.get() == 0) {
                if (nanos <= 0)//纳秒数小于等于0
                    return null;
                //不为空条件队列阻塞 阻塞一定时间
               //指定时间内没有被唤醒 返回负数 指定时间内被唤醒 返回正数
                nanos = notEmpty.awaitNanos(nanos);
            }
            //获取头节点的下个节点的数据
            x = dequeue();
           //获取当前数据量  然后当前数据量-1
            c = count.getAndDecrement();
            ////消费后的数据量大于1 说明还可以消费
            if (c > 1)
            	//唤醒消费者锁中不为满的条件队列中的一个线程
                notEmpty.signal();
        } finally {
        	//释放消费者锁
            takeLock.unlock();
        }
        //如果c等于容量 消费了一个之后 队列肯定不满了
        if (c == capacity)
        	//唤醒生产者锁不为满条件队列中的一个阻塞线程
            signalNotFull();
        return x;
    }
    
    /**
     * 	出队列 会移除队头数据
     * @return 数据项 可能为null
     */
    public E poll() {
        final AtomicInteger count = this.count;
        //获取当前链表的数据量 等于0说明没有数 直接放回null
        if (count.get() == 0)
            return null;
        E x = null;
        int c = -1;
        final ReentrantLock takeLock = this.takeLock;
        //获取消费者锁
        takeLock.lock();
        try {
        	//如果当前数据量大于0
            if (count.get() > 0) {
            	//获取头节点的next节点数据项
                x = dequeue();
                //获取到当前的数据量 然后减一
                c = count.getAndDecrement();
               //消费后的数据量大于1 说明还可以消费
                if (c > 1)
                	//唤醒消费者锁中不为满的条件队列中的一个线程
                    notEmpty.signal();
            }
        } finally {
        	//释放消费者锁
            takeLock.unlock();
        }
        //如果c等于容量 消费了一个之后 队列肯定不满了
        if (c == capacity)
        	//唤醒生产者锁不为满条件队列中的一个阻塞线程
            signalNotFull();
        return x;
    }
    /**
     * 获取队头数据 不移除数据
     * @return 数据项 可能为null
     */
    public E peek() {
    	//数据量为0 值返回null
        if (count.get() == 0)
            return null;
        final ReentrantLock takeLock = this.takeLock;
        //获取消费者锁
        takeLock.lock();
        try {
        	//队头是没有存放数据的 所以拿的是队头的后继中的数据项
            Node<E> first = head.next;
            if (first == null)
                return null;
            else
                return first.item;
        } finally {
        	//释放消费者锁
            takeLock.unlock();
        }
    }

    /**
     * 断开trail的后继节点p
     */
    void unlink(Node<E> p, Node<E> trail) {
        p.item = null;
        trail.next = p.next;
        if (last == p)
            last = trail;
        //当前数据量-1之前等于容量
        if (count.getAndDecrement() == capacity)
        	//唤醒消费者不为空条件队列中的一个阻塞线程 从条件队列的头节点开始
            notFull.signal();
    }

    /**
     *	删除数据o
     * @param o 
     * @return true 删除成功 false 删除失败
     */
    public boolean remove(Object o) {
    	//o为null直接返回false
        if (o == null) return false;
        //锁住生产者锁和消费者锁两把锁
        fullyLock();
        try {
        	//p是trail的后继节点 head中没有数据项所以不用比较
        	//用两个指针从头向尾寻找 找到相同数据的节点就移除掉
            for (Node<E> trail = head, p = trail.next;
                 p != null;
                 trail = p, p = p.next) {
            	//找到数据项相等的节点
                if (o.equals(p.item)) {
                	//断开trail的后继节点p
                    unlink(p, trail);
                    return true;
                }
            }
            return false;
        } finally {
        	//释放生产者锁和消费者锁
            fullyUnlock();
        }
    }

    /**
     *	判断是否包含数据o
     * @param o 数据项
     * @return true 包含 false 不包含
     */
    public boolean contains(Object o) {
    	//o为null 直接返回false
        if (o == null) return false;
        //锁住生产者锁和消费者锁两把锁
        fullyLock();
        try {
        	//从头遍历节点执行数据项的判断
            for (Node<E> p = head.next; p != null; p = p.next)
                if (o.equals(p.item))
                    return true;
            return false;
        } finally {
        	//释放生产者锁和消费者锁
            fullyUnlock();
        }
    }

    /**
     *	数组的拷贝
     */
    public Object[] toArray() {
    	//锁住生产者锁和消费者锁两把锁
        fullyLock();
        try {
            int size = count.get();
            Object[] a = new Object[size];
            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = p.item;
            return a;
        } finally {
        	//释放生产者锁和消费者锁
            fullyUnlock();
        }
    }

    /**
     *	拷贝成指定类型的数组
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
    	//锁住生产者锁和消费者锁两把锁
        fullyLock();
        try {
            int size = count.get();
            if (a.length < size)
                a = (T[])java.lang.reflect.Array.newInstance
                    (a.getClass().getComponentType(), size);

            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = (T)p.item;
            if (a.length > k)
                a[k] = null;
            return a;
        } finally {
        	//释放生产者锁和消费者锁
            fullyUnlock();
        }
    }

    public String toString() {
        fullyLock();
        try {
            Node<E> p = head.next;
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
            fullyUnlock();
        }
    }

    /**
     *  清空数组
     */
    public void clear() {
    	//锁住生产者锁和消费者锁两把锁
        fullyLock();
        try {
            for (Node<E> p, h = head; (p = h.next) != null; h = p) {
                h.next = h;
                p.item = null;
            }
            head = last;
            // assert head.item == null && head.next == null;
            if (count.getAndSet(0) == capacity)
                notFull.signal();
        } finally {
        	//释放生产者锁和消费者锁
            fullyUnlock();
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
        boolean signalNotFull = false;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            int n = Math.min(maxElements, count.get());
            // count.get provides visibility to first n Nodes
            Node<E> h = head;
            int i = 0;
            try {
                while (i < n) {
                    Node<E> p = h.next;
                    c.add(p.item);
                    p.item = null;
                    h.next = h;
                    h = p;
                    ++i;
                }
                return n;
            } finally {
                // Restore invariants even if c.add() threw
                if (i > 0) {
                    // assert h.item == null;
                    head = h;
                    signalNotFull = (count.getAndAdd(-i) == capacity);
                }
            }
        } finally {
            takeLock.unlock();
            if (signalNotFull)
                signalNotFull();
        }
    }

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        /*
         * Basic weakly-consistent iterator.  At all times hold the next
         * item to hand out so that if hasNext() reports true, we will
         * still have it to return even if lost race with a take etc.
         */

        private Node<E> current;
        private Node<E> lastRet;
        private E currentElement;

        Itr() {
            fullyLock();
            try {
                current = head.next;
                if (current != null)
                    currentElement = current.item;
            } finally {
                fullyUnlock();
            }
        }

        public boolean hasNext() {
            return current != null;
        }

        /**
         * Returns the next live successor of p, or null if no such.
         *
         * Unlike other traversal methods, iterators need to handle both:
         * - dequeued nodes (p.next == p)
         * - (possibly multiple) interior removed nodes (p.item == null)
         */
        private Node<E> nextNode(Node<E> p) {
            for (;;) {
                Node<E> s = p.next;
                if (s == p)
                    return head.next;
                if (s == null || s.item != null)
                    return s;
                p = s;
            }
        }

        public E next() {
            fullyLock();
            try {
                if (current == null)
                    throw new NoSuchElementException();
                E x = currentElement;
                lastRet = current;
                current = nextNode(current);
                currentElement = (current == null) ? null : current.item;
                return x;
            } finally {
                fullyUnlock();
            }
        }

        public void remove() {
            if (lastRet == null)
                throw new IllegalStateException();
            fullyLock();
            try {
                Node<E> node = lastRet;
                lastRet = null;
                for (Node<E> trail = head, p = trail.next;
                     p != null;
                     trail = p, p = p.next) {
                    if (p == node) {
                        unlink(p, trail);
                        break;
                    }
                }
            } finally {
                fullyUnlock();
            }
        }
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    static final class LBQSpliterator<E> implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final LinkedBlockingQueue<E> queue;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        long est;           // size estimate
        LBQSpliterator(LinkedBlockingQueue<E> queue) {
            this.queue = queue;
            this.est = queue.size();
        }

        public long estimateSize() { return est; }

        public Spliterator<E> trySplit() {
            Node<E> h;
            final LinkedBlockingQueue<E> q = this.queue;
            int b = batch;
            int n = (b <= 0) ? 1 : (b >= MAX_BATCH) ? MAX_BATCH : b + 1;
            if (!exhausted &&
                ((h = current) != null || (h = q.head.next) != null) &&
                h.next != null) {
                Object[] a = new Object[n];
                int i = 0;
                Node<E> p = current;
                q.fullyLock();
                try {
                    if (p != null || (p = q.head.next) != null) {
                        do {
                            if ((a[i] = p.item) != null)
                                ++i;
                        } while ((p = p.next) != null && i < n);
                    }
                } finally {
                    q.fullyUnlock();
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
            final LinkedBlockingQueue<E> q = this.queue;
            if (!exhausted) {
                exhausted = true;
                Node<E> p = current;
                do {
                    E e = null;
                    q.fullyLock();
                    try {
                        if (p == null)
                            p = q.head.next;
                        while (p != null) {
                            e = p.item;
                            p = p.next;
                            if (e != null)
                                break;
                        }
                    } finally {
                        q.fullyUnlock();
                    }
                    if (e != null)
                        action.accept(e);
                } while (p != null);
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null) throw new NullPointerException();
            final LinkedBlockingQueue<E> q = this.queue;
            if (!exhausted) {
                E e = null;
                q.fullyLock();
                try {
                    if (current == null)
                        current = q.head.next;
                    while (current != null) {
                        e = current.item;
                        current = current.next;
                        if (e != null)
                            break;
                    }
                } finally {
                    q.fullyUnlock();
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
     * Returns a {@link Spliterator} over the elements in this queue.
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
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new LBQSpliterator<E>(this);
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData The capacity is emitted (int), followed by all of
     * its elements (each an {@code Object}) in the proper order,
     * followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        fullyLock();
        try {
            // Write out any hidden stuff, plus capacity
            s.defaultWriteObject();

            // Write out all elements in the proper order.
            for (Node<E> p = head.next; p != null; p = p.next)
                s.writeObject(p.item);

            // Use trailing null as sentinel
            s.writeObject(null);
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in capacity, and any hidden stuff
        s.defaultReadObject();

        count.set(0);
        last = head = new Node<E>(null);

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
