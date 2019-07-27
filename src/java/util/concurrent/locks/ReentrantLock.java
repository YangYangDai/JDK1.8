package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.Collection;

/**
 * 可重入的独占锁
 * 内部实现了公平和非公平的同步器
 */
public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;
    /**
     * 同步器
     */
    private final Sync sync;

    /**
     * 内部同步器
     * 继承AbstractQueuedSynchronizer
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        /**
         * 留给子类实现
         */
        abstract void lock();

        /**
         * 非公平的尝试获取独占锁 
         * 获取成功返回true 否者false
         */
        final boolean nonfairTryAcquire(int acquires) {
        	//获取当前线程
            final Thread current = Thread.currentThread();
            //获取重入次数
            int c = getState();
            if (c == 0) {//0表示独占锁还没被获取
            	//cas尝试设置重入次数 这里就是获取独占锁
                if (compareAndSetState(0, acquires)) {
                	//获取独占锁成功 设置当前线程为独占线程
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }//已经有线程获取了独占锁 判断独占线程是不是当前线程
            else if (current == getExclusiveOwnerThread()) {
            	//是的话  重入次数加acquires
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);//设置重入次数
                return true;
            }
            return false;
        }
        /**
         * 尝试释放独占锁 
         * 如果释放完全也就是state为0 返回true 否者返回false
         */
        protected final boolean tryRelease(int releases) {
        	//得到重入次数
            int c = getState() - releases;
            //当前线程不是独占线程
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            boolean free = false;
            //重入次数等于0 说明独占锁可以释放
            if (c == 0) {
                free = true;
                //置空独占锁
                setExclusiveOwnerThread(null);
            }
            //设置重入次数
            setState(c);
            return free;
        }
        /**
         * 	判断当前线程是不是独占线程
         */
        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }
        /**
         * 获取Condition 内部由条件队列
         */
        final ConditionObject newCondition() {
            return new ConditionObject();
        }
        /**
         * 获取独占线程 重入次数如果为0 说明当前没有独占线程 
         */
        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }
        /**
         * 获取当前线程的重入次数
         * 如果当前线程都不是独占线程 直接返回0
         */
        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }
        /**
         * 判断当前是否被锁住
         * 判断独占线程的重入次数就知道了
         */
        final boolean isLocked() {
            return getState() != 0;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

    /**
     *	非公平的同步器
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;

        /**
         * 尝试获取独占锁
         */
        final void lock() {
        	//通过cas设置state的值 设置成功 表示抢到独占锁
            if (compareAndSetState(0, 1))
            	//设置独占线程为当前线程
                setExclusiveOwnerThread(Thread.currentThread());
            else
                acquire(1);//尝试获取独占锁
        }
        /**
         * 尝试获取独占锁
         */
        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }

    /**
     * 公平的同步器
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;
        /**
         * 获取锁
         */
        final void lock() {
            acquire(1);
        }

        /**
         * 尝试获取锁 阻塞队列如果没有阻塞线程 尝试获取
         * 成功返回true 失败返回false 
         */
        protected final boolean tryAcquire(int acquires) {
        	//获取当前线程
            final Thread current = Thread.currentThread();
            //获取当前的锁资源
            int c = getState();
            if (c == 0) {//0表示独占锁还没被获取
            	//阻塞队列中没有阻塞节点且cas设置重入次数成功
            	//这里是公平的 前面已经有阻塞线程了我就不尝试获取独占锁了
                if (!hasQueuedPredecessors() &&
                    compareAndSetState(0, acquires)) {
                	//设置当前线程为独占线程
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            //判断当前的线程是不是独占线程 用于可重入
            else if (current == getExclusiveOwnerThread()) {
                //增加重入次数
            	int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);//设置重入的次数
                return true;
            }
            return false;
        }
    }

    /**
     * 无参构造
     * 默认非公平的同步器
     */
    public ReentrantLock() {
        sync = new NonfairSync();
    }

    /**
     * true 公平的同步器 false 非公平的同步器
     * @param fair 是否公平 
     */
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    /**
     * 获取独占锁
     */
    public void lock() {
        sync.lock();
    }

    /**
     * 获取独占锁 可中断的
     */
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    /**
     *	尝试获取独占锁 
     * @return true 获取成功 false 获取失败
     */
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    /**
     *	在一定时间内尝试获取独占锁  可中断的
     * @param timeout 等待获取锁的时间
     * @param unit 时间的单位
     * @return true 获取成功 false 获取失败
     */
    public boolean tryLock(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    /**
     * 释放独占锁
     */
    public void unlock() {
        sync.release(1);
    }

    /**
     * 获取Condition 每次都是获取一个新的Condition
     */
    public Condition newCondition() {
        return sync.newCondition();
    }

    /**
     * 获取当前线程的重入次数
     * 如果当前线程都不是独占线程 直接返回0
     */
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    /**
     * 	判断当前线程是不是独占线程
     * 	@return true 当前线程是独占线程 false 当前线程不是独占线程
     */
    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     *	判断当前是否被锁住
     *	@return true 被锁住的 false 没有被锁住的
     */
    public boolean isLocked() {
        return sync.isLocked();
    }

    /**
     *	判断内部使用的是公平锁还是非公平锁
     * @return {@code true} if this lock has fairness set true
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     *	获取独占线程 重入次数如果为0 说明当前没有独占线程 
     *	
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     *	判断阻塞队列中是否有阻塞线程
     *	@return true 有阻塞线程 false 没有阻塞线程
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * 判断当前线程是否在阻塞队列中
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
     * The state, in brackets, includes either the String {@code "Unlocked"}
     * or the String {@code "Locked by"} followed by the
     * {@linkplain Thread#getName name} of the owning thread.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ?
                                   "[Unlocked]" :
                                   "[Locked by thread " + o.getName() + "]");
    }
}
