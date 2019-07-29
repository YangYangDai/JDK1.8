package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import sun.misc.Unsafe;

/**
 * 	抽象的队列同步器
 */
public abstract class AbstractQueuedSynchronizer
    extends AbstractOwnableSynchronizer
    implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() { }

    /**
     *      +------+  prev +-----+       +-----+
     * head |      | <---- |     | <---- |     |  tail
     *      +------+       +-----+       +-----+
     * 线程同步队列的节点 
     * 节点存储线程
     * 通过Node我们可以实现两个队列
     * 一是通过prev和next实现CLH队列(线程同步队列,双向队列)
     * 二是nextWaiter实现Condition条件上的等待线程队列(单向队列)
     */
    static final class Node {
        /**
         * 标志是在共享模式下
         */
        static final Node SHARED = new Node();
        /**
         * 标志是在独占模式下
         */
        static final Node EXCLUSIVE = null;
        /**
         * 取消状态：半天抢不到锁不抢了或者指定时间内没有获取到锁
         */
        static final int CANCELLED =  1;
        /**
         * 表示当前节点的的后继节点将要或者已经被阻塞
         * 在当前节点被释放的时候要唤醒后继节点中的线程
         */
        static final int SIGNAL    = -1;
        /**
         * 表示当前节点在等待condition，即在condition队列中
         */
        static final int CONDITION = -2;
        /**
         * 表示releaseShared需要被传播给后续节点（仅在共享模式下使用）
         */
        static final int PROPAGATE = -3;
        /**
         * 节点的状态值 默认为0
         * volatile 保证可见性
         */
        volatile int waitStatus;

        /**
         * 指向前驱
         */
        volatile Node prev;

        /**
         * 指向后继
         */
        volatile Node next;

        /**
         * 节点中的线程
         */
        volatile Thread thread;

        /**
         * 存储在condition队列中的后继节点
         * 1. 表示下一个在Condition条件上等待的节点
         * 2. 表示是共享模式或者独占模式，注意第一种情况节点一定是共享模式
         * 如果是SHARED，表示当前节点是共享模式，如果是null，当前节点是独占模式
         * 如果是其他值，当前节点也是独占模式，不过这个值也是Condition队列的下一个节点
         */
        Node nextWaiter;

        /**
         * 是不是共享锁
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 获取prev节点
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {    
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // 用在Condition中
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * 线程同步队列的头指针  当前持有锁的线程
     */
    private transient volatile Node head;

    /**
     * 线程同步队列的尾指针
     */
    private transient volatile Node tail;

    /**
     * volatile 保证可见性
     * 独占模式下表示   重入次数
     * 共享模式下表示  可用的锁资源数
     */
    private volatile int state;

    /**
     * 获取state
     */
    protected final int getState() {
        return state;
    }

    /**
     * 设置state
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     *	 通过cas设置state的值
     *	true 成功 false 失败
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities

    /**
     *	挂起线程的超时纳秒时间
     */
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * 	死循保证节点插入到线程的阻塞队列中去
     * 	@return 返回node的前驱节点
     */
    private Node enq(final Node node) {
        for (;;) {//死循环
            Node t = tail;
            //尾节点为空 线程同步队列还没有数据
            if (t == null) { // 必须初始化
                if (compareAndSetHead(new Node()))//cas设置头节点 注意这里new了一个新的node
                    tail = head;//头尾指针都指向新new的node
            } else {//一定会执行这步
            	//尾部插入node
                node.prev = t;
                if (compareAndSetTail(t, node)) {//cas设置尾节点
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * 	添加节点到线程阻塞队列中
     * 	如果线程阻塞队列不为空的话   尝试直接插入队尾
     * 	阻塞队列为空或者直接插入队尾失败了
     * 	死循环保证插入节点到阻塞队列
     * @param mode 独占或共享
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        // 如果线程同步队列不为空的话   尝试直接插入队尾
        Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            //cas设置尾节点 成功就直接返回
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        //线程阻塞队列为空或者直接插入队尾失败了
        //死循环保证插入成功
        enq(node);
        return node;
    }

    /**
     * 重新设置队列头head，它只在acquire系列的方法中调用
     */
    private void setHead(Node node) {
        head = node;
        //线程也没有意义了，因为该线程已经获取到锁了
        node.thread = null;
        // 前一个节点已经没有意义了
        node.prev = null;
    }

    /**
     * 	唤起存在的后继节点
     */
    private void unparkSuccessor(Node node) {
        //获取node的状态
        int ws = node.waitStatus;
        if (ws < 0)//如果node的waitStatus小于0
            compareAndSetWaitStatus(node, ws, 0);//cas设置node的WaitStatus为0
        
        Node s = node.next;//获取后继节点
        //如果后继节点为null或者当前节点被取消 waitStatus只有为CANCELLED的时候才会大于1
        if (s == null || s.waitStatus > 0) {
            s = null;
            //遍历从队尾到node之间的节点
            for (Node t = tail; t != null && t != node; t = t.prev)
                //找到在node之后的第一个非取消状态的节点
            	if (t.waitStatus <= 0)
                    s = t;
        }
        //如果s不为空 唤醒s节点的线程
        if (s != null)
            LockSupport.unpark(s.thread);
    }

    /**
     * 	唤醒等待共享锁的线程
     */
    private void doReleaseShared() {
        for (;;) {//死循环
            Node h = head;//h指向头节点
            //阻塞队列中至少有两个节点
            if (h != null && h != tail) {
                int ws = h.waitStatus;//获取h的状态
                //如果状态为SIGNAL 唤醒后续节点
                if (ws == Node.SIGNAL) {
                	//cas设置头节点的WaitStatus为0失败
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            
                    unparkSuccessor(h);//唤起h的后继节点
                }
                //如果状态值为0且cas设置状态值为PROPAGATE失败
                else if (ws == 0 &&
                         !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            if (h == head)                   // loop if head changed
                break;
        }
    }

    /**
     * 	把node设置同步队列的头节点
     * 	并向后传播自己获取锁成功的信息
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; //指向老的头节点
        setHead(node);
        /**
         * propagate大于0
         */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }

    // Utilities for various versions of acquire

    /**
     * 取消请求的节点
     * 设置node的waitStatus为取消状态
     */
    private void cancelAcquire(Node node) {
        // node不存在直接返回
        if (node == null)
            return;

        node.thread = null;

        // 清除取消状态的前驱
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        Node predNext = pred.next;
        node.waitStatus = Node.CANCELLED;

        // 如果node是尾部  则设置尾部为node的前驱
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            int ws;
            if (pred != head &&
                ((ws = pred.waitStatus) == Node.SIGNAL ||
                 (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                pred.thread != null) {
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    compareAndSetNext(pred, predNext, next);
            } else {
            	//唤起存在的后继节点
                unparkSuccessor(node);
            }

            node.next = node; //利于gc
        }
    }

    /**
     * 	判断node是不是需要被挂起
     * @param pred node的前驱
     * @param node 当前节点
     * @return {@code true} if thread should block
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;//获取前驱的状态
        //表示前驱的后继节点将要或者已经被阻塞，在前驱释放的时候需要unpark后继节点
        if (ws == Node.SIGNAL)
            return true;//所以node可以被挂起
        if (ws > 0) {//说明前驱已经处于取消状态
        	//向前查找不为取消状态的节点并把node的prev和pred指向不为取消状态的节点
        	//顺便也剔除了取消状态的节点
        	//而且当前节点后续的唤醒操作还得依赖前驱
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;//pred.next ——> node
        } else {
        	/**
        	 * 前驱节点的waitStatus不等于-1和1，那也就是只可能是0，-2，-3
        	 * waitStatu默认为0
        	 * 设置前驱的节点状态为SIGNAL
        	 */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;//node不可以被挂起
    }

    /**
     * 	设置当前线程中断的标志位
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     *	 挂起当前线程(等待被唤醒)
     *	 并返回线程的中断状态
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();//返回当前的线程中断位标志，并重置为false
    }

    /*
     * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much.
     */

    /**
     * 返回线程被中断的状态
     * 如果节点前驱是头节点 就再尝试获取一下锁 
     * 否则的话就挂起当前线程
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {//死循环
            	//获取节点的前驱
                final Node p = node.predecessor();
                //如果节点前驱是头节点 就再尝试获取一下锁 成功的话
                if (p == head && tryAcquire(arg)) {
                	//设置当前节点为头节点
                    setHead(node);
                    p.next = null; //利于gc
                    failed = false;
                    return interrupted;
                }
                //判断node是不是需要被挂起且挂起当前线程并返回线程的中断状态
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)//如果是被中断的
                cancelAcquire(node);
        }
    }

    /**
     * 	获取独占锁  可中断的
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
        throws InterruptedException {
    	//添加独占模式的节点到线程阻塞队列中
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {//死循环
            	//获取node的前驱
                final Node p = node.predecessor();
                //如果前驱是头节点 万一头节点刚执行完了 
                //尝试直接获取锁
                if (p == head && tryAcquire(arg)) {
                	//获取成功的直接设置node为头节点
                    setHead(node);
                    p.next = null; //利于gc
                    failed = false;
                    return;
                }
                //node是不是需要被挂起且挂起当前线程(等待被唤醒) 
                //在parkAndCheckInterrupt()中线程被标志位中断了 直接抛出异常 中断
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                	//直接抛出异常 中断
                    throw new InterruptedException();
            }
        } finally {
        	//异常情况 
            if (failed)
            	//取消请求的节点
                cancelAcquire(node);
        }
    }

    /**
     * 	获取独占锁  有超时时间的 可中断的
     *
     * @param arg 
     * @param nanosTimeout 最大的等待时间 单位纳秒
     * @return true 获取锁成功 false 获取锁失败
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
    	//等待时间<=0 直接返回false
        if (nanosTimeout <= 0L)
            return false;
        //获取锁超时的时间点
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {//死循环
                final Node p = node.predecessor();
                //如果前驱是头节点直接尝试获取锁
                if (p == head && tryAcquire(arg)) {
                	//成功的话 直接设置node为头节点
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                nanosTimeout = deadline - System.nanoTime();
                //nanosTimeout小于等于0 说明已经过了锁超时的时间点 直接返回false
                if (nanosTimeout <= 0L)
                    return false;
                //node是不是需要被挂起且挂起当前线程(等待被唤醒) 
                //且此时还有大于1000纳秒的超时时间
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                	//直接挂起线程指定的时间nanosTimeout
                    LockSupport.parkNanos(this, nanosTimeout);
                //线程是否被标记为中断的 是的话直接抛出异常
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
        	//异常情况 
            if (failed)
            	//取消请求的节点
                cancelAcquire(node);
        }
    }

    /**
     *	 获取共享锁不可中断
     * @param arg 
     */
    private void doAcquireShared(int arg) {
    	//添加共享模式的节点到线程阻塞队列中
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
            	//获取当前节点的前驱
                final Node p = node.predecessor();
                //前驱是头节点
                if (p == head) {
                	//尝试获取共享锁 子类实现
                    int r = tryAcquireShared(arg);
                    //大于等于0表示获取成功
                    if (r >= 0) {
                    	//把node设置同步队列的头节点 并把当前可用的资源向后传播
                        setHeadAndPropagate(node, r);
                        p.next = null; // 利于gc
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                //node是不是需要被挂起且挂起当前线程(等待被唤醒) 
                //在parkAndCheckInterrupt()中线程被标志位中断了 并且重新设置为false了
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
        	//异常情况 
            if (failed)
            	//取消请求的节点
                cancelAcquire(node);
        }
    }

    /**
     * 	获取共享锁 可中断的
     */
    private void doAcquireSharedInterruptibly(int arg)
        throws InterruptedException {
    	//添加共享模式的节点到线程阻塞队列中
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {//死循环
            	//获取node的前驱节点
                final Node p = node.predecessor();
                if (p == head) {//前驱如果是头节点
                	//尝试获取共享锁
                    int r = tryAcquireShared(arg);
                    //获取锁成功
                    if (r >= 0) {//大于等于0
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                //node需要被挂起且node中的线程挂起成功且线程没有被标志位中断的
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
        	//异常情况 
            if (failed)
            	//取消请求的节点
                cancelAcquire(node);
        }
    }

    /**
     *	最大等待时间内获取共享锁  
     * @param arg 请求资源的数量
     * @param nanosTimeout 最大等待的纳秒数
     * @return  获取成功 true 否者false
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        //死亡线  超过这个时间直接返回false
        final long deadline = System.nanoTime() + nanosTimeout;
        //添加共享节点到线程阻塞队列中
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {//死循环
            	//获取节点的前驱
                final Node p = node.predecessor();
                //如果节点前驱为头节点
                if (p == head) {
                	//尝试获取共享锁
                    int r = tryAcquireShared(arg);
                    //获取成功
                    if (r >= 0) {
                    	//将自己设置为头节点
                        setHeadAndPropagate(node, r);
                        p.next = null; // 利于gc
                        failed = false;
                        return true;
                    }
                }
                //获取剩余的可等待时间
                nanosTimeout = deadline - System.nanoTime();
                //没有剩余的可等待时间了 直接返回false
                if (nanosTimeout <= 0L)
                    return false;
                //判断node是不是需要被挂起 
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                //线程是否被标记为中断的 是的话直接抛出异常
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
        	//异常情况 
            if (failed)
            	//取消请求的节点
                cancelAcquire(node);
        }
    }


    /**
     * 	尝试获取独占锁
     * 	具体策略由子类实现
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 	尝试释放独占锁
     * 	具体策略由子类实现
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 	尝试获取共享锁
     * 	具体策略由子类实现
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 	尝试获取共享锁
     * 	具体策略由子类实现
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 	判断当前线程是不是独占线程
     * 	由子类实现
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     *	获取独占锁   
     *	tryAcquire 留给需要的子类实现  模板方法模式
     * @param arg 需要的资源数量
     */
    public final void acquire(int arg) {
    	//tryAcquire	尝试获取失败 
    	//addWaiter	添加独占模式的节点到线程同步队列
    	//acquireQueued 尝试获取 失败就挂起等待前驱唤醒
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

    /**
     * 	 获取独占锁    	可中断的
     *	tryAcquire 留给需要的子类实现  模板方法模式
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            doAcquireInterruptibly(arg);
    }

    /**
     *	获取独占锁  有超时时间的 可中断的
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquire(arg) ||
            doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 	释放独占锁
     */
    public final boolean release(int arg) {
    	//尝试释放独占锁 留给需要的子类实现
        if (tryRelease(arg)) {
        	//独占锁释放成功 唤醒后继节点
            Node h = head;
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    /**
     *	获取共享锁不可中断
     * @param 
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }

    /**
     * 	获取共享锁  可中断
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        //尝试获取共享锁 子类实现小于0 则加入线程同步队列中去
        if (tryAcquireShared(arg) < 0)
            doAcquireSharedInterruptibly(arg);
    }

    /**
     *	一定时间获取共享锁  可中断
     * @param arg 
     * @param nanosTimeout 等待的最大纳秒数
     * @return 获取成功 true  超时或获取失败false
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
            doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * 	尝试释放共享锁
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {//尝试释放共享锁成功
            //唤醒后继阻塞节点
        	doReleaseShared();
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     *	阻塞队列中还有没有阻塞线程
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is if an acquire method has ever blocked.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there has ever been contention
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * Returns the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued.
     *
     * <p>In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     *         {@code null} if no threads are currently queued
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null) ||
            ((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null))
            return st;

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * 	判断线程是否在阻塞队列中
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    /**
     * Returns {@code true} if the apparent first queued thread, if one
     * exists, is waiting in exclusive mode.  If this method returns
     * {@code true}, and the current thread is attempting to acquire in
     * shared mode (that is, this method is invoked from {@link
     * #tryAcquireShared}) then it is guaranteed that the current thread
     * is not the first queued thread.  Used only as a heuristic in
     * ReentrantReadWriteLock.
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null &&
            (s = h.next)  != null &&
            !s.isShared()         &&
            s.thread != null;
    }

    /**
     * 	判断阻塞队列里有没有阻塞节点
     */
    public final boolean hasQueuedPredecessors() {
        Node t = tail; 
        Node h = head;
        Node s;
        return h != t &&
            ((s = h.next) == null || s.thread != Thread.currentThread());
    }


    // Instrumentation and monitoring methods

    /**
     *	获取阻塞队列的长度 不包含线程为空的节点  头节点线程为null
     */
    public final int getQueueLength() {
        int n = 0;
        //从尾结点向前遍历
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     *	获取阻塞队列中的阻塞线程 以集合的形式返回
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        //从尾结点向前遍历
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     *	获取独占阻塞队列中的阻塞线程 以集合的形式返回
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     *	获取共享阻塞队列中的阻塞线程 以集合的形式返回
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        int s = getState();
        String q  = hasQueuedThreads() ? "non" : "";
        return super.toString() +
            "[State = " + s + ", " + q + "empty queue]";
    }


    // Internal support methods for Conditions

    /**
     * 判断当前节点是否在阻塞队列中
     */
    final boolean isOnSyncQueue(Node node) {
    	//节点的waitStatus为CONDITION或者节点的前驱为null 说明不在阻塞队列中
    	//条件队列中的节点转移到阻塞队列中的时候waitStatus是为0的 
    	//waitStatus为CONDITION 肯定表示节点还在条件队列中
    	//节点的前驱为null 条件队列中的节点前驱肯定为null 因为根本没有使用到
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        //如果节点的后继不为空说明在阻塞队列中 因为条件队列中的节点后继肯定是null
        if (node.next != null) 
            return true;
        return findNodeFromTail(node);
    }

    /**
     * 直接从阻塞队列的尾部开始查找 找到就直接返回true
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    /**
     * 	将节点从条件队列转移到阻塞队列中
     	* 转移成功返回true 失败说明节点的状态值不是CONDITION
     */
    final boolean transferForSignal(Node node) {
    	//cas把node节点的waitStatus从CONDITION设置成0 
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
        	//设置失败直接返回false
            return false;
        //死循保证节点插入到线程的阻塞队列中去 并得到node的前驱节点
        Node p = enq(node);
        //获取前驱的waitStatus
        int ws = p.waitStatus;
        //前驱节点被取消或者cas设置设置waitStatus的值为SIGNAL失败  直接唤起当前节点中的线程
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
        	//唤醒node节点中的线程
            LockSupport.unpark(node.thread);
        return true;
    }

    /**
     * 	判断线程中断的位置
     */
    final boolean transferAfterCancelledWait(Node node) {
    	//cas设置节点的WaitStatus如果设置成功说明当前节点的WaitStatus为CONDITION 说明是在signal方法之前发生的中断
    	//不然的话肯定会设置失败  因为signal阶段会设置WaitStatus为0
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
        	//将已经被中断线程的节点放入阻塞队列
        	enq(node);
            return true;
        }
        //signal 调用之后，没完成转移之前，发生了中断
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }

    /**
     * 完全释放独占锁 因为可能重入了几次
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
        	//获取到重入次数
            int savedState = getState();
            //完全释放独占锁 将state设置为0
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed)//失败就把node的waitStatus设置为CANCELLED
                node.waitStatus = Node.CANCELLED;
        }
    }

    // Instrumentation methods for conditions

    /**
     * 	判断condition是属于当前同步器的
     * 	true 属于 false 不属于
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     *	判断条件队列中是否还有阻塞线程
     *
     */
    public final boolean hasWaiters(ConditionObject condition) {
    	//condition不是属于当前同步器的 直接抛出异常
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        //判断条件队列中是否还有阻塞线程
        return condition.hasWaiters();
    }

    /**
     * 	获取条件队列中的阻塞线程个数
     */
    public final int getWaitQueueLength(ConditionObject condition) {
    	//condition不是属于当前同步器的 直接抛出异常
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        //获取条件队列中的阻塞线程个数
        return condition.getWaitQueueLength();
    }

    /**
     *	获取条件队列中的阻塞线程 以集合的形式返回
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
    	//condition不是属于当前同步器的 直接抛出异常
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /**
         * 	条件队列的头节点
         */
        private transient Node firstWaiter;
        /**
         *	 条件队列的首节点
         */
        private transient Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() { }

        // Internal methods

        /**
         * 	添加节点到某个condition的条件队列中
         */
        private Node addConditionWaiter() {
            Node t = lastWaiter;
            // 如果t不为null且t的waitStatus值不等于CONDITION 
            // 等待队列中waitStatus的值只可能是CONDITION或CANCELLED
            if (t != null && t.waitStatus != Node.CONDITION) {
                //从条件队列的头节点遍历 清除状态为取消状态的节点
            	unlinkCancelledWaiters();
                t = lastWaiter;
            }
            //新建条件队列的node waitStatus=CONDITION
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }

        /**
         * 	唤醒条件队列中的节点
         */
        private void doSignal(Node first) {
        	//如果first转移到阻塞队列失败 那就从first后面第一个节点转移
            do {
            	//条件队列中头节点的下个节点为null 
                if ((firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;//把lastWaiter指向null
                first.nextWaiter = null;//利于gc
            } while (!transferForSignal(first) &&
                     (first = firstWaiter) != null);
        }

        /**
         * 	删除和转移所有节点到阻塞线程上去
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
        	//置空条件队列中的头尾节点
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;//置空nextWaiter
                //转移节点到阻塞队列中去
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * 	从条件队列的头节点遍历 清除状态为取消状态的节点
         */
        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    if (next == null)
                        lastWaiter = trail;
                }
                else
                    trail = t;
                t = next;
            }
        }

        // public methods

        /**
         * 唤醒条件队列中等待时间最久的线程
         */
        public final void signal() {
        	//当前线程如果不是独占锁的线程 直接抛出异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            //头节点不为空的话 唤醒头节点
            if (first != null)
                doSignal(first);
        }

        /**
         *	唤醒所有的条件队列中的阻塞线程
         */
        public final void signalAll() {
        	//如果是独占锁 抛出异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted())
                    interrupted = true;
            }
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /**
         * 重新中断当前线程，因为它代表 await() 期间没有被中断，而是 signal() 以后发生的中断
         */
        private static final int REINTERRUPT =  1;
        /**
         * 代表在 await() 期间发生了中断
         */
        private static final int THROW_IE    = -1;

        /**
         * 没有中断 0
         * signal之前中断 THROW_IE
         * signal之后中断 REINTERRUPT
         */
        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ?
                (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                0;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode)
            throws InterruptedException {
        	//代表在 await() 期间发生了中断
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            //重新中断当前线程，因为它代表 await() 期间没有被中断，而是 signal() 以后发生的中断
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        /**
         * 可中断条件await 调用这个方法说明当前node已经持有锁了
         */
        public final void await() throws InterruptedException {
            //线程被标记为中断的就直接中断掉
        	if (Thread.interrupted())
                throw new InterruptedException();
        	//添加节点到condition的条件队列中
            Node node = addConditionWaiter();
            //释放当前节点持有的锁
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            //node还没有转移到阻塞队列中或者线程被中断都会终止循环
            while (!isOnSyncQueue(node)) {
            	//不在阻塞队列中 挂起当前线程 当线程被唤醒或者是park的时候被中断了
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            //在阻塞队列中了  尝试获取独占锁并设置成之前自己的重入次数 
            //node.nextWaiter != null 说明是在signal之前中断的
            //node.nextWaiter == null 说明是在signal之后中断的
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            //节点的nextWaiter不为null 需要清空取消状态的节点
            if (node.nextWaiter != null)
            	//从条件队列的头节点遍历 清除状态为取消状态的节点
                unlinkCancelledWaiters();
            //等于0说明没有发生中断 不等于0则说明发现了中断
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         *  	挂起线程一段时间
         *  	@return 返回负数 说明超时了 返回正数说明没有超时
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                	//指定线程的挂起时间
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return deadline - System.nanoTime();
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         *	判断条件队列中是否还有节点
         *	true 表示还有 false 表示没有
         */
        protected final boolean hasWaiters() {
        	//当前线程如果是独占线程 直接抛出异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            //遍历当前的条件队列 
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
            	//只要有一个节点的waitStatus是CONDITION 直接返回true
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         *	获取条件队列中的节点个数
         */
        protected final int getWaitQueueLength() {
        	//当前线程如果是独占线程 直接抛出异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            //遍历当前的条件队列 
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
            	//节点的waitStatus是CONDITION
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         *	 获取条件队列中的阻塞线程 以集合的形式返回
         */
        protected final Collection<Thread> getWaitingThreads() {
        	//当前线程如果是独占线程 直接抛出异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
            	//节点的waitStatus是CONDITION
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    //而且线程不能为null
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("next"));

        } catch (Exception ex) { throw new Error(ex); }
    }

    /**
     * cas设置头节点 仅在enq中使用
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * cas设置尾节点 仅在enq中使用
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * cas设置node的WaitStatus
     */
    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                                        expect, update);
    }

    /**
     * cas设置node
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
