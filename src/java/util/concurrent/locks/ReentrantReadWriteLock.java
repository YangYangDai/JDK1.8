package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.Collection;

/**
 *	从名字来看是：可重入的读写锁
 */
public class ReentrantReadWriteLock
        implements ReadWriteLock, java.io.Serializable {
    private static final long serialVersionUID = -6992448646407690164L;
    /**
     *	内部的读锁
     */
    private final ReentrantReadWriteLock.ReadLock readerLock;
    /**
     * 	内部的写锁
     */
    private final ReentrantReadWriteLock.WriteLock writerLock;
    /**
     * 	同步器
     */
    final Sync sync;

    /**
     * 	默认使用的是非公平的锁
     */
    public ReentrantReadWriteLock() {
        this(false);
    }

    /**
     *	
     * @param fair true 使用公平锁 使用费公平锁
     */
    public ReentrantReadWriteLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
        //初始化读锁
        readerLock = new ReadLock(this);
        //初始化写锁
        writerLock = new WriteLock(this);
    }
    //获取写锁
    public ReentrantReadWriteLock.WriteLock writeLock() { return writerLock; }
    //获取读锁
    public ReentrantReadWriteLock.ReadLock  readLock()  { return readerLock; }

    /**
     *	抽象的同步器
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 6317671515068378041L;

        /**
         *	高16位表示读锁获取的次数 包括重入次数
         *	低16位表示独占锁的重入次数
         */
        static final int SHARED_SHIFT   = 16;
        //10000000000000000
        static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
        //1111111111111111
        static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
        //1111111111111111
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /**
         * 	获取读锁的获取次数
         */
        static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
        /**
         * 	获取独占的重入次数
         */
        static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }

        /**
         * 	用于记录线程占用的读锁资源
         */
        static final class HoldCounter {
            int count = 0;
            final long tid = getThreadId(Thread.currentThread());
        }

        /**
         * ThreadLocal的子类
         */
        static final class ThreadLocalHoldCounter
            extends ThreadLocal<HoldCounter> {
            public HoldCounter initialValue() {
                return new HoldCounter();
            }
        }

        /**
         *	用于记录线程占用的读锁资源
         */
        private transient ThreadLocalHoldCounter readHolds;

        /**
         * 	缓存	最后一个获取读锁线程的获取读锁的次数 最后一个获取读锁的线程id
         */
        private transient HoldCounter cachedHoldCounter;

        /**
         *	在没有线程获取读锁的情况 第一个获取的
         */
        private transient Thread firstReader = null;
        /**
         * 	在没有线程获取读锁的情况 第一个获取的 获取次数
         */
        private transient int firstReaderHoldCount;

        Sync() {
            readHolds = new ThreadLocalHoldCounter();
            setState(getState());
        }

        /**
         * Returns true if the current thread, when trying to acquire
         * the read lock, and otherwise eligible to do so, should block
         * because of policy for overtaking other waiting threads.
         */
        abstract boolean readerShouldBlock();

        /**
         * Returns true if the current thread, when trying to acquire
         * the write lock, and otherwise eligible to do so, should block
         * because of policy for overtaking other waiting threads.
         */
        abstract boolean writerShouldBlock();
        /**
         * 	释放独占锁
         * 	@return true 写锁完全释放 false 没有完全释放
         */
        protected final boolean tryRelease(int releases) {
        	//当前线程不是独占线程 一直异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int nextc = getState() - releases;
            //释放后的重入次数 等于0说明当前写锁完全释放 
            boolean free = exclusiveCount(nextc) == 0;
            //完全释放的话 就把当前的独占线程设置为null
            if (free)
                setExclusiveOwnerThread(null);
            //设置state
            setState(nextc);
            return free;
        }
        /**
         * 	尝试获取独占锁
         * 	true 获取成功 false 获取失败
         */
        protected final boolean tryAcquire(int acquires) {
        	//获取当前线程
            Thread current = Thread.currentThread();
            int c = getState();
            //获取重入次数
            int w = exclusiveCount(c);
            //c不等于0说一定存在读锁或者写锁
            if (c != 0) {
            	//w==0说明没有写锁 一定有读锁 直接返回获取失败
            	//w != 0 说明有写锁 此时肯定没有读锁 但是当前线程不是独占线程 也直接返回false
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                //超过写锁的最大重入次数
                if (w + exclusiveCount(acquires) > MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                //设置重入次数
                setState(c + acquires);
                return true;
            }
            //到这里说明此时没有读锁也没有写锁
            if (writerShouldBlock() ||
            	//cas设置重入次数失败
                !compareAndSetState(c, c + acquires))
                return false;
            //设置当前线程为独占线程
            setExclusiveOwnerThread(current);
            return true;
        }
        /**
         * 	尝试释放共享锁
         * @return true 表示当前没有读锁了 false 表示当前还有读锁
         */
        protected final boolean tryReleaseShared(int unused) {
        	//获取当前锁
            Thread current = Thread.currentThread();
            //在没有线程获取读锁的情况 第一个获取的线程等于当前线程
            if (firstReader == current) {
            	//获取读锁次数为1
                if (firstReaderHoldCount == 1)
                	//置空firstReader
                    firstReader = null;
                else
                	//获取读锁次数为-1
                    firstReaderHoldCount--;
            } else {
            	//最后一个获取读锁线程的获取读锁的次数 最后一个获取读锁的线程id
                HoldCounter rh = cachedHoldCounter;
                //rh为null或者rh中线程的id不等于当前线程的id  当前是不是最后一个
                if (rh == null || rh.tid != getThreadId(current))
                	//不是的话 就到ThreadLocal获取当前的HoldCounter
                    rh = readHolds.get();
                //获取当前线程获取读锁的次数
                int count = rh.count;
                //获取读锁的次数小于等于1
                if (count <= 1) {
                	//移除掉当前的线程获取读锁的信息
                    readHolds.remove();
                    if (count <= 0)
                        throw unmatchedUnlockException();
                }
                //获取次数--
                --rh.count;
            }
            for (;;) {//死循环
                int c = getState();
                //读锁被获取的总次数-1
                int nextc = c - SHARED_UNIT;
                if (compareAndSetState(c, nextc))
                	//表示当前没有读锁
                    return nextc == 0;
            }
        }

        private IllegalMonitorStateException unmatchedUnlockException() {
            return new IllegalMonitorStateException(
                "attempt to unlock read lock, not locked by current thread");
        }
        /**
         * 	获取共享锁
         * @return 大于等于0 表示获取成功 否者失败
         */
        protected final int tryAcquireShared(int unused) {
        	//获取当前线程
            Thread current = Thread.currentThread();
            int c = getState();
            //独占锁的重入次数不为0 说明写锁已经被获取到了
            if (exclusiveCount(c) != 0 &&
            	//独占线程不是当前线程  表示不是获取到写锁的线程要再获取读锁
                getExclusiveOwnerThread() != current)
                return -1;
            //读锁的获取次数
            int r = sharedCount(c);
            //是否要阻塞当前读线程
            if (!readerShouldBlock() &&
            	//读锁的获取次数要小于最大读锁获取次数
                r < MAX_COUNT &&
                //读锁的获取次数+1
                compareAndSetState(c, c + SHARED_UNIT)) {
            	//说明是第一获取读锁的线程
                if (r == 0) {
                	//在没有线程获取读锁的情况 第一个获取的
                    firstReader = current;
                    //在没有线程获取读锁的情况 第一个获取的获取次数
                    firstReaderHoldCount = 1;
                } else if (firstReader == current) {
                	//在没有线程获取读锁的情况 第一个获取的获取次数
                    firstReaderHoldCount++;
                } else {
                	//最后一个获取读锁线程的获取读锁的cachedHoldCounter
                    HoldCounter rh = cachedHoldCounter;
                    //cachedHoldCounter设置为当前线程的
                    if (rh == null || rh.tid != getThreadId(current))
                    	//重新设置为当前线程的cachedHoldCounter
                        cachedHoldCounter = rh = readHolds.get();
                    //如果次数为0
                    else if (rh.count == 0)
                        readHolds.set(rh);
                    //获取次数+1
                    rh.count++;
                }
                return 1;
            }
            //再次尝试获取读锁
            return fullTryAcquireShared(current);
        }

        /**
         * 	再次尝试获取读锁
         */
        final int fullTryAcquireShared(Thread current) {
        	//
            HoldCounter rh = null;
            for (;;) {//死循环
                int c = getState();
                //独占锁的重入次数不为0 说明写锁已经被获取到了
                if (exclusiveCount(c) != 0) {
                	//独占线程不是当前线程  表示不是获取到写锁的线程要再获取读锁
                    if (getExclusiveOwnerThread() != current)
                        return -1;
                  //是否要阻塞当前读线程
                } else if (readerShouldBlock()) {
                    if (firstReader == current) {
                        
                    } else {
                        if (rh == null) {
                            rh = cachedHoldCounter;
                            if (rh == null || rh.tid != getThreadId(current)) {
                                rh = readHolds.get();
                                if (rh.count == 0)
                                    readHolds.remove();
                            }
                        }
                        if (rh.count == 0)
                            return -1;
                    }
                }
                //当前读锁的获取次数等于最大获取次数
                if (sharedCount(c) == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                //读锁的获取次数+1 表示获取成功
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                	//说明是第一获取读锁的线程
                    if (sharedCount(c) == 0) {
                    	//第一个获取读锁的线程
                        firstReader = current;
                        //第一个获取读锁的线程获取读锁的次数
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                    	//第一个获取读锁的线程获取读锁的次数
                    	firstReaderHoldCount++;
                    } else {
                    	//cachedHoldCounter设置为当前线程的
                        if (rh == null)
                            rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                        cachedHoldCounter = rh; // cache for release
                    }
                    return 1;
                }
            }
        }

        /**
         * 	尝试获取写锁
         */
        final boolean tryWriteLock() {
        	//获取当前线程
            Thread current = Thread.currentThread();
            int c = getState();
            //说明此时至少有一个读锁或者一个写锁
            if (c != 0) {
            	//获取重入次数
                int w = exclusiveCount(c);
                //重入次数为0 说明此时只有读锁存在 直接返回false
                //重入次数不为0 说明此时只有写锁存在 当前线程不是独占线程的话 直接返回false
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                //超出写锁的最大重入次数
                if (w == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
            }
            //c==0 说明此时没有读锁也没有写锁
            //cas设置重入次数 失败直接返回false
            if (!compareAndSetState(c, c + 1))
                return false;
            //设置当前线程为独占线程
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * 	尝试获取读锁
         */
        final boolean tryReadLock() {
        	//获取当前线程
            Thread current = Thread.currentThread();
            for (;;) {//死循环
                int c = getState();
                //当前有写锁且当前线程不是写锁的独占线程 直接返回false
                if (exclusiveCount(c) != 0 &&
                    getExclusiveOwnerThread() != current)
                    return false;
                //读锁的获取次数
                int r = sharedCount(c);
                //超过最大值 直接抛出异常
                if (r == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                //cas 读锁的获取次数+1
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                	//当前还没有读锁
                    if (r == 0) {
                    	//把firstReader设置为当前线程
                    	//firstReaderHoldCount为1
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {//如果当前就是firstReader
                    	//直接firstReaderHoldCount为1
                        firstReaderHoldCount++;
                    } else {
                        HoldCounter rh = cachedHoldCounter;
                        //在判断cachedHoldCounter是不是缓存了当前线程的获取次数
                        if (rh == null || rh.tid != getThreadId(current))
                        	//没有的话  从ThreadLocal获取
                            cachedHoldCounter = rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        //获取次数加一
                        rh.count++;
                    }
                    return true;
                }
            }
        }
        /**
         * 	判断当前线程是不是独占线程
         */
        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        // Methods relayed to outer class

        final ConditionObject newCondition() {
            return new ConditionObject();
        }
        /**
         * 	获取拥有写锁的线程
         * 	当前线程没有拥有写锁 直接返回null
         */
        final Thread getOwner() {
            return ((exclusiveCount(getState()) == 0) ?
                    null :
                    getExclusiveOwnerThread());
        }
        /**
         * 	获取读锁总的被获取次数
         */
        final int getReadLockCount() {
            return sharedCount(getState());
        }
        /**
         * 	是否被写锁锁住
         */
        final boolean isWriteLocked() {
            return exclusiveCount(getState()) != 0;
        }
        /**
         * 	获取写锁的重入次数
         */
        final int getWriteHoldCount() {
            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
        }
        /**
         * 	获取当前线程读锁的获取次数
         */
        final int getReadHoldCount() {
        	//读锁总的被获取次数等于0 说明此时没有读锁 直接返回0
            if (getReadLockCount() == 0)
                return 0;
            //获取当前线程
            Thread current = Thread.currentThread();
            //恰巧当前线程是firstReader
            if (firstReader == current)
            	//直接返回firstReaderHoldCount
                return firstReaderHoldCount;
            HoldCounter rh = cachedHoldCounter;
            //恰巧cachedHoldCounter不为null而且cachedHoldCounter中的线程是当前线程
            if (rh != null && rh.tid == getThreadId(current))
                return rh.count;//直接返回cachedHoldCounter.counter
            //前面都没有获取到的话 就到ThreadLocal中去拿
            int count = readHolds.get().count;
            //等于0说明没有获取次数了  从ThreadLocal中移除掉
            if (count == 0) readHolds.remove();
            return count;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            readHolds = new ThreadLocalHoldCounter();
            setState(0); // reset to unlocked state
        }

        final int getCount() { return getState(); }
    }

    /**
     * 	非公平的同步器
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -8159625535654395037L;
        final boolean writerShouldBlock() {
            return false; // writers can always barge
        }
        /**
         * 	是否要阻塞当前读线程
         * 	true 阻塞  false 不阻塞
         */
        final boolean readerShouldBlock() {
            /* As a heuristic to avoid indefinite writer starvation,
             * block if the thread that momentarily appears to be head
             * of queue, if one exists, is a waiting writer.  This is
             * only a probabilistic effect since a new reader will not
             * block if there is a waiting writer behind other enabled
             * readers that have not yet drained from the queue.
             */
            return apparentlyFirstQueuedIsExclusive();
        }
    }

    /**
     * 	公平的同步器
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -2274990926593161451L;
        /**
         * 	是否要阻塞当前读线程
         * 	true 阻塞  false 不阻塞
         */
        final boolean writerShouldBlock() {
            return hasQueuedPredecessors();
        }
        /**
         * 	是否要阻塞当前读线程
         * 	true 阻塞  false 不阻塞
         */
        final boolean readerShouldBlock() {
            return hasQueuedPredecessors();
        }
    }

    /**
     * The lock returned by method {@link ReentrantReadWriteLock#readLock}.
     */
    public static class ReadLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -5992448646407690164L;
        private final Sync sync;

        /**
         * Constructor for use by subclasses
         *
         * @param lock the outer lock object
         * @throws NullPointerException if the lock is null
         */
        protected ReadLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         * 	获取锁
         */
        public void lock() {
            sync.acquireShared(1);
        }

        /**
         *	获取锁 可中断的
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         * 	尝试获取锁
         * @return true 获取成功 false 获取失败
         */
        public boolean tryLock() {
            return sync.tryReadLock();
        }

        /**
         * 	尝试获取锁 有超时时间 可中断
         * @param timeout 超时时间
         * @param unit 时间单位
         * @return true 获取成功 false 获取失败
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        /**
         * 	释放锁
         */
        public void unlock() {
            sync.releaseShared(1);
        }

        /**
         *	 不支持
         * @throws UnsupportedOperationException always
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns a string identifying this lock, as well as its lock state.
         * The state, in brackets, includes the String {@code "Read locks ="}
         * followed by the number of held read locks.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() +
                "[Read locks = " + r + "]";
        }
    }

    /**
     * 	写锁
     */
    public static class WriteLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -4992448646407690164L;
        private final Sync sync;

        /**
         * Constructor for use by subclasses
         *
         * @param lock the outer lock object
         * @throws NullPointerException if the lock is null
         */
        protected WriteLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         * 	获取锁
         */
        public void lock() {
            sync.acquire(1);
        }

        /**
         *	获取锁 可中断的
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        /**
         * 	尝试获取写锁
         * 	@return true 获取成功 false 获取失败
         */
        public boolean tryLock( ) {
            return sync.tryWriteLock();
        }

        /**
         *	获取锁 有超时时间 可中断的
         * @param timeout 超时时间
         * @param unit	时间单位
         * @return true 获取成功 false 获取失败
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        /**
         * 	释放锁
         */
        public void unlock() {
            sync.release(1);
        }

        /**
         * 	获取Condition
         */
        public Condition newCondition() {
            return sync.newCondition();
        }

        /**
         * Returns a string identifying this lock, as well as its lock
         * state.  The state, in brackets includes either the String
         * {@code "Unlocked"} or the String {@code "Locked by"}
         * followed by the {@linkplain Thread#getName name} of the owning thread.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            Thread o = sync.getOwner();
            return super.toString() + ((o == null) ?
                                       "[Unlocked]" :
                                       "[Locked by thread " + o.getName() + "]");
        }

        /**
         * 	判断当前线程是不是独占线程
         */
        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        /**
         * 	获取写锁的重入次数
         */
        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }

    // Instrumentation and status

    /**
     * 	判断内部使用的是公平锁还是非公平锁
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * 	获取拥有写锁的线程
     * 	当前线程没有拥有写锁 直接返回null
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * 	获取读锁总的被获取次数
     */
    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    /**
     *	是否被写锁锁住
     */
    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    /**
     * 	判断当前线程是不是获取到了写锁、独占线程
     */
    public boolean isWriteLockedByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * 	获取当前线程的写锁的重入次数
     */
    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    /**
     * 	获取当前线程读锁的获取次数
     */
    public int getReadHoldCount() {
        return sync.getReadHoldCount();
    }

    /**
     *	获取写线程
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getExclusiveQueuedThreads();
    }

    /**
     * 	获取读线程
     * 	获取共享阻塞队列中的阻塞线程 以集合的形式返回 
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        return sync.getSharedQueuedThreads();
    }

    /**
     *	判断阻塞队列中是否有阻塞线程
     *	@return true 有阻塞线程 false 没有阻塞线程
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     *	 判断当前线程是否在阻塞队列中
     * @return true 在 false 不在
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     *	获取当前阻塞队列中线程的个数
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     *	获取当前阻塞队列中的阻塞线程集合
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * 	condition要属于当前ReentrantLock
     *	查看condition的条件队列是否有阻塞线程
     *	@return true 有 false 没有
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     *	condition要属于当前ReentrantLock
     *	获取condition的条件队列中的等待线程数量
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     *	condition要属于当前ReentrantLock
     *	获取condition的条件队列中的等待线程的集合
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a string identifying this lock, as well as its lock state.
     * The state, in brackets, includes the String {@code "Write locks ="}
     * followed by the number of reentrantly held write locks, and the
     * String {@code "Read locks ="} followed by the number of held
     * read locks.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        int c = sync.getCount();
        int w = Sync.exclusiveCount(c);
        int r = Sync.sharedCount(c);

        return super.toString() +
            "[Write locks = " + w + ", Read locks = " + r + "]";
    }

    /**
     * Returns the thread id for the given thread.  We must access
     * this directly rather than via method Thread.getId() because
     * getId() is not final, and has been known to be overridden in
     * ways that do not preserve unique mappings.
     */
    static final long getThreadId(Thread thread) {
        return UNSAFE.getLongVolatile(thread, TID_OFFSET);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long TID_OFFSET;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            TID_OFFSET = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("tid"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
