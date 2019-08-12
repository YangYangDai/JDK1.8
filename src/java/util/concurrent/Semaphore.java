package java.util.concurrent;
import java.util.Collection;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 *	信号量 内部使用共享锁实现
 *	以许可证为共享资源控制线程数
 *	内部有公平和非公平的两种同步器
 * 	线程抢夺到指定个数的许可证后 可执行
 * 	用完然后释放掉 阻塞的线程会抢夺许可证 抢到许可证后可执行
 *  有公平和非公平的两种实现
 */
public class Semaphore implements java.io.Serializable {
    private static final long serialVersionUID = -3222578661600680210L;
    /**
     * 	同步器
     * 	继承AbstractQueuedSynchronizer
     */
    private final Sync sync;

    /**
     * 	抽象的同步器
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1192457210091910933L;

        Sync(int permits) {
            setState(permits);
        }
        /**
         *	获取当前可用的许可证
         */
        final int getPermits() {
            return getState();
        }
        /**
         *	非公平尝试获取许可证
         *	获取锁失败 remaining<0
         *	获取锁成功 remaining>=0
         *	@param acquires 请求获取的许可证个数
         */
        final int nonfairTryAcquireShared(int acquires) {
            for (;;) {//死循环
                int available = getState();
                //剩余的可用许可证个数
                int remaining = available - acquires;
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
            }
        }
        /**
         *	尝试释放许可证
         *	@param releases 准备释放的许可证个数
         */
        protected final boolean tryReleaseShared(int releases) {
            for (;;) {//死循环
            	//获取当前可用的许可证数量
                int current = getState();
                int next = current + releases;
                if (next < current) 
                    throw new Error("Maximum permit count exceeded");
                //cas设置许可证的数量 成功返回true
                if (compareAndSetState(current, next))
                    return true;
            }
        }
        /**
         *	减少许可证数量
         *	@param reductions 减少许可证的数量
         */
        final void reducePermits(int reductions) {
            for (;;) {//死循环
            	//获取当前可用的许可证数量
                int current = getState();
                //减少可用的许可证数量
                int next = current - reductions;
                if (next > current) 
                    throw new Error("Permit count underflow");
                //cas设置许可证的数量 成功返回true
                if (compareAndSetState(current, next))
                    return;
            }
        }
        /**
         * 	占用当前所有可用的许可镇数量
         * 	@return 置空之前的许可证数量
         */
        final int drainPermits() {
            for (;;) {
                int current = getState();
                if (current == 0 || compareAndSetState(current, 0))
                    return current;
            }
        }
    }

    /**
     * 	非公平同步器 
     * 	不管先后顺序 都可以尝试获取许可证 默认都是非公平的 效率高 但是可能会出现长时间等待的线程
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -2694183684443567898L;

        NonfairSync(int permits) {
            super(permits);
        }
        /**
         *	尝试获取许可证
         *	@param acquires 请求获取的许可证个数
         */
        protected int tryAcquireShared(int acquires) {
        	//获取锁失败 remaining<0 获取锁成功 remaining>=0
            return nonfairTryAcquireShared(acquires);
        }
    }

    /**
     * 	公平同步器 
     * 	先到先得 后来要排队 可以保证不会有线程长时间等待得不到执行
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = 2014338818796000944L;

        FairSync(int permits) {
            super(permits);
        }
        /**
         * 	尝试请求许可证
         * @param acquires 请求获取的许可证个数
         * @return 获取锁失败 remaining<0 获取锁成功 remaining>=0
         */
        protected int tryAcquireShared(int acquires) {
            for (;;) {
            	//阻塞队列中已经有线程在排队了 直接返回 -1
                if (hasQueuedPredecessors())
                    return -1;
                //获取可用的许可证
                int available = getState();
                //剩余可用的许可证个数
                int remaining = available - acquires;
                //获取锁失败 remaining<0 获取锁成功 remaining>=0
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
            }
        }
    }

    /**
     *	默认是非公平的
     * @param permits 请求获取的许可证数量
     */
    public Semaphore(int permits) {
        sync = new NonfairSync(permits);
    }

    /**
     * @param permits 请求获取的许可证数量
     * @param fair true 公平 false 非公平
     */
    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }

    /**
     * 	获取一个许可证 可中断
     */
    public void acquire() throws InterruptedException {
    	//调用的是AQS中的模板方法
        sync.acquireSharedInterruptibly(1);
    }

    /**
     *	 获取一个许可证 不可中断
     */
    public void acquireUninterruptibly() {
    	//调用的是AQS中的模板方法
        sync.acquireShared(1);
    }

    /**
     *	循环获取一个许可证  不会让线程去阻塞队列
     * @return 获取成功 true 获取失败 false
     */
    public boolean tryAcquire() {
    	//仅使用CAS+自旋 不会加入阻塞队列中 
        return sync.nonfairTryAcquireShared(1) >= 0;
    }

    /**
     *	一定时间获取共享锁  可中断
     * @param timeout 最大等待获取许可证的时间
     * @param unit timeout的时间单位
     * @return 获取成功返回true 获取时间超时返回false
     */
    public boolean tryAcquire(long timeout, TimeUnit unit)
        throws InterruptedException {
    	//调用的是AQS中的模板方法
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * 	释放一个许可证
     */
    public void release() {
    	//调用的是AQS中的模板方法
        sync.releaseShared(1);
    }

    /**
     *	获取几个许可证  可中断
     * @param permits 获取的许可证数
     */
    public void acquire(int permits) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        //调用的是AQS中的模板方法
        sync.acquireSharedInterruptibly(permits);
    }

    /**
     * 	获取几个许可证  不可中断
     * @param permits 获取的许可证数
     */
    public void acquireUninterruptibly(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        //调用的是AQS中的模板方法
        sync.acquireShared(permits);
    }

    /**
     *	内部循环获取几个许可证  不可中断 
     * @param permits the number of permits to acquire
     * @return 获取成功 true 获取失败 false 
     */
    public boolean tryAcquire(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        //仅使用CAS+自旋 不会加入阻塞队列中 
        return sync.nonfairTryAcquireShared(permits) >= 0;
    }

    /**
     *	尝试获取几个许可证
     * @param permits 许可证的个数
     * @param timeout 最大等待时间
     * @param unit 时间的单位
     * @return 获取成功 true 失败或者超时 false 
     */
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        //调用的是AQS中的模板方法
        return sync.tryAcquireSharedNanos(permits, unit.toNanos(timeout));
    }

    /**
     *	释放许可证
     * @param permits 许可证的数量
     * @throws 
     */
    public void release(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
       //调用的是AQS中的模板方法
        sync.releaseShared(permits);
    }

    /**
     *	获取可用的许可证数量
     * @return 可用许可证数量
     */
    public int availablePermits() {
        return sync.getPermits();
    }

    /**
     *	占用当前所有可用的许可证数量
     * @return 当前所有可用的许可证数量
     */
    public int drainPermits() {
        return sync.drainPermits();
    }

    /**
     *	减少可用的许可证数量
     * @param reduction 需要减少的数量
     */
    protected void reducePermits(int reduction) {
        if (reduction < 0) throw new IllegalArgumentException();
        sync.reducePermits(reduction);
    }

    /**
     * 	判断当前使用的是公平锁还是非公平
     * @return 公平返回true 非公平返回false
     */
    public boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     *	阻塞队列中除了头节点是否还有其他阻塞节点
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * 	获取阻塞队列中的被阻塞的线程数 
     * 	头节点是获取到锁的 注意这里是不包含头节点  的头节点的线程被置空了
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     *	获取阻塞队列中被阻塞的线程集合 
     *	头节点是获取到锁的 注意这里是不包含头节点  的头节点的线程被置空了
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * Returns a string identifying this semaphore, as well as its state.
     * The state, in brackets, includes the String {@code "Permits ="}
     * followed by the number of permits.
     *
     * @return a string identifying this semaphore, as well as its state
     */
    public String toString() {
        return super.toString() + "[Permits = " + sync.getPermits() + "]";
    }
}
