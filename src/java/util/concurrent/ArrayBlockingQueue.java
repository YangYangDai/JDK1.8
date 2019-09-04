package java.util.concurrent;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.lang.ref.WeakReference;
import java.util.Spliterators;
import java.util.Spliterator;

/**
 * 数组阻塞队列
 * 底层：数组+ReentrantLock + notFullCondition + notFullCondition
 * notFullCondition+notFullCondition用的是同一个ReentrantLock
 * 出队列与入队列是阻塞进行的，某个时间点，只有一个线程能获取到锁，然后生产或者消费数据
 * 添加的数据不能为null 队列长度是有限制的 
 */
public class ArrayBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    private static final long serialVersionUID = -817911632652898426L;

   /**
    * 对象数组
    */
    final Object[] items;

    /**
     * 获取数据的下标（take, poll, peek or remove）
     */
    int takeIndex;

    /**
     * 添加数据的下标（put, offer, or add）
     */
    int putIndex;

    /**
     * 数据个数
     */
    int count;

    /**
     * 可重入的独占锁
     */
    final ReentrantLock lock;

    /**
     * 不为空的等待条件：用于控制消费者线程
     */
    private final Condition notEmpty;

   /**
    * 没有满的等待条件：用于控制生产者线程
    */
    private final Condition notFull;

    /**
     * 迭代器
     */
    transient Itrs itrs = null;

    /**
     * Circularly decrement i.
     */
    final int dec(int i) {
        return ((i == 0) ? items.length : i) - 1;
    }

    /**
     * 返回对应下标的数据
     */
    @SuppressWarnings("unchecked")
    final E itemAt(int i) {
        return (E) items[i];
    }

    /**
     * 校验v是不是null 是的话抛出异常
     * @param v 数据
     */
    private static void checkNotNull(Object v) {
        if (v == null)
            throw new NullPointerException();
    }

    /**
     * 入队列
     * 只有获取到独占锁的线程才能调用该方法
     * 在putIndex处保存数据 
     * 唤醒消费者
     */
    private void enqueue(E x) {
    	//获取到数组
        final Object[] items = this.items;
        //在下标putIndex的位置保存x
        items[putIndex] = x;
        //如果putIndex+1等于数组的长度的话
        //就要从头开始存放数据了 putIndex=0
        if (++putIndex == items.length)
            putIndex = 0;
        //数据个数加1
        count++;
        //生产了数据 唤醒消费者(不为空条件队列中的一个阻塞线程) 队头开始唤醒
        notEmpty.signal();
    }

    /**
     * 出队列
     * 只有获取到独占锁的线程才能调用该方法
     * 获取takeIndex处的数据
     * 唤醒生产者
     */
    private E dequeue() {
        final Object[] items = this.items;
        @SuppressWarnings("unchecked")
        //获取下标takeIndex处的数据
        E x = (E) items[takeIndex];
        //置空
        items[takeIndex] = null;
        //takeIndex+1等于数组长度
        //说明获取到了数据的末端了
        //需要从头开始获取 takeIndex设置为0
        if (++takeIndex == items.length)
            takeIndex = 0;
        //数据量减一
        count--;
        //迭代器不为空的 也出队列
        if (itrs != null)
            itrs.elementDequeued();
        //消费了数据 唤醒生产者(不为满条件队列中的一个阻塞线程) 队头开始唤醒
        notFull.signal();
        return x;
    }

    /**
     * 删除指定下标的数据
     */
    void removeAt(final int removeIndex) {
        final Object[] items = this.items;
        //如果要删除的下标等于takeIndex
        if (removeIndex == takeIndex) {
        	//takeIndex位置置空
            items[takeIndex] = null;
            //takeIndex+1等于数组长度
            //takeIndex从下标0开始获取
            if (++takeIndex == items.length)
                takeIndex = 0;
            //数据量减一
            count--;
            //如果迭代器不为null
            if (itrs != null)
            	//移除数据
                itrs.elementDequeued();
        } else {
            final int putIndex = this.putIndex;
            //死循环
            //把数据向队头方向移动
            for (int i = removeIndex;;) {
                int next = i + 1;
                //next等于数组长度了 next要从0开始
                if (next == items.length)
                    next = 0;
                //next不等于putIndex
                if (next != putIndex) {
                    items[i] = items[next];
                    i = next;
                } else {
                    items[i] = null;
                    this.putIndex = i;
                    break;
                }
            }
            //数据量减一
            count--;
            //迭代器减一
            if (itrs != null)
                itrs.removedAt(removeIndex);
        }
       //消费掉了一个 唤醒生产者(条件不为满条件队列中的一个) 队头开始唤醒
        notFull.signal();
    }

    /**
     * 默认是非公平的锁
     * @param capacity 容量
     */
    public ArrayBlockingQueue(int capacity) {
        this(capacity, false);
    }

    /**
     * @param capacity 容量
     * @param fair true 公平锁 false 非公平锁
     */
    public ArrayBlockingQueue(int capacity, boolean fair) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        //初始化对象数组
        this.items = new Object[capacity];
        //可重入锁
        lock = new ReentrantLock(fair);
        //不为空的等待条件：用于控制消费者线程
        notEmpty = lock.newCondition();
        //没有满的等待条件：用于控制生产者线程
        notFull =  lock.newCondition();
    }

    /**
     *	
     * @param capacity 容量
     * @param fair true 公平锁 false 非公平锁 
     * @param c 集合
     */
    public ArrayBlockingQueue(int capacity, boolean fair,
                              Collection<? extends E> c) {
    	//先初始化
        this(capacity, fair);
        final ReentrantLock lock = this.lock;
        //获取锁
        lock.lock(); 
        try {
        	//i用于计算集合c中的元素个数
            int i = 0;
            try {
                for (E e : c) {
                	//校验e是不是null 是的话抛出异常
                    checkNotNull(e);
                    items[i++] = e;
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
            	//超出数组的容量 直接抛出异常
                throw new IllegalArgumentException();
            }
            //当前的数据量
            count = i;
            //如果数据量等于容量putIndex=0
            putIndex = (i == capacity) ? 0 : i;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 入队列
     * 最后调用的是自己的offer(E e) 
     * @param e 要添加的数据
     */
    public boolean add(E e) {
        return super.add(e);
    }

    /**
     * 尝试入队列
     * @return true 添加成功 false 添加失败
     */
    public boolean offer(E e) {
    	//校验e是不是null 是的话抛出异常
        checkNotNull(e);
        final ReentrantLock lock = this.lock;
        //获取锁
        lock.lock();
        try {
        	//当前数据量等于数组的长度 满了 直接返回false
            if (count == items.length)
                return false;
            else {
            	//在putIndex的位置保存数据
            	//入队列 然后唤醒消费者者不为空条件队列中的一个 从队头开始
                enqueue(e);
                return true;
            }
        } finally {
        	//释放锁
            lock.unlock();
        }
    }

    /**
     * 入队列 可中断的
     * 如果数组满了 就等待数组不为满
     */
    public void put(E e) throws InterruptedException {
        //校验数据E 为null直接抛出空指针异常
    	checkNotNull(e);
        final ReentrantLock lock = this.lock;
        //获取独占锁 可中断的
        lock.lockInterruptibly();
        try {
        	//数据的个数等于数组的长度
        	//说明数据已经放满了 放不下了
            while (count == items.length)
            	//生产者线程等待 等待消费者的唤醒
                notFull.await();
            //入队列 然后唤醒消费者者不为空条件队列中的一个 从队头开始
            enqueue(e);
        } finally {
        	//释放独占锁 
            lock.unlock();
        }
    }

    /**
     * 入队列 超时可中断的
     * @param timeout  超时时间
     * @param unit	时间单位
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
    	//校验数据E 为null直接抛出空指针异常
        checkNotNull(e);
        //换算成纳秒
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        //获取锁
        lock.lockInterruptibly();
        try {
            while (count == items.length) {
            	//表示超时了 直接返回false
                if (nanos <= 0)
                    return false;
                //挂起固定的时间nanos
                //超时返回小于等于0 
                nanos = notFull.awaitNanos(nanos);
            }
            //入队列 然后唤醒消费者者不为空条件队列中的一个 从队头开始
            enqueue(e);
            return true;
        } finally {
        	//释放锁
            lock.unlock();
        }
    }
    /**
     * 出队列
     * @param E 返回数据
     */
    public E poll() {
        final ReentrantLock lock = this.lock;
        //获取锁
        lock.lock();
        try {
        	//数据量为0 直接返回null 
        	//否则出队列 然后唤醒生产者不为空条件队列中的一个 从队头开始
            return (count == 0) ? null : dequeue();
        } finally {
        	//释放锁 
            lock.unlock();
        }
    }
    /**
     * 获取下标为takeIndex数据 可中断的
     */
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        //获取可重入的独占锁
        lock.lockInterruptibly();
        try {
        	//当数据个数为0时
        	//条件不为空则要等待
            while (count == 0)
            	//消费者等待 等待生产者生产数据唤醒
                notEmpty.await();
            //出队列 然后唤醒生产者不为空条件队列中的一个 从队头开始
            return dequeue();
        } finally {
        	//释放可重入的独占锁
            lock.unlock();
        }
    }
    /**
     * 出队列 有超时时间 可中断
     * @param timeout  超时时间
     * @param unit	时间单位
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        //转成纳秒
    	long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        //获取锁
        lock.lockInterruptibly();
        try {
        	//数据量为0
            while (count == 0) {
            	//表示超时了 直接返回null
                if (nanos <= 0)
                    return null;
                //挂起固定的时间nanos 
                //超时返回小于等于0 
                nanos = notEmpty.awaitNanos(nanos);
            }
            //出队列 然后唤醒生产者不为空条件队列中的一个 从队头开始
            return dequeue();
        } finally {
        	//释放锁
            lock.unlock();
        }
    }
    /**
     * 获取队头数据 
     */
    public E peek() {
        final ReentrantLock lock = this.lock;
        //获取锁
        lock.lock();
        try {
        	//返回takeIndex下标的数据
            return itemAt(takeIndex);
        } finally {
        	//释放锁	
            lock.unlock();
        }
    }
    /**
     * 获取数据量
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
     *	返回可用的大小
     *	(容量 - 数据量)
     */
    public int remainingCapacity() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.length - count;
        } finally {
            lock.unlock();
        }
    }

    /**
     *	删除数据o
     * @return true 删除成功 false 删除失败
     */
    public boolean remove(Object o) {
    	//o为null 直接返回false
        if (o == null) return false;
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
        	//如果数据量大于0
            if (count > 0) {
                final int putIndex = this.putIndex;
                int i = takeIndex;
                //从takeIndex一直往后获取当找到
                //等于数组长度时 从下标0开始找 找到takeIndex等putIndex
                //找到相等的对象 就删除对应下标i的数据
                do {
                    if (o.equals(items[i])) {
                        removeAt(i);
                        return true;
                    }
                    if (++i == items.length)
                        i = 0;
                } while (i != putIndex);
            }
            //数据量小于等于0 直接返回false
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     *	队列是否包含对象o
     * @param o 
     * @return true 包含 false 不包含
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
        	//如果数据量大于0
            if (count > 0) {
                final int putIndex = this.putIndex;
                int i = takeIndex;
                //从takeIndex一直往后获取当找到
                //等于数组长度时 从下标0开始找 找到takeIndex等putIndex
                //找到相等的对象 直接返回true
                do {
                    if (o.equals(items[i]))
                        return true;
                    if (++i == items.length)
                        i = 0;
                } while (i != putIndex);
            }
            //数据量小于等于0 直接返回false
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     *	队列转数组
     *	底层就是数组的拷贝
     */
    public Object[] toArray() {
        Object[] a;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final int count = this.count;
            a = new Object[count];
            int n = items.length - takeIndex;
            if (count <= n)
                System.arraycopy(items, takeIndex, a, 0, count);
            else {
                System.arraycopy(items, takeIndex, a, 0, n);
                System.arraycopy(items, 0, a, n, count - n);
            }
        } finally {
            lock.unlock();
        }
        return a;
    }

    /**
     *	队列转换成指定类型的数组
     *	底层就是数组的拷贝
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final int count = this.count;
            final int len = a.length;
            if (len < count)
                a = (T[])java.lang.reflect.Array.newInstance(
                    a.getClass().getComponentType(), count);
            int n = items.length - takeIndex;
            if (count <= n)
                System.arraycopy(items, takeIndex, a, 0, count);
            else {
                System.arraycopy(items, takeIndex, a, 0, n);
                System.arraycopy(items, 0, a, n, count - n);
            }
            if (len > count)
                a[count] = null;
        } finally {
            lock.unlock();
        }
        return a;
    }

    public String toString() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int k = count;
            if (k == 0)
                return "[]";

            final Object[] items = this.items;
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = takeIndex; ; ) {
                Object e = items[i];
                sb.append(e == this ? "(this Collection)" : e);
                if (--k == 0)
                    return sb.append(']').toString();
                sb.append(',').append(' ');
                if (++i == items.length)
                    i = 0;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清空队列
     * 	数组的清空
     */
    public void clear() {
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int k = count;
            if (k > 0) {
                final int putIndex = this.putIndex;
                int i = takeIndex;
                do {
                    items[i] = null;
                    if (++i == items.length)
                        i = 0;
                } while (i != putIndex);
                takeIndex = putIndex;
                count = 0;
                if (itrs != null)
                    itrs.queueIsEmpty();
                for (; k > 0 && lock.hasWaiters(notFull); k--)
                    notFull.signal();
            }
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
        checkNotNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = Math.min(maxElements, count);
            int take = takeIndex;
            int i = 0;
            try {
                while (i < n) {
                    @SuppressWarnings("unchecked")
                    E x = (E) items[take];
                    c.add(x);
                    items[take] = null;
                    if (++take == items.length)
                        take = 0;
                    i++;
                }
                return n;
            } finally {
                // Restore invariants even if c.add() threw
                if (i > 0) {
                    count -= i;
                    takeIndex = take;
                    if (itrs != null) {
                        if (count == 0)
                            itrs.queueIsEmpty();
                        else if (i > take)
                            itrs.takeIndexWrapped();
                    }
                    for (; i > 0 && lock.hasWaiters(notFull); i--)
                        notFull.signal();
                }
            }
        } finally {
            lock.unlock();
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

    /**
     * Shared data between iterators and their queue, allowing queue
     * modifications to update iterators when elements are removed.
     *
     * This adds a lot of complexity for the sake of correctly
     * handling some uncommon operations, but the combination of
     * circular-arrays and supporting interior removes (i.e., those
     * not at head) would cause iterators to sometimes lose their
     * places and/or (re)report elements they shouldn't.  To avoid
     * this, when a queue has one or more iterators, it keeps iterator
     * state consistent by:
     *
     * (1) keeping track of the number of "cycles", that is, the
     *     number of times takeIndex has wrapped around to 0.
     * (2) notifying all iterators via the callback removedAt whenever
     *     an interior element is removed (and thus other elements may
     *     be shifted).
     *
     * These suffice to eliminate iterator inconsistencies, but
     * unfortunately add the secondary responsibility of maintaining
     * the list of iterators.  We track all active iterators in a
     * simple linked list (accessed only when the queue's lock is
     * held) of weak references to Itr.  The list is cleaned up using
     * 3 different mechanisms:
     *
     * (1) Whenever a new iterator is created, do some O(1) checking for
     *     stale list elements.
     *
     * (2) Whenever takeIndex wraps around to 0, check for iterators
     *     that have been unused for more than one wrap-around cycle.
     *
     * (3) Whenever the queue becomes empty, all iterators are notified
     *     and this entire data structure is discarded.
     *
     * So in addition to the removedAt callback that is necessary for
     * correctness, iterators have the shutdown and takeIndexWrapped
     * callbacks that help remove stale iterators from the list.
     *
     * Whenever a list element is examined, it is expunged if either
     * the GC has determined that the iterator is discarded, or if the
     * iterator reports that it is "detached" (does not need any
     * further state updates).  Overhead is maximal when takeIndex
     * never advances, iterators are discarded before they are
     * exhausted, and all removals are interior removes, in which case
     * all stale iterators are discovered by the GC.  But even in this
     * case we don't increase the amortized complexity.
     *
     * Care must be taken to keep list sweeping methods from
     * reentrantly invoking another such method, causing subtle
     * corruption bugs.
     */
    class Itrs {

        /**
         * Node in a linked list of weak iterator references.
         */
        private class Node extends WeakReference<Itr> {
            Node next;

            Node(Itr iterator, Node next) {
                super(iterator);
                this.next = next;
            }
        }

        /** Incremented whenever takeIndex wraps around to 0 */
        int cycles = 0;

        /** Linked list of weak iterator references */
        private Node head;

        /** Used to expunge stale iterators */
        private Node sweeper = null;

        private static final int SHORT_SWEEP_PROBES = 4;
        private static final int LONG_SWEEP_PROBES = 16;

        Itrs(Itr initial) {
            register(initial);
        }

        /**
         * Sweeps itrs, looking for and expunging stale iterators.
         * If at least one was found, tries harder to find more.
         * Called only from iterating thread.
         *
         * @param tryHarder whether to start in try-harder mode, because
         * there is known to be at least one iterator to collect
         */
        void doSomeSweeping(boolean tryHarder) {
            // assert lock.getHoldCount() == 1;
            // assert head != null;
            int probes = tryHarder ? LONG_SWEEP_PROBES : SHORT_SWEEP_PROBES;
            Node o, p;
            final Node sweeper = this.sweeper;
            boolean passedGo;   // to limit search to one full sweep

            if (sweeper == null) {
                o = null;
                p = head;
                passedGo = true;
            } else {
                o = sweeper;
                p = o.next;
                passedGo = false;
            }

            for (; probes > 0; probes--) {
                if (p == null) {
                    if (passedGo)
                        break;
                    o = null;
                    p = head;
                    passedGo = true;
                }
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.isDetached()) {
                    // found a discarded/exhausted iterator
                    probes = LONG_SWEEP_PROBES; // "try harder"
                    // unlink p
                    p.clear();
                    p.next = null;
                    if (o == null) {
                        head = next;
                        if (next == null) {
                            // We've run out of iterators to track; retire
                            itrs = null;
                            return;
                        }
                    }
                    else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }

            this.sweeper = (p == null) ? null : o;
        }

        /**
         * Adds a new iterator to the linked list of tracked iterators.
         */
        void register(Itr itr) {
            // assert lock.getHoldCount() == 1;
            head = new Node(itr, head);
        }

        /**
         * Called whenever takeIndex wraps around to 0.
         *
         * Notifies all iterators, and expunges any that are now stale.
         */
        void takeIndexWrapped() {
            // assert lock.getHoldCount() == 1;
            cycles++;
            for (Node o = null, p = head; p != null;) {
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.takeIndexWrapped()) {
                    // unlink p
                    // assert it == null || it.isDetached();
                    p.clear();
                    p.next = null;
                    if (o == null)
                        head = next;
                    else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }
            if (head == null)   // no more iterators to track
                itrs = null;
        }

        /**
         * Called whenever an interior remove (not at takeIndex) occurred.
         *
         * Notifies all iterators, and expunges any that are now stale.
         */
        void removedAt(int removedIndex) {
            for (Node o = null, p = head; p != null;) {
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.removedAt(removedIndex)) {
                    // unlink p
                    // assert it == null || it.isDetached();
                    p.clear();
                    p.next = null;
                    if (o == null)
                        head = next;
                    else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }
            if (head == null)   // no more iterators to track
                itrs = null;
        }

        /**
         * Called whenever the queue becomes empty.
         *
         * Notifies all active iterators that the queue is empty,
         * clears all weak refs, and unlinks the itrs datastructure.
         */
        void queueIsEmpty() {
            // assert lock.getHoldCount() == 1;
            for (Node p = head; p != null; p = p.next) {
                Itr it = p.get();
                if (it != null) {
                    p.clear();
                    it.shutdown();
                }
            }
            head = null;
            itrs = null;
        }

        /**
         * Called whenever an element has been dequeued (at takeIndex).
         */
        void elementDequeued() {
            // assert lock.getHoldCount() == 1;
            if (count == 0)
                queueIsEmpty();
            else if (takeIndex == 0)
                takeIndexWrapped();
        }
    }

    /**
     * Iterator for ArrayBlockingQueue.
     *
     * To maintain weak consistency with respect to puts and takes, we
     * read ahead one slot, so as to not report hasNext true but then
     * not have an element to return.
     *
     * We switch into "detached" mode (allowing prompt unlinking from
     * itrs without help from the GC) when all indices are negative, or
     * when hasNext returns false for the first time.  This allows the
     * iterator to track concurrent updates completely accurately,
     * except for the corner case of the user calling Iterator.remove()
     * after hasNext() returned false.  Even in this case, we ensure
     * that we don't remove the wrong element by keeping track of the
     * expected element to remove, in lastItem.  Yes, we may fail to
     * remove lastItem from the queue if it moved due to an interleaved
     * interior remove while in detached mode.
     */
    private class Itr implements Iterator<E> {
        /** Index to look for new nextItem; NONE at end */
        private int cursor;

        /** Element to be returned by next call to next(); null if none */
        private E nextItem;

        /** Index of nextItem; NONE if none, REMOVED if removed elsewhere */
        private int nextIndex;

        /** Last element returned; null if none or not detached. */
        private E lastItem;

        /** Index of lastItem, NONE if none, REMOVED if removed elsewhere */
        private int lastRet;

        /** Previous value of takeIndex, or DETACHED when detached */
        private int prevTakeIndex;

        /** Previous value of iters.cycles */
        private int prevCycles;

        /** Special index value indicating "not available" or "undefined" */
        private static final int NONE = -1;

        /**
         * Special index value indicating "removed elsewhere", that is,
         * removed by some operation other than a call to this.remove().
         */
        private static final int REMOVED = -2;

        /** Special value for prevTakeIndex indicating "detached mode" */
        private static final int DETACHED = -3;

        Itr() {
            // assert lock.getHoldCount() == 0;
            lastRet = NONE;
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                if (count == 0) {
                    // assert itrs == null;
                    cursor = NONE;
                    nextIndex = NONE;
                    prevTakeIndex = DETACHED;
                } else {
                    final int takeIndex = ArrayBlockingQueue.this.takeIndex;
                    prevTakeIndex = takeIndex;
                    nextItem = itemAt(nextIndex = takeIndex);
                    cursor = incCursor(takeIndex);
                    if (itrs == null) {
                        itrs = new Itrs(this);
                    } else {
                        itrs.register(this); // in this order
                        itrs.doSomeSweeping(false);
                    }
                    prevCycles = itrs.cycles;
                    // assert takeIndex >= 0;
                    // assert prevTakeIndex == takeIndex;
                    // assert nextIndex >= 0;
                    // assert nextItem != null;
                }
            } finally {
                lock.unlock();
            }
        }

        boolean isDetached() {
            // assert lock.getHoldCount() == 1;
            return prevTakeIndex < 0;
        }

        private int incCursor(int index) {
            // assert lock.getHoldCount() == 1;
            if (++index == items.length)
                index = 0;
            if (index == putIndex)
                index = NONE;
            return index;
        }

        /**
         * Returns true if index is invalidated by the given number of
         * dequeues, starting from prevTakeIndex.
         */
        private boolean invalidated(int index, int prevTakeIndex,
                                    long dequeues, int length) {
            if (index < 0)
                return false;
            int distance = index - prevTakeIndex;
            if (distance < 0)
                distance += length;
            return dequeues > distance;
        }

        /**
         * Adjusts indices to incorporate all dequeues since the last
         * operation on this iterator.  Call only from iterating thread.
         */
        private void incorporateDequeues() {
            // assert lock.getHoldCount() == 1;
            // assert itrs != null;
            // assert !isDetached();
            // assert count > 0;

            final int cycles = itrs.cycles;
            final int takeIndex = ArrayBlockingQueue.this.takeIndex;
            final int prevCycles = this.prevCycles;
            final int prevTakeIndex = this.prevTakeIndex;

            if (cycles != prevCycles || takeIndex != prevTakeIndex) {
                final int len = items.length;
                // how far takeIndex has advanced since the previous
                // operation of this iterator
                long dequeues = (cycles - prevCycles) * len
                    + (takeIndex - prevTakeIndex);

                // Check indices for invalidation
                if (invalidated(lastRet, prevTakeIndex, dequeues, len))
                    lastRet = REMOVED;
                if (invalidated(nextIndex, prevTakeIndex, dequeues, len))
                    nextIndex = REMOVED;
                if (invalidated(cursor, prevTakeIndex, dequeues, len))
                    cursor = takeIndex;

                if (cursor < 0 && nextIndex < 0 && lastRet < 0)
                    detach();
                else {
                    this.prevCycles = cycles;
                    this.prevTakeIndex = takeIndex;
                }
            }
        }

        /**
         * Called when itrs should stop tracking this iterator, either
         * because there are no more indices to update (cursor < 0 &&
         * nextIndex < 0 && lastRet < 0) or as a special exception, when
         * lastRet >= 0, because hasNext() is about to return false for the
         * first time.  Call only from iterating thread.
         */
        private void detach() {
            // Switch to detached mode
            // assert lock.getHoldCount() == 1;
            // assert cursor == NONE;
            // assert nextIndex < 0;
            // assert lastRet < 0 || nextItem == null;
            // assert lastRet < 0 ^ lastItem != null;
            if (prevTakeIndex >= 0) {
                // assert itrs != null;
                prevTakeIndex = DETACHED;
                // try to unlink from itrs (but not too hard)
                itrs.doSomeSweeping(true);
            }
        }

        /**
         * For performance reasons, we would like not to acquire a lock in
         * hasNext in the common case.  To allow for this, we only access
         * fields (i.e. nextItem) that are not modified by update operations
         * triggered by queue modifications.
         */
        public boolean hasNext() {
            // assert lock.getHoldCount() == 0;
            if (nextItem != null)
                return true;
            noNext();
            return false;
        }

        private void noNext() {
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                // assert cursor == NONE;
                // assert nextIndex == NONE;
                if (!isDetached()) {
                    // assert lastRet >= 0;
                    incorporateDequeues(); // might update lastRet
                    if (lastRet >= 0) {
                        lastItem = itemAt(lastRet);
                        // assert lastItem != null;
                        detach();
                    }
                }
                // assert isDetached();
                // assert lastRet < 0 ^ lastItem != null;
            } finally {
                lock.unlock();
            }
        }

        public E next() {
            // assert lock.getHoldCount() == 0;
            final E x = nextItem;
            if (x == null)
                throw new NoSuchElementException();
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                if (!isDetached())
                    incorporateDequeues();
                // assert nextIndex != NONE;
                // assert lastItem == null;
                lastRet = nextIndex;
                final int cursor = this.cursor;
                if (cursor >= 0) {
                    nextItem = itemAt(nextIndex = cursor);
                    // assert nextItem != null;
                    this.cursor = incCursor(cursor);
                } else {
                    nextIndex = NONE;
                    nextItem = null;
                }
            } finally {
                lock.unlock();
            }
            return x;
        }

        public void remove() {
            // assert lock.getHoldCount() == 0;
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                if (!isDetached())
                    incorporateDequeues(); // might update lastRet or detach
                final int lastRet = this.lastRet;
                this.lastRet = NONE;
                if (lastRet >= 0) {
                    if (!isDetached())
                        removeAt(lastRet);
                    else {
                        final E lastItem = this.lastItem;
                        // assert lastItem != null;
                        this.lastItem = null;
                        if (itemAt(lastRet) == lastItem)
                            removeAt(lastRet);
                    }
                } else if (lastRet == NONE)
                    throw new IllegalStateException();
                // else lastRet == REMOVED and the last returned element was
                // previously asynchronously removed via an operation other
                // than this.remove(), so nothing to do.

                if (cursor < 0 && nextIndex < 0)
                    detach();
            } finally {
                lock.unlock();
                // assert lastRet == NONE;
                // assert lastItem == null;
            }
        }

        /**
         * Called to notify the iterator that the queue is empty, or that it
         * has fallen hopelessly behind, so that it should abandon any
         * further iteration, except possibly to return one more element
         * from next(), as promised by returning true from hasNext().
         */
        void shutdown() {
            // assert lock.getHoldCount() == 1;
            cursor = NONE;
            if (nextIndex >= 0)
                nextIndex = REMOVED;
            if (lastRet >= 0) {
                lastRet = REMOVED;
                lastItem = null;
            }
            prevTakeIndex = DETACHED;
            // Don't set nextItem to null because we must continue to be
            // able to return it on next().
            //
            // Caller will unlink from itrs when convenient.
        }

        private int distance(int index, int prevTakeIndex, int length) {
            int distance = index - prevTakeIndex;
            if (distance < 0)
                distance += length;
            return distance;
        }

        /**
         * Called whenever an interior remove (not at takeIndex) occurred.
         *
         * @return true if this iterator should be unlinked from itrs
         */
        boolean removedAt(int removedIndex) {
            // assert lock.getHoldCount() == 1;
            if (isDetached())
                return true;

            final int cycles = itrs.cycles;
            final int takeIndex = ArrayBlockingQueue.this.takeIndex;
            final int prevCycles = this.prevCycles;
            final int prevTakeIndex = this.prevTakeIndex;
            final int len = items.length;
            int cycleDiff = cycles - prevCycles;
            if (removedIndex < takeIndex)
                cycleDiff++;
            final int removedDistance =
                (cycleDiff * len) + (removedIndex - prevTakeIndex);
            // assert removedDistance >= 0;
            int cursor = this.cursor;
            if (cursor >= 0) {
                int x = distance(cursor, prevTakeIndex, len);
                if (x == removedDistance) {
                    if (cursor == putIndex)
                        this.cursor = cursor = NONE;
                }
                else if (x > removedDistance) {
                    // assert cursor != prevTakeIndex;
                    this.cursor = cursor = dec(cursor);
                }
            }
            int lastRet = this.lastRet;
            if (lastRet >= 0) {
                int x = distance(lastRet, prevTakeIndex, len);
                if (x == removedDistance)
                    this.lastRet = lastRet = REMOVED;
                else if (x > removedDistance)
                    this.lastRet = lastRet = dec(lastRet);
            }
            int nextIndex = this.nextIndex;
            if (nextIndex >= 0) {
                int x = distance(nextIndex, prevTakeIndex, len);
                if (x == removedDistance)
                    this.nextIndex = nextIndex = REMOVED;
                else if (x > removedDistance)
                    this.nextIndex = nextIndex = dec(nextIndex);
            }
            else if (cursor < 0 && nextIndex < 0 && lastRet < 0) {
                this.prevTakeIndex = DETACHED;
                return true;
            }
            return false;
        }

        /**
         * Called whenever takeIndex wraps around to zero.
         *
         * @return true if this iterator should be unlinked from itrs
         */
        boolean takeIndexWrapped() {
            // assert lock.getHoldCount() == 1;
            if (isDetached())
                return true;
            if (itrs.cycles - prevCycles > 1) {
                // All the elements that existed at the time of the last
                // operation are gone, so abandon further iteration.
                shutdown();
                return true;
            }
            return false;
        }

