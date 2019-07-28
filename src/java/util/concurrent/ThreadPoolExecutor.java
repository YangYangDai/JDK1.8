package java.util.concurrent;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 *	线程池
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    /**
     * 	表示两个概念
     * 	1. workerCount：表明当前有效的线程数
     * 	2. runState：表明当前线程池的状态
     *   RUNNING:  接受新任务 并处理队列中的任务
     *   SHUTDOWN: 不接受新任务 但会处理队列中的任务
     *   STOP:     不接受新任务，不处理排队的任务 中断正在进行的任务
     *   TIDYING:      整理态，所有任务已经结束，workerCount = 0 ，将执行terminated()方法   
     *   TERMINATED: 结束态，terminated() 方法已完成
     *
     *
     * RUNNING -> SHUTDOWN
     *     	调用shutdown()
     * (RUNNING or SHUTDOWN) -> STOP
     *    	调用shutdownNow()
     * SHUTDOWN -> TIDYING
     *    When both queue and pool are empty
     * STOP -> TIDYING
     *    When pool is empty
     * TIDYING -> TERMINATED
     *    When the terminated() hook method has completed
     * AtomicInteger 线程安全的
     */
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    /**
     * 	有效的线程数所占为29位
     */
    private static final int COUNT_BITS = Integer.SIZE - 3;
    /**
     *	 得到的是低28位全是1的536870911
     */
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState以高阶位存储
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    /**
     * 	获取线程池的运行状态
     */
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    /**
     * 	获取当前运行的线程数å
     */
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    /**
     * 状态比较 
     */
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }
    /**
     * 状态比较 
     */
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }
    /**
     * 	判断是线程池是否在运行
     */
    private static boolean isRunning(int c) {
    	//RUNNING为负数
        return c < SHUTDOWN;
    }

    /**
     * cas 工作线程数+1
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * cas 工作线程数-1
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * 清空WorkerCount
     */
    private void decrementWorkerCount() {
        do {} while (! compareAndDecrementWorkerCount(ctl.get()));
    }

    /**
     * 	存放任务的阻塞队列
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * 	主要的可重入的独占锁
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * 	存放了所有的工作线程
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();

    /**
     * 	终止的Condition
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * Tracks largest attained pool size. Accessed only under
     * mainLock.
     */
    private int largestPoolSize;

    /**
     * 	完成的任务数
     */
    private long completedTaskCount;

    /**
     * 	线程工厂
     * 	这个可以设置一下线程的名字前缀  好用来区别专有的线程
     * 	volatile 保证其可见性和顺序性
     */
    private volatile ThreadFactory threadFactory;

    /**
     * 	拒绝执行时的处理策略
     * 	volatile 保证其可见性和顺序性
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * 	(最大线程数 - 核心线程数)的线程没有接到新任务最大的存活时间 
     * 	这里已经被转为纳秒了
     * 	volatile 保证其可见性和顺序性
     */
    private volatile long keepAliveTime;

    /**
     * 	默认 false 即使没有任务 核心线程仍是存活的
     * 	true  核心线程数的线程没有接到新任务最大的存活时间用keepAliveTime
     * 	volatile 保证其可见性和顺序性
     */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * 	核心线程数
     * 	volatile 保证其可见性和顺序性
     */
    private volatile int corePoolSize;

    /**
     * 	最大线程数
     * 	volatile 保证其可见性和顺序性
     */
    private volatile int maximumPoolSize;

    /**
     * 	默认拒绝执行时的处理策略
     *  直接丢弃所有还没执行的任务
     */
    private static final RejectedExecutionHandler defaultHandler =
        new AbortPolicy();

    /**
     * 
     */
    private static final RuntimePermission shutdownPerm =
        new RuntimePermission("modifyThread");

    /* The context to be used when executing the finalizer, or null. */
    private final AccessControlContext acc;

    /**
     * 	执行任务的对象是一个Runnable又是一个同步器
     */
    private final class Worker
        extends AbstractQueuedSynchronizer
        implements Runnable
    {
        private static final long serialVersionUID = 6138294804551838833L;

        /**
         * 	要执行的Worker线程
         */
        final Thread thread;
        /**
         * 	要执行的任务
         */
        Runnable firstTask;
        /**
         * 	此Worker执行完成的任务数
         */
        volatile long completedTasks;

        /**
         * 初始化一个Worker
         * @param firstTask 任务
         */
        Worker(Runnable firstTask) {
        	//设置同步器的状态值
            setState(-1); 
            this.firstTask = firstTask;
            //将当前Worker->Thread
            this.thread = getThreadFactory().newThread(this);
        }
        /**
         * 调用runWorker
         */
        public void run() {
        	//调用runWorker
        	//传入自己本身
            runWorker(this);
        }
        /**
         * 判断当前线程是不是独占线程
         */
        protected boolean isHeldExclusively() {
            return getState() != 0;
        }
        /**
         * 尝试获取独占锁
         */
        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }
        /**
         * 尝试释放独占锁
         */
        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }
        
        /**
         * 只要是启动的都中断掉
         */
        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /*
     * Methods for setting control state
     */

    /**	
     *	设置线程池的运行状态 必须是有先后次序的
     */
    private void advanceRunState(int targetState) {
        for (;;) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||
                ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * 尝试终止线程池
     */
    final void tryTerminate() {
        for (;;) {//死循环
        	//获取线程池的状态和工作线程数
            int c = ctl.get();
            //1.线程池处于运行状态
            //2.线程池处于TERMINATED状态
            //3.线程池处于SHUTDOWN状态且任务阻塞队列不为空
            //都是直接返回
            if (isRunning(c) ||
                runStateAtLeast(c, TIDYING) ||
                (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
                return;
            //工作线程数不为0
            if (workerCountOf(c) != 0) { // Eligible to terminate
            	//中断空闲的工作线程
                interruptIdleWorkers(ONLY_ONE);
                return;
            }
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
            	//cas设置运行转态为TIDYING
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                    	//留给子类实现 钩子函数
                        terminated();
                    } finally {
                    	//cas设置运行转态为TERMINATED
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
        }
    }

    /*
     * Methods for controlling interrupts to worker threads.
     */

    /**
     * 关闭线程权限的校验
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    security.checkAccess(w.thread);
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * 中断所有的工作线程
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
        	//中断所有的workers
            for (Worker w : workers)
                w.interruptIfStarted();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 中断空闲的工作线程
     * onlyOne为true时 只能中断一个工作线程
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                //工作线程没有被标志中断且能获取到锁的
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                    	//把工作线程标志为中断
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 中断Worker
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     */

    /**
     * 执行拒绝策略
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    void onShutdown() {
    }

    /**
     * State check needed by ScheduledThreadPoolExecutor to
     * enable running tasks during shutdown.
     *
     * @param shutdownOK true if should return true if SHUTDOWN
     */
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }

    /**
     * 获取到任务阻塞队列中的所有任务 并删除掉
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    /*
     * Methods for creating, running and cleaning up after workers
     */

    /**
     * 添加工作线程
     *
     * @param firstTask 任务 可能会为null
     *
     * @param core true 表示是核心线程数
     * @return true if successful
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
    	//用于跳出循环
        retry:
        for (;;) {//死循环
        	//获取线程池的状态和工作线程数
            int c = ctl.get();
            //获取线程池的运行状态
            int rs = runStateOf(c);
            //线程池状态处于STOP、TIDYING、TERMINATED 会直接不执行firstTask
            //线程池状态处于SHUTDOWN且新添加进来的任务firstTask!=null 返回添加失败
            //线程池状态处于SHUTDOWN且任务阻塞队列为null 返回添加失败
            //说明处于SHUTDOWN状态 任务阻塞队列中的任务还是会被执行 新添加进来的任务firstTask不会被执行
            if (rs >= SHUTDOWN &&
                ! (rs == SHUTDOWN &&
                   firstTask == null &&
                   ! workQueue.isEmpty()))
                return false;//

            for (;;) {//死循环
            	//获取工作线程数
                int wc = workerCountOf(c);
                //工作线程数大于等于容量 返回添加失败
                //如果是添加核心线程 工作线程数大于核心线程数 返回添加失败
                //如果是添加最大线程数 工作线程数大于最大线程数 返回添加失败
                if (wc >= CAPACITY ||
                    wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;
                //cas 工作线程数+1
                if (compareAndIncrementWorkerCount(c))
                    break retry;//跳出最外层的循环
                //获取线程池的状态和工作线程数
                c = ctl.get(); 
                //当前的线程池状态与进入内层循环之前的状态一直
                //就回到外层循环去
                if (runStateOf(c) != rs)
                    continue retry;
            }
        }
    	//标志Worker没有被启动	
        boolean workerStarted = false;
        //标志Worker是不是被新添加的
        boolean workerAdded = false;
        Worker w = null;
        try {
        	//new一个Worker
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                //获取锁
                mainLock.lock();
                try {
                	//获取当前线程池的状态
                    int rs = runStateOf(ctl.get());
                    //1.处于运行状态
                    //2.处于SHUTDOWN状态且firstTask为null
                    if (rs < SHUTDOWN ||
                        (rs == SHUTDOWN && firstTask == null)) {
                    	//如果线程已经是启动了 抛出异常
                        if (t.isAlive())
                            throw new IllegalThreadStateException();
                        //添加工作线程到工作线程集合中去
                        workers.add(w);
                        //获取当前的工作线程数量
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        //表示是新增工作线程
                        workerAdded = true;
                    }
                } finally {
                	//释放锁
                    mainLock.unlock();
                }
                //如果是新添加的工作线程
                if (workerAdded) {
                    t.start();//就启动它
                    workerStarted = true;
                }
            }
        } finally {
        	//如果启动工作线程失败的话
            if (! workerStarted)
            	//工作线程集合中移除掉w
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    /**
     * 工作线程集合中移除掉w
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            //清空WorkerCount
            decrementWorkerCount();
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     *	Worker结束时调用 统计整个线程池完成的任务个数之类的工作
     * @param w worker
     * @param completedAbruptly true 发生异常 false 正常执行
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
    	//发生异常的 清空WorkerCount
        if (completedAbruptly) 
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
        	//完成的任务数等于每个工作线程完成的任务数之和
            completedTaskCount += w.completedTasks;
            //移除执行完的工作线程
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        tryTerminate();
        //获取线程池的状态
        int c = ctl.get();
        //线程池的状态如果是SHUTDOWN或者RUNNING
        if (runStateLessThan(c, STOP)) {
        	//没有发生异常的
            if (!completedAbruptly) {
            	//最小的工作线程数 默认是corePoolSize
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                //min == 0且任务阻塞队列中海油任务
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;//最小的工作线程数设置为1
                //当前运行的线程数大于等于min
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            //增加工作线
            addWorker(null, false);
        }
    }

    /**
     * 获取Runnable对象
     */
    private Runnable getTask() {
        boolean timedOut = false; 
        for (;;) {//死循环
            int c = ctl.get();
            ////获取线程池的状态
            int rs = runStateOf(c);

            //当前状态>=STOP 清空WorkerCount
            //或者当前状态=SHUTDOWN且存放任务的阻塞队列清空WorkerCount
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                //清空WorkerCount
            	decrementWorkerCount();
            	//直接返回null
                return null;
            }
            //获取当前运行的线程数
            int wc = workerCountOf(c);

            //判断当前会不会有超时要被淘汰的Worker
            //1. allowCoreThreadTimeOut设置为了true timed=true
            //2. 当前运行的线程数大于核心线程数 timed=true
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;
            //(当前运行的线程数大于最大线程数或者 (timed && timedOut)为true)
            //且(当前运行的线程数大于1或者存放任务的阻塞队列不为空)
            if ((wc > maximumPoolSize || (timed && timedOut))
                && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
            	//当前会有超时要被淘汰的Worker就在指定的时间获取队列中的任务 超时会获取到null
            	//否者的话就直接从队列中获取任务
                Runnable r = timed ?
                	//出队列 指定
                    workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                    workQueue.take();
                if (r != null)
                    return r;
                //第一次循环之后timedOut会被设置为true
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     * 	执行任务
     *
     * @param w the worker
     */
    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        //firstTask置为null
        w.firstTask = null;
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
        	//不断获取任务执行
        	//当前Worker中的Runnable对象不为空task != null
        	//当前Worker中的Runnable对象为null getTask()去获取一个Runnable对象
            while (task != null || (task = getTask()) != null) {
            	//获取当前Worker的独占锁
                w.lock();
                //当线程池是处于STOP状态或者TIDYING、TERMINATED状态时，设置当前线程处于中断状态 runStateAtLeast(ctl.get(), STOP)
                //当前线程就处于RUNNING或者SHUTDOWN状态并清除线程的中断状态 Thread.interrupted() &&runStateAtLeast(ctl.get(), STOP)
                //确保当前线程不处于中断状态 !wt.isInterrupted()
                //就是说线程池是处于STOP状态或者TIDYING、TERMINATED状态时 设置当前线程处于中断状态
                //如果处于RUNNING或者SHUTDOWN状态要清除线程的中断状态 不设置当前线程处于中断状态
                if ((runStateAtLeast(ctl.get(), STOP) ||
                     (Thread.interrupted() &&
                      runStateAtLeast(ctl.get(), STOP))) &&
                    !wt.isInterrupted())
                    wt.interrupt();//当线程池是处于STOP状态或者TIDYING、TERMINATED状态时 且还未设置中断状态的
                try {
                	//运行之前的操作 钩子方法
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                    	//调用Runnable的run方法
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                    	//运行之后的操作 钩子方法
                        afterExecute(task, thrown);
                    }
                } finally {
                	//Runnable对象设置为null
                    task = null;
                    //当前Worker执行完成的任务数
                    w.completedTasks++;
                    //释放当前Worker的独占锁
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
        	//Worker结束时调用 统计整个线程池完成的任务个数之类的工作
            processWorkerExit(w, completedAbruptly);
        }
    }
    
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), defaultHandler);
    }
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             threadFactory, defaultHandler);
    }
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), handler);
    }

    /**
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime (最大线程数 - 核心线程数)的线程没有接到新任务最大的存活时间
     * @param unit 超时时间的单位
     * @param 存放任务的阻塞队列
     * @param threadFactory 线程工厂
     * @param handler 拒绝策略
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
            maximumPoolSize <= 0 ||
            maximumPoolSize < corePoolSize ||
            keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.acc = System.getSecurityManager() == null ?
                null :
                AccessController.getContext();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     *
     * @param command 任务
     */
    public void execute(Runnable command) {
    	//任务为null直接抛出异常
        if (command == null)
            throw new NullPointerException();
        //获取线程池的状态和工作线程数
        int c = ctl.get();
        //运行的线程数小于核心线程数
        if (workerCountOf(c) < corePoolSize) {
        	//把command作为工作线程的firstTask
            if (addWorker(command, true))
                return;
            //获取线程池的状态和工作线程数
            c = ctl.get();
        }
        //线程池处于运行状态且把任务成功放入阻塞队列中
        if (isRunning(c) && workQueue.offer(command)) {
        	//获取线程池的状态和工作线程数
            int recheck = ctl.get();
            //线程池没有处于运行状态且把在阻塞队列的任务删除成功
            if (!isRunning(recheck) && remove(command))
            	//执行拒绝策略
                reject(command);
            //当前运行的线程数等于0
            else if (workerCountOf(recheck) == 0)
            	//添加工作线程
                addWorker(null, false);
        }
        //添加工作线程失败的话 执行拒绝策略
        else if (!addWorker(command, false))
        	//执行拒绝策略
            reject(command);
    }

    /**
     *	线程池的状态变为SHUTDOWN状态
     *	中断掉空闲的worker
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        //获取锁
        mainLock.lock();
        try {
        	//关闭线程必要权限的校验
            checkShutdownAccess();
            //设置线程池的运行状态为SHUTDOWN
            advanceRunState(SHUTDOWN);
            //中断掉空闲的worker
            interruptIdleWorkers();
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
        	//释放锁
            mainLock.unlock();
        }
        tryTerminate();
    }

    /**
     *	线程池的状态变为STOP状态
     *	@return 获取到任务阻塞队列中的所有任务
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
        	//关闭线程必要权限的校验
            checkShutdownAccess();
            //设置线程池的运行状态为STOP
            advanceRunState(STOP);
            //中断所有的工作线程
            interruptWorkers();
            //获取到任务阻塞队列中的所有任务
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        //尝试终止线程池
        tryTerminate();
        return tasks;
    }
    /**
     * 是否Shutdown
     * 没有在运行就是被Shutdown
     */
    public boolean isShutdown() {
        return ! isRunning(ctl.get());
    }

    /**
     * 线程池是否在为TIDYING
     * 没有在运行且小于TERMINATED
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return ! isRunning(c) && runStateLessThan(c, TERMINATED);
    }
    /**
     * 线程池是否在为TERMINATED
     * 状态大于等于TERMINATED
     */
    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
                if (runStateAtLeast(ctl.get(), TERMINATED))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes {@code shutdown} when this executor is no longer
     * referenced and it has no threads.
     */
    protected void finalize() {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null || acc == null) {
            shutdown();
        } else {
            PrivilegedAction<Void> pa = () -> { shutdown(); return null; };
            AccessController.doPrivileged(pa, acc);
        }
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    /**
     * 	返回当前的线程工厂
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler(RejectedExecutionHandler)
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.  If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
        else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Starts a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return {@code false}
     * if all core threads have already been started.
     *
     * @return {@code true} if a thread was started
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&
            addWorker(null, true);
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }

    /**
     * Starts all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * @return the number of threads started
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    /**
     * Returns true if this pool allows core threads to time out and
     * terminate if no tasks arrive within the keepAlive time, being
     * replaced if needed when new tasks arrive. When true, the same
     * keep-alive policy applying to non-core threads applies also to
     * core threads. When false (the default), core threads are never
     * terminated due to lack of incoming tasks.
     *
     * @return {@code true} if core threads are allowed to time out,
     *         else {@code false}
     *
     * @since 1.6
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * Sets the policy governing whether core threads may time out and
     * terminate if no tasks arrive within the keep-alive time, being
     * replaced if needed when new tasks arrive. When false, core
     * threads are never terminated due to lack of incoming
     * tasks. When true, the same keep-alive policy applying to
     * non-core threads applies also to core threads. To avoid
     * continual thread replacement, the keep-alive time must be
     * greater than zero when setting {@code true}. This method
     * should in general be called before the pool is actively used.
     *
     * @param value {@code true} if should time out, else {@code false}
     * @throws IllegalArgumentException if value is {@code true}
     *         and the current keep-alive time is not greater than zero
     *
     * @since 1.6
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if the new maximum is
     *         less than or equal to zero, or
     *         less than the {@linkplain #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the core number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     *
     * @param time the time to wait.  A time value of zero will cause
     *        excess threads to terminate immediately after executing tasks.
     * @param unit the time unit of the {@code time} argument
     * @throws IllegalArgumentException if {@code time} less than zero or
     *         if {@code time} is zero and {@code allowsCoreThreadTimeOut}
     * @see #getKeepAliveTime(TimeUnit)
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0)
            interruptIdleWorkers();
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * that threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime(long, TimeUnit)
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.
     *
     * <p>This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue. For
     * example, a task entered using {@code submit} might be
     * converted into a form that maintains {@code Future} status.
     * However, in such cases, method {@link #purge} may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task the task to remove
     * @return {@code true} if the task was removed
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     * Tries to remove from the work queue all {@link Future}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    q.remove(r);
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = (runStateLessThan(c, SHUTDOWN) ? "Running" :
                     (runStateAtLeast(c, TERMINATED) ? "Terminated" :
                      "Shutting down"));
        return super.toString() +
            "[" + rs +
            ", pool size = " + nworkers +
            ", active threads = " + nactive +
            ", queued tasks = " + workQueue.size() +
            ", completed tasks = " + ncompleted +
            "]";
    }

    /* Extension hooks */

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.afterExecute} at the
     * beginning of this method.
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     *  <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null && r instanceof Future<?>) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * {@code super.terminated} within this method.
     */
    protected void terminated() { }

    /* Predefined RejectedExecutionHandlers */

    /**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() { }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a
     * {@code RejectedExecutionException}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() { }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }

    /**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() { }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries {@code execute}, unless the executor
     * is shut down, in which case the task is discarded.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() { }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
