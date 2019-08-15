package java.util.concurrent;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 	CountDownLatch 使用AQS中的共享锁
 * 	可以使用CountDownLatch完成一下的工作
 * 	一个或多个线程等待一个或多完成之后再执行
 * 	await方法  同步状态的值大于0线程阻塞
 * 	countDown方法 同步状态的值减一 同步状态的值等于0 唤起所有等待的线程
 */
public class CountDownLatch {
    /**
     * 	同步器
     * 	继承AbstractQueuedSynchronizer
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 4982264981922014374L;

        Sync(int count) {
            setState(count);//设置同步状态的值
        }

        int getCount() {
            return getState();//获取同步状态的值
        }
        /**
         * 	 尝试获取共享锁
         *	 同步状态的值等于0 当前等待的线程获取到锁
         *	否则就需要等待
         *	则返回1 否者返回-1
         */
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }
        /**
         * 	尝试释放共享锁
         * 	把同步状态的值减一
         * 	如果state减到0 则返回true 唤醒所有阻塞的线程
         */
        protected boolean tryReleaseShared(int releases) {
            for (;;) {//死循环
                int c = getState();//获取同步状态的值
                if (c == 0)//值为0返回false
                    return false;
                int nextc = c-1;//值减一
                //cas设置值 设置成功且nextc等于0返回true否者false
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }
    }
    /**
     * 	同步器
     */
    private final Sync sync;

    /**
     * 	初始化内部属性Sync 同步器 设置同步状态的值
     * @param count 同步状态的值
     */
    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.sync = new Sync(count);
    }

    /**
     * 	尝试获取共享锁可中断对外提供的
     * 	内部同步器实现
     * 	state != 0 阻塞线程
     */
    public void await() throws InterruptedException {
    	//调用AQS中的模板方法
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * 	在一定时间内尝试获取共享锁可中断对外提供的
     * 	内部同步器实现
     */
    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
    	//调用AQS中的模板方法
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * 	state减一
     * 	内部同步器实现
     */
    public void countDown() {
    	//调用AQS中的模板方法
        sync.releaseShared(1);
    }

    /**
     * 	获取同步状态的值
     * 	内部同步器实现
     */
    public long getCount() {
        return sync.getCount();
    }

    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }
}