//         /** Uncomment for debugging. */
//         public String toString() {
//             return ("cursor=" + cursor + " " +
//                     "nextIndex=" + nextIndex + " " +
//                     "lastRet=" + lastRet + " " +
//                     "nextItem=" + nextItem + " " +
//                     "lastItem=" + lastItem + " " +
//                     "prevCycles=" + prevCycles + " " +
//                     "prevTakeIndex=" + prevTakeIndex + " " +
//                     "size()=" + size() + " " +
//                     "remainingCapacity()=" + remainingCapacity());
//         }
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
        return Spliterators.spliterator
            (this, Spliterator.ORDERED | Spliterator.NONNULL |
             Spliterator.CONCURRENT);
    }

    /**
     * Deserializes this queue and then checks some invariants.
     *
     * @param s the input stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.InvalidObjectException if invariants are violated
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {

        // Read in items array and various fields
        s.defaultReadObject();

        // Check invariants over count and index fields. Note that
        // if putIndex==takeIndex, count can be either 0 or items.length.
        if (items.length == 0 ||
            takeIndex < 0 || takeIndex >= items.length ||
            putIndex  < 0 || putIndex  >= items.length ||
            count < 0     || count     >  items.length ||
            Math.floorMod(putIndex - takeIndex, items.length) !=
            Math.floorMod(count, items.length)) {
            throw new java.io.InvalidObjectException("invariants violated");
        }
    }
}
