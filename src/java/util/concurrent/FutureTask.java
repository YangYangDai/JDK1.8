package java.util.concurrent;
import java.util.concurrent.locks.LockSupport;

/**
 * FutureTask
 */
public class FutureTask<V> implements RunnableFuture<V> {

    /**
     * FutureTask的运行状态
     * 	运行状态的转换:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    private volatile int state;
    private static final int NEW          = 0;//任务还没开始执行
    private static final int COMPLETING   = 1;//任务执行完成了 但是返回结果可能还没设置好
    //大于COMPLETING 说明任务都是完成了的 不管是中断还是取消还是返回正常或者非正常结果 都是完成了
    private static final int NORMAL       = 2;//任务执行完成了返回结果也设置好了
    private static final int EXCEPTIONAL  = 3;//任务执行完成了返回结果设置的是异常信息
    private static final int CANCELLED    = 4;//任务被取消的
    private static final int INTERRUPTING = 5;//线程中断状态被设置ture 但线程未响应中断
    private static final int INTERRUPTED  = 6;//线程被中断的

    /**
     * 	真正获取返回值的地方
     */
    private Callable<V> callable;
    /**
     * 	返回结果 可能是异常信息
     */
    private Object outcome; 
    /**
     * 	执行callable的线程
     */
    private volatile Thread runner;
    /**
     * 	等待队列
     */
    private volatile WaitNode waiters;

