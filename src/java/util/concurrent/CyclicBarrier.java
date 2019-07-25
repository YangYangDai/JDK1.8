package java.util.concurrent;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 	底层是有ReentrantLock+Condition实现
 *	 可重复使用
 * 	多个线程相互等待 最后一个 到达时 然后一起执行
 */
public class CyclicBarrier {
    /**
     * 	代  
     *  CyclicBarrier是可循环使用的
     */
    private static class Generation {
        boolean broken = false;
    }

   /**
    * 	可重入锁
    */
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * Condition
     */
    private final Condition trip = lock.newCondition();
    /**
     * 	触发屏障之前需要调用的线程数
     */
    private final int parties;
    /**
     * 	触发屏障时执行
     * 	调用的线程数等于parties
     */
    private final Runnable barrierCommand;
    /**
     * 	当前所处的代
     */
    private Generation generation = new Generation();

    /**
     * 	还没到达屏障处的线程数
     */
    private int count;

    /**
     * 	到达屏障处的操作
     * 	开启下一次
     * 	唤醒所有阻塞的线程
     * 	从新设置count
     * 	生成新的一代
     */
    private void nextGeneration() {
        //唤醒此condition中所有的线程
        trip.signalAll();
        //从新设置还没到达屏障处的线程数
        count = parties;
        //生成新的一代
        generation = new Generation();
    }

    /**
     * 	打破栅栏
     */
    private void breakBarrier() {
        generation.broken = true;
        count = parties;
        //唤醒此condition中所有的线程
        trip.signalAll();
    }

    /**
     * 	等待的关键代码
     */
    private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
               TimeoutException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Generation g = generation;
            //判断栅栏有没有被打破
            if (g.broken)
                throw new BrokenBarrierException();
            //判断线程有没有被中断
            if (Thread.interrupted()) {
                breakBarrier();
                throw new InterruptedException();
            }
            int index = --count;
            //当所有的线程都到达屏障处
            if (index == 0) {  // tripped
                boolean ranAction = false;
                try {
                	//触发屏障时执行
                    final Runnable command = barrierCommand;
                    //执行barrierCommand 构造传入
                    if (command != null)
                        command.run();
                    ranAction = true;
                    //到达屏障处的操作 开启下一次
                    nextGeneration();
                    return 0;
                } finally {
                    if (!ranAction)
                        breakBarrier();
                }
            }

            //死循环
            for (;;) {
                try {
                	//没有设置超时时间
                    if (!timed)
                    	//添加到条件队列中去 并挂起
                        trip.await();
                    else if (nanos > 0L)
                    	//添加到条件队列中去 并挂起 有超时时间
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        // We're about to finish waiting even if we had not
                        // been interrupted, so this interrupt is deemed to
                        // "belong" to subsequent execution.
                        Thread.currentThread().interrupt();
                    }
                }
                //判断栅栏没有破损
                if (g.broken)
                    throw new BrokenBarrierException();
                //generation发生了改变
                if (g != generation)
                    return index;
                //超时 打破栅栏 并抛出异常
                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     *
     * @param parties 触发屏障之前需要调用的线程数
     * @param barrierAction 触发屏障时执行
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }

    /**
     * @param parties 触发屏障之前需要调用的线程数
     */
    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    /**
     * 	获取需要执行的线程数
     */
    public int getParties() {
        return parties;
    }

    /**
     *	 等待
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

    /**
     *
     * @param timeout 最大等待时间
     * @param unit 时间的单位
     */
    public int await(long timeout, TimeUnit unit)
        throws InterruptedException,
               BrokenBarrierException,
               TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    /**
     * 	判断当前栅栏是不是被打破的
     */
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 	打破当前的栅栏
     * 	开启新的
     */
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // 打破栅栏
            nextGeneration(); // 开启下一个循环
        } finally {
            lock.unlock();
        }
    }

    /**
     * 	获取已经到达屏障处的线程数量 也就是处于等待状态的线程数量
     */
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}