    /**
     *	 返回结果 正常结果值或者异常信息
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
    	//获取到结果
        Object x = outcome;
        //如果运行状态是NORMAL
        if (s == NORMAL)
        	//直接把对象转成V类型
            return (V)x;
        //运行状态为CANCELLED、INTERRUPTING、INTERRUPTED 直接抛出CancellationException
        if (s >= CANCELLED)
            throw new CancellationException();
        //EXCEPTIONAL状态直接抛出ExecutionException
        throw new ExecutionException((Throwable)x);
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * 
     */
    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }

    public boolean isCancelled() {
        return state >= CANCELLED;
    }

    public boolean isDone() {
        return state != NEW;
    }
    /**
     * 	取消任务的执行
     * @param mayInterruptIfRunning	mayInterruptIfRunning 是否能中断
     * @return true 成功 false 失败
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
    	//运行状态不是NEW或者cas设置INTERRUPTING或CANCELLED状态失败 直接返回false
        if (!(state == NEW &&
              UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                  mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;
        try {
        	//当前可中断运行的
            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner;
                    if (t != null)
                    	//设置标志位中断的
                        t.interrupt();
                } finally {
                	//并设置为当前的运行状态为INTERRUPTED
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            finishCompletion();
        }
        return true;
    }

    /**
     * 	会阻塞到获取返回结果为止 可中断的 
     */
    public V get() throws InterruptedException, ExecutionException {
    	//获取到运行状态
        int s = state;
        //如果运行状态小于COMPLETING 说明此时结果还没设置
        if (s <= COMPLETING)
        	//等待执行完成且结果的设置
            s = awaitDone(false, 0L);
        return report(s);
    }

    /**
     * 	获取返回结果 有超时时间的
     */
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
    	//时间单位为null直接抛出异常
        if (unit == null)
            throw new NullPointerException();
        //获取运行状态
        int s = state;
        //运行状态为NEW、COMPLETING且等待执行后状态还是NEW、COMPLETING 直接抛出超时异常
        if (s <= COMPLETING &&
            (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
            throw new TimeoutException();
        return report(s);
    }

    /**
     * 	留给子类实现
     */
    protected void done() { }

    /**
     * 	设置返回结果
     * 	先把运行状态设置设置COMPLETING 设置成功 再把outcome设置为v
     *	最后设置为NORMAL
     * @param v the value
     */
    protected void set(V v) {
    	//先把运行状态设置设置COMPLETING
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            finishCompletion();
        }
    }

    /**
     * 	先把运行状态设置设置COMPLETING 设置成功 再把outcome设置为Throwable
     * 	最后把运行状态设置为EXCEPTIONAL
     */
    protected void setException(Throwable t) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL);
            finishCompletion();
        }
    }
    /**
     * 	当FutureTask线程执行时
     */
    public void run() {
    	//状态不为New或者设置把当前线程设置为runner失败 直接返回 
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return;
        try {
            Callable<V> c = callable;
            //callable不为null且执行状态为New
            if (c != null && state == NEW) {
            	//返回值
                V result;
                //callable是否执行成功
                boolean ran;
                try {
                	//获取callable运行后的结果
                    result = c.call();
                    ran = true;
                } catch (Throwable ex) {//异常的
                	//返回值设置为null
                    result = null;
                    //callable执行失败
                    ran = false;
                    //把运行状态设置先设置COMPLETING 最后设置为EXCEPTIONAL
                    //并把outcome设置为ex
                    setException(ex);
                }
                //执行成功
                if (ran)
                    set(result);
            }
        } finally {
            runner = null;
            int s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }

    /**
     *	
     * @return {@code true} if successfully run and reset
     */
    protected boolean runAndReset() {
    	//运行装不是NEW或者设置runner为当前线程失败的 直接返回false
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return false;
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            //callable不为null且执行状态为New
            if (c != null && s == NEW) {
                try {
                	//获取callable运行后的结果 这里没有设置返回结果
                    c.call(); 
                    ran = true;
                } catch (Throwable ex) {
                	 //把运行状态设置先设置COMPLETING 最后设置为EXCEPTIONAL
                    //并把outcome设置为ex
                    setException(ex);
                }
            }
        } finally {
            runner = null;
            s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        return ran && s == NEW;
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }

    /**
     * 	唤醒等待队列中存在的线程
     */
    private void finishCompletion() {
        for (WaitNode q; (q = waiters) != null;) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                    	//置空节点中的线程
                        q.thread = null;
                        //唤醒t
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null;
                    q = next;
                }
                break;
            }
        }
        //留给子类实现
        done();
        //能获取到执行结果了 把callable设置为null
        callable = null;
    }

    /**
     *	等待执行完成且结果的设置或者任务中断或者超时
     * @param timed 是否设置超时
     * @param nanos 超时时间 单位纳秒
     * @return state 任务的执行状态
     */
    private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
    	//计算超时的时间点
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (;;) {//死循环
        	//返回线程是否被标记中断 并清除中断标记
            if (Thread.interrupted()) {
            	//删除节点q 并抛出异常
                removeWaiter(q);
                throw new InterruptedException();
            }
            //获取当前运行状态
            int s = state;
            //大于COMPLETING 直接返回说明执行完成了 不管是中断还是返回正常或者非正常结果 都是完成了
            if (s > COMPLETING) {
            	//节点不为空 置空节点中的线程
                if (q != null)
                    q.thread = null;
                return s;
            }
            //表示结果等待赋值的
            else if (s == COMPLETING) 
            	//让出cpu给其它线程执行
                Thread.yield();
            else if (q == null)//等待节点为null 则new一个WaitNode
                q = new WaitNode();
            else if (!queued)
            	//如果当前的waiters为null 则把当前节点设置为waiters 成为队头
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q.next = waiters, q);
            else if (timed) {//设置了超时
            	//当前的剩余时间
                nanos = deadline - System.nanoTime();
                //如果一场超时了
                if (nanos <= 0L) {
                	//删除节点q 返回运行状态
                    removeWaiter(q);
                    return state;
                }
                //挂起当前线程
                LockSupport.parkNanos(this, nanos);
            }
            else
            	//挂起当前线程
                LockSupport.park(this);
        }
    }

    /**
     * 	移除节点node
     * 	找到下一个等待的线程 并把节点作为waiters
     */
    private void removeWaiter(WaitNode node) {
    	//node不为空
        if (node != null) {
        	//先把node关联的线程设置为null
            node.thread = null;
            retry:
            for (;;) {//死循环
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        pred = q;
                    else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) 
                            continue retry;
                    }
                    else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                          q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
