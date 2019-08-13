package java.lang;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.LockSupport;
import sun.nio.ch.Interruptible;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;
import sun.security.util.SecurityConstants;


/**
 * 	线程
 * 	创建线程的两种方式
 *	1. 继承Thread
 *	2. 实现Runnable
 */
public
class Thread implements Runnable {
    /* Make sure registerNatives is the first thing <clinit> does. */
    private static native void registerNatives();
    static {
        registerNatives();
    }
    /**
     * 	线程的名称
     * 	volatile 保证可见性
     */
    private volatile String name;
    /**
     * 	线程的优先级
     */
    private int            priority;
    private Thread         threadQ;
    private long           eetop;

    /* Whether or not to single_step this thread. */
    private boolean     single_step;

    /**
     * 	是否是守护程序线程
     */
    private boolean     daemon = false;

    /* JVM state */
    private boolean     stillborn = false;

    /**
     * 	将要执行的目标
     */
    private Runnable target;

    /**
     * 	线程所属的线程组
     */
    private ThreadGroup group;

    /**
     * 	线程的上下文ClassLoader
     */
    private ClassLoader contextClassLoader;

    /* The inherited AccessControlContext of this thread */
    private AccessControlContext inheritedAccessControlContext;

    /**
     *	 用于匿名线程:自动编号
     */
    private static int threadInitNumber;
    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }

    /* ThreadLocal values pertaining to this thread. This map is maintained
     * by the ThreadLocal class. */
    ThreadLocal.ThreadLocalMap threadLocals = null;

    /*
     * InheritableThreadLocal values pertaining to this thread. This map is
     * maintained by the InheritableThreadLocal class.
     */
    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;

    /*
     *	此线程的请求堆栈大小，如果创建者未指定堆栈大小，则为0。
     */
    private long stackSize;

    /*
     * JVM-private state that persists after native thread termination.
     */
    private long nativeParkEventPointer;

    /*
     * 	线程的id
     */
    private long tid;

    /* For generating thread ID */
    private static long threadSeqNumber;

    /* 
     * 	线程的状态
     */
    private volatile int threadStatus = 0;


    private static synchronized long nextThreadID() {
        return ++threadSeqNumber;
    }

    /**
     * The argument supplied to the current call to
     * java.util.concurrent.locks.LockSupport.park.
     * Set by (private) java.util.concurrent.locks.LockSupport.setBlocker
     * Accessed using java.util.concurrent.locks.LockSupport.getBlocker
     */
    volatile Object parkBlocker;

    /* The object in which this thread is blocked in an interruptible I/O
     * operation, if any.  The blocker's interrupt method should be invoked
     * after setting this thread's interrupt status.
     */
    private volatile Interruptible blocker;
    private final Object blockerLock = new Object();

    /* Set the blocker field; invoked via sun.misc.SharedSecrets from java.nio code
     */
    void blockedOn(Interruptible b) {
        synchronized (blockerLock) {
            blocker = b;
        }
    }

    /**
     *	 线程的最低优先级
     */
    public final static int MIN_PRIORITY = 1;

   /**
     * 	 线程的默认优先级
     */
    public final static int NORM_PRIORITY = 5;

    /**
     * 	线程的最高优先级
     */
    public final static int MAX_PRIORITY = 10;

    /**
     * 	返回当前正在执行的线程对象的引用
     */
    public static native Thread currentThread();

    /**
     * 
     */
    public static native void yield();

    /**
     * 	可中断的
     *	线程休眠指定时间，又会重入抢夺cpu 
     *	如果当前线程已经获取到锁，是不会释放锁的
     * @param  millis 休眠的时间 单位毫秒
     */
    public static native void sleep(long millis) throws InterruptedException;

    /**
     * 	可中断的
     * 	当前正在执行的线程休眠（暂时停止执行）指定的毫秒数加上指定的纳秒数，具体取决于系统定时器和调度程序的精度和准确性
     * 	如果当前线程已经获取到锁，是不会释放锁的
     * @param  millis 休眠的时间 单位毫秒
     * @param  nanos 取值范围0-999999 再加上需要休眠纳秒
     */
    public static void sleep(long millis, int nanos)
    throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException(
                                "nanosecond timeout value out of range");
        }

        if (nanos >= 500000 || (nanos != 0 && millis == 0)) {
            millis++;
        }

        sleep(millis);
    }

    /**
     *	 使用当前的AccessControlContext初始化一个Thread
     */
    private void init(ThreadGroup g, Runnable target, String name,
                      long stackSize) {
        init(g, target, name, stackSize, null, true);
    }

    /**
     * 	初始化一个Thread
     *
     * @param g 线程组
     * @param target 调用run（）方法的对象
     * @param name 新建线程的名称
     * @param stackSize 新线程的所需堆栈大小，orzero指示要忽略此参数。
     * @param acc AccessControlContext
     * @param inheritThreadLocals if {@code true}, inherit initial values for
     *            inheritable thread-locals from the constructing thread
     */
    private void init(ThreadGroup g, Runnable target, String name,
                      long stackSize, AccessControlContext acc,
                      boolean inheritThreadLocals) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }

        this.name = name;

        Thread parent = currentThread();
        SecurityManager security = System.getSecurityManager();
        if (g == null) {
            /* Determine if it's an applet or not */

            /* If there is a security manager, ask the security manager
               what to do. */
            if (security != null) {
                g = security.getThreadGroup();
            }

            /* If the security doesn't have a strong opinion of the matter
               use the parent thread group. */
            if (g == null) {
                g = parent.getThreadGroup();
            }
        }

        /* checkAccess regardless of whether or not threadgroup is
           explicitly passed in. */
        g.checkAccess();

        /*
         * Do we have the required permissions?
         */
        if (security != null) {
            if (isCCLOverridden(getClass())) {
                security.checkPermission(SUBCLASS_IMPLEMENTATION_PERMISSION);
            }
        }

        g.addUnstarted();

        this.group = g;
        this.daemon = parent.isDaemon();
        this.priority = parent.getPriority();
        if (security == null || isCCLOverridden(parent.getClass()))
            this.contextClassLoader = parent.getContextClassLoader();
        else
            this.contextClassLoader = parent.contextClassLoader;
        this.inheritedAccessControlContext =
                acc != null ? acc : AccessController.getContext();
        this.target = target;
        setPriority(priority);
        if (inheritThreadLocals && parent.inheritableThreadLocals != null)
            this.inheritableThreadLocals =
                ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
        /* Stash the specified stack size in case the VM cares */
        this.stackSize = stackSize;

        /* Set thread ID */
        tid = nextThreadID();
    }

    /**
     * Throws CloneNotSupportedException as a Thread can not be meaningfully
     * cloned. Construct a new Thread instead.
     *
     * @throws  CloneNotSupportedException
     *          always
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /**
     * 	new一个Thread对象
     * 	默认的生成的名字--》 Thread-自动编号(nextThreadNum())
     */
    public Thread() {
        init(null, null, "Thread-" + nextThreadNum(), 0);
    }

    /**
     *	new一个Thread对象
     *	默认的生成的名字--》 Thread-自动编号(nextThreadNum())
     * @param  target 调用run（）方法的对象
     */
    public Thread(Runnable target) {
        init(null, target, "Thread-" + nextThreadNum(), 0);
    }

    /**
     * 	指定AccessControlContextnew一个Thread对象 
     */
    Thread(Runnable target, AccessControlContext acc) {
        init(null, target, "Thread-" + nextThreadNum(), 0, acc, false);
    }

    /**
     *	new一个Thread对象
     * @param  group 线程组
     * @param  target	调用run（）方法的对象
     */
    public Thread(ThreadGroup group, Runnable target) {
        init(group, target, "Thread-" + nextThreadNum(), 0);
    }

    /**
     * new一个Thread对象
     * @param   name 新线程的名称
     */
    public Thread(String name) {
        init(null, null, name, 0);
    }

    /**
     *	new一个Thread对象
     * @param  group 线程组
     * @param  name	新线程的名称
     */
    public Thread(ThreadGroup group, String name) {
        init(group, null, name, 0);
    }

    /**
     * new一个Thread对象
     * @param  target 调用run（）方法的对象
     * @param  name 新线程的名称
     */
    public Thread(Runnable target, String name) {
        init(null, target, name, 0);
    }

    /**
     *	new一个Thread对象
     * @param  group	线程组
     * @param  target	调用run（）方法的对象
     * @param  name	新线程的名称
     */
    public Thread(ThreadGroup group, Runnable target, String name) {
        init(group, target, name, 0);
    }

    /**
     *	new一个Thread对象
     * @param  group 线程组
     * @param  target	调用run（）方法的对象
     * @param  name 新线程的名称
     * @param  stackSize	新线程的所需堆栈大小，orzero指示要忽略此参数
     */
    public Thread(ThreadGroup group, Runnable target, String name,
                  long stackSize) {
        init(group, target, name, stackSize);
    }

    /**
     * 	线程开始执行; Java虚拟机调用此线程的  run 方法
     */
    public synchronized void start() {
    	//启动之前要保证线程的状态为New
        if (threadStatus != 0)
            throw new IllegalThreadStateException();

        /* 
         *	将线程加入线程组
         * */
        group.add(this);

        boolean started = false;
        try {
            start0();
            started = true;
        } finally {
            try {
                if (!started) {
                    group.threadStartFailed(this);
                }
            } catch (Throwable ignore) {
                /* do nothing. If start0 threw a Throwable then
                  it will be passed up the call stack */
            }
        }
    }
    /**
     *  	本地方法启动线程
     */
    private native void start0();

    /**
     * 	线程执行run方法调用将要执行的目标run方法
     */
    @Override
    public void run() {
        if (target != null) {
            target.run();
        }
    }

    /**
     * 	系统调用此方法，以便Thread在实际退出之前有机会进行清理。
     */
    private void exit() {
        if (group != null) {
            group.threadTerminated(this);
            group = null;
        }
        /* Aggressively null out all reference fields: see bug 4006245 */
        target = null;
        /* Speed the release of some of these resources */
        threadLocals = null;
        inheritableThreadLocals = null;
        inheritedAccessControlContext = null;
        blocker = null;
        uncaughtExceptionHandler = null;
    }
    /**
     * 	弃用
     */
    @Deprecated
    public final void stop() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            checkAccess();
            if (this != Thread.currentThread()) {
                security.checkPermission(SecurityConstants.STOP_THREAD_PERMISSION);
            }
        }
        // A zero status value corresponds to "NEW", it can't change to
        // not-NEW because we hold the lock.
        if (threadStatus != 0) {
            resume(); // Wake up thread if it was suspended; no-op otherwise
        }

        // The VM can handle all thread states
        stop0(new ThreadDeath());
    }

    /**
     * 	弃用
     */
    @Deprecated
    public final synchronized void stop(Throwable obj) {
        throw new UnsupportedOperationException();
    }

    /**
     *	将线程中断标志位设置为true
     */
    public void interrupt() {
        if (this != Thread.currentThread())
            checkAccess();

        synchronized (blockerLock) {
            Interruptible b = blocker;
            if (b != null) {
                interrupt0();           // Just to set the interrupt flag
                b.interrupt(this);
                return;
            }
        }
        interrupt0();
    }

    /**
     * 	返回当前线程的中断标志位状态 然后清除设置为false
     */
    public static boolean interrupted() {
        return currentThread().isInterrupted(true);
    }

    /**
     * 	判断线程中断标志位是不是true 不会清除中断标示位
     */
    public boolean isInterrupted() {
        return isInterrupted(false);
    }

    /**
     * 	本地方法
     *	 测试某些线程是否已被中断 
     *	根据传递的ClearInterrupted值重置或不重置中断状态。
     *	@param  ClearInterrupted true 重置中断标志位 false 不重置
     */
    private native boolean isInterrupted(boolean ClearInterrupted);
    /**
     * 	弃用
     */
    @Deprecated
    public void destroy() {
        throw new NoSuchMethodError();
    }

    /**
     * 	测试此线程是否处于活动状态。如果线程已经启动并且尚未死亡，则该线程处于活动状态。
     *
     * @return  true 存活  false 死亡
     */
    public final native boolean isAlive();

    /**
     * 	弃用
     */
    @Deprecated
    public final void suspend() {
        checkAccess();
        suspend0();
    }

    /**
     * 	弃用
     */
    @Deprecated
    public final void resume() {
        checkAccess();
        resume0();
    }

    /**
     * 	修改线程的优先级
     * @param newPriority 新的优先级
     */
    public final void setPriority(int newPriority) {
        ThreadGroup g;
        checkAccess();
        //优先级的值需要在[0,10]
        if (newPriority > MAX_PRIORITY || newPriority < MIN_PRIORITY) {
            throw new IllegalArgumentException();
        }
        if((g = getThreadGroup()) != null) {
            if (newPriority > g.getMaxPriority()) {
                newPriority = g.getMaxPriority();
            }
            setPriority0(priority = newPriority);
        }
    }

    /**
     *	 获取线程的优先级
     * @return  优先级
     */
    public final int getPriority() {
        return priority;
    }

    /**
     * 	修改线程的名称
     * @param      name   新的名称
     */
    public final synchronized void setName(String name) {
        checkAccess();
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        this.name = name;
        if (threadStatus != 0) {
            setNativeName(name);
        }
    }

    /**
     *	获取线程的名称
     */
    public final String getName() {
        return name;
    }

    /**
     * 	获取线程所属的线程组
     */
    public final ThreadGroup getThreadGroup() {
        return group;
    }
    /**
     * 	当前线程的线程组及其子组中的每个活动线程的总和  估计值
     */
    public static int activeCount() {
        return currentThread().getThreadGroup().activeCount();
    }

    /**
     * 	将当前线程的线程组及其子组中的每个活动线程复制到指定的数组中
     */
    public static int enumerate(Thread tarray[]) {
        return currentThread().getThreadGroup().enumerate(tarray);
    }

    /**
     * 	弃用
     */
    @Deprecated
    public native int countStackFrames();

    /**
     *	线程join当前线程 当前线程需要等待线程执行完成或者指定等待时间
     * @param  millis 等待时间	毫秒  millis = 0 等待死亡
     */
    public final synchronized void join(long millis)
    throws InterruptedException {
    	//获取当前时间的毫秒值
        long base = System.currentTimeMillis();
        long now = 0;
        //等待时间小于0直接抛出异常
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        //millis == 0
        if (millis == 0) {
        	//线程没有死亡 当前线程一直等待
            while (isAlive()) {
                wait(0);
            }
        } else {
        	//线程没有执行完成
            while (isAlive()) {
                long delay = millis - now;
                //等待完了指定时间 将不再继续等待
                if (delay <= 0) {
                    break;
                }
                wait(delay);
                now = System.currentTimeMillis() - base;
            }
        }
    }

    /**
     *	线程join当前线程 当前线程需要等待线程执行完成或者指定等待时间=毫秒+纳秒
     * @param  millis 等待时间 毫秒
     *
     * @param  nanos 等待时间 纳秒 取值 [0,999999]
     */
    public final synchronized void join(long millis, int nanos)
    throws InterruptedException {

        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException(
                                "nanosecond timeout value out of range");
        }
        if (nanos >= 500000 || (nanos != 0 && millis == 0)) {
            millis++;
        }
        //转换成millis然后调用
        join(millis);
    }

    /**
     *	 等待线程死亡
     */
    public final void join() throws InterruptedException {
    	//0表示需要等待线程的死亡
        join(0);
    }

    /**
     *	将当前线程的堆栈跟踪打印到标准错误流。此方法仅用于调试。
     */
    public static void dumpStack() {
        new Exception("Stack trace").printStackTrace();
    }

    /**
     *	将线程设置为守护线程
     *	 必须在启动线程之前调用此方法
     * @param  on true 设置为守护线程 
     */
    public final void setDaemon(boolean on) {
        checkAccess();
        //线程还没开始 存活的话 就不能设置了 直接抛出异常
        if (isAlive()) {
            throw new IllegalThreadStateException();
        }
        daemon = on;
    }

    /**
     *	判断线程是不是守护线程
     * @return  true 是守护线程 false 不是
     */
    public final boolean isDaemon() {
        return daemon;
    }

    /**
     *	确定当前运行的线程是否具有修改此线程的权限
     */
    public final void checkAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkAccess(this);
        }
    }
    public String toString() {
        ThreadGroup group = getThreadGroup();
        if (group != null) {
            return "Thread[" + getName() + "," + getPriority() + "," +
                           group.getName() + "]";
        } else {
            return "Thread[" + getName() + "," + getPriority() + "," +
                            "" + "]";
        }
    }

    /**
     * 	返回此Thread的上下文ClassLoader
     * 	上下文ClassLoader由线程的创建者提供
     * 	供加载类和资源时在此线程中运行的代码使用
     */
    @CallerSensitive
    public ClassLoader getContextClassLoader() {
        if (contextClassLoader == null)
            return null;
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            ClassLoader.checkClassLoaderPermission(contextClassLoader,
                                                   Reflection.getCallerClass());
        }
        return contextClassLoader;
    }

    /**
     * 	为此Thread设置上下文ClassLoader。可以在创建线程时设置上下文ClassLoader，
     * 	并允许线程的创建者通过{0code getContextClassLoader}向加载类和资源时在线程中运行的代码提供适当的类加载器。
     */
    public void setContextClassLoader(ClassLoader cl) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("setContextClassLoader"));
        }
        contextClassLoader = cl;
    }

    /**
     * 	判断线程是否获取了指定对象的锁
     * @param  obj the object on which to test lock ownership
     * @return true 获取了  false 没有获取
     */
    public static native boolean holdsLock(Object obj);

    private static final StackTraceElement[] EMPTY_STACK_TRACE
        = new StackTraceElement[0];

    /**
     * 	返回表示此线程的堆栈转储的堆栈跟踪元素数组。如果此线程尚未启动，已启动但尚未安排由系统运行或已终止，则此方法将返回零长度数组。
     * 	如果返回的数组长度非零，则数组的第一个元素表示堆栈的顶部，这是序列中最近的方法调用。数组的最后一个元素表示堆栈的底部，这是序列中最近的方法调用。
     */
    public StackTraceElement[] getStackTrace() {
        if (this != Thread.currentThread()) {
            // check for getStackTrace permission
            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkPermission(
                    SecurityConstants.GET_STACK_TRACE_PERMISSION);
            }
            // optimization so we do not call into the vm for threads that
            // have not yet started or have terminated
            if (!isAlive()) {
                return EMPTY_STACK_TRACE;
            }
            StackTraceElement[][] stackTraceArray = dumpThreads(new Thread[] {this});
            StackTraceElement[] stackTrace = stackTraceArray[0];
            // a thread that was alive during the previous isAlive call may have
            // since terminated, therefore not having a stacktrace.
            if (stackTrace == null) {
                stackTrace = EMPTY_STACK_TRACE;
            }
            return stackTrace;
        } else {
            // Don't need JVM help for current thread
            return (new Exception()).getStackTrace();
        }
    }

    /**
     * Returns a map of stack traces for all live threads.
     * The map keys are threads and each map value is an array of
     * <tt>StackTraceElement</tt> that represents the stack dump
     * of the corresponding <tt>Thread</tt>.
     * The returned stack traces are in the format specified for
     * the {@link #getStackTrace getStackTrace} method.
     *
     * <p>The threads may be executing while this method is called.
     * The stack trace of each thread only represents a snapshot and
     * each stack trace may be obtained at different time.  A zero-length
     * array will be returned in the map value if the virtual machine has
     * no stack trace information about a thread.
     *
     * <p>If there is a security manager, then the security manager's
     * <tt>checkPermission</tt> method is called with a
     * <tt>RuntimePermission("getStackTrace")</tt> permission as well as
     * <tt>RuntimePermission("modifyThreadGroup")</tt> permission
     * to see if it is ok to get the stack trace of all threads.
     *
     * @return a <tt>Map</tt> from <tt>Thread</tt> to an array of
     * <tt>StackTraceElement</tt> that represents the stack trace of
     * the corresponding thread.
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        <tt>checkPermission</tt> method doesn't allow
     *        getting the stack trace of thread.
     * @see #getStackTrace
     * @see SecurityManager#checkPermission
     * @see RuntimePermission
     * @see Throwable#getStackTrace
     *
     * @since 1.5
     */
    public static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        // check for getStackTrace permission
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(
                SecurityConstants.GET_STACK_TRACE_PERMISSION);
            security.checkPermission(
                SecurityConstants.MODIFY_THREADGROUP_PERMISSION);
        }

        // Get a snapshot of the list of all threads
        Thread[] threads = getThreads();
        StackTraceElement[][] traces = dumpThreads(threads);
        Map<Thread, StackTraceElement[]> m = new HashMap<>(threads.length);
        for (int i = 0; i < threads.length; i++) {
            StackTraceElement[] stackTrace = traces[i];
            if (stackTrace != null) {
                m.put(threads[i], stackTrace);
            }
            // else terminated so we don't put it in the map
        }
        return m;
    }


    private static final RuntimePermission SUBCLASS_IMPLEMENTATION_PERMISSION =
                    new RuntimePermission("enableContextClassLoaderOverride");

    /** cache of subclass security audit results */
    /* Replace with ConcurrentReferenceHashMap when/if it appears in a future
     * release */
    private static class Caches {
        /** cache of subclass security audit results */
        static final ConcurrentMap<WeakClassKey,Boolean> subclassAudits =
            new ConcurrentHashMap<>();

        /** queue for WeakReferences to audited subclasses */
        static final ReferenceQueue<Class<?>> subclassAuditsQueue =
            new ReferenceQueue<>();
    }

    /**
     * Verifies that this (possibly subclass) instance can be constructed
     * without violating security constraints: the subclass must not override
     * security-sensitive non-final methods, or else the
     * "enableContextClassLoaderOverride" RuntimePermission is checked.
     */
    private static boolean isCCLOverridden(Class<?> cl) {
        if (cl == Thread.class)
            return false;

        processQueue(Caches.subclassAuditsQueue, Caches.subclassAudits);
        WeakClassKey key = new WeakClassKey(cl, Caches.subclassAuditsQueue);
        Boolean result = Caches.subclassAudits.get(key);
        if (result == null) {
            result = Boolean.valueOf(auditSubclass(cl));
            Caches.subclassAudits.putIfAbsent(key, result);
        }

        return result.booleanValue();
    }

    /**
     * Performs reflective checks on given subclass to verify that it doesn't
     * override security-sensitive non-final methods.  Returns true if the
     * subclass overrides any of the methods, false otherwise.
     */
    private static boolean auditSubclass(final Class<?> subcl) {
        Boolean result = AccessController.doPrivileged(
            new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    for (Class<?> cl = subcl;
                         cl != Thread.class;
                         cl = cl.getSuperclass())
                    {
                        try {
                            cl.getDeclaredMethod("getContextClassLoader", new Class<?>[0]);
                            return Boolean.TRUE;
                        } catch (NoSuchMethodException ex) {
                        }
                        try {
                            Class<?>[] params = {ClassLoader.class};
                            cl.getDeclaredMethod("setContextClassLoader", params);
                            return Boolean.TRUE;
                        } catch (NoSuchMethodException ex) {
                        }
                    }
                    return Boolean.FALSE;
                }
            }
        );
        return result.booleanValue();
    }

    private native static StackTraceElement[][] dumpThreads(Thread[] threads);
    private native static Thread[] getThreads();

    /**
     *	获取线程id
     */
    public long getId() {
        return tid;
    }

    /**
     * 	线程的状态，线程可以处于以下状态之一
     *   NEW  			尚未启动的线程处于这种状态 
     *   RUNNABLE  		可运行线程的线程状态
     *   BLOCKED  		等待锁而被阻塞的线程处于这种状态
     *   WAITING  		等待状态
     *   TIMED_WAITING  在指定的等待时间内，等待另一个线程执行某个操作的线程处于这种状态。
     *   TERMINATED		已退出的线程处于这种状态。
     * 	 线程在某个时间点只能处于一种状态
     */
    public enum State {
        /**
         * 	尚未启动线程的状态
         */
        NEW,

        /**
         * 	可运行线程的线程状态
         * 	处于可运行状态的线程正在Java虚拟机中执行，但它可能正在等待操作系统中的其他资源，比如处理器
         */
        RUNNABLE,
        /**
         *	 等待锁的阻塞线程的线程状态
         */
        BLOCKED,

        /**
         * 	等待状态
         * 	由于调用以下方法之一，线程处于等待状态:
         * 	Object.wait();
         * 	Thread.join();
         *  LockSupport.park();
         *  
         * 	处于等待状态的线程正在等待另一个线程执行特定的操作。
         *	一个线程调用了Object.wait() 需要等待获取到锁的线程调用Object.notify()或者Object.notifyAll()才会继续执行
         *	一个线程调用了Thread.join()则指定的线程需要等待线程终止才会继续执行
         */
        WAITING,

        /**
         * 	指定等待时间的等待状态
         * 	线程由于调用下列方法之一，并指定了正等待时间，会处于定时等待状态:
         * 	Thread.sleep
         * 	Object.wait  with timeout
         * 	Thread.join  with timeout
         * 	LockSupport.parkNanos
         * 	LockSupport.parkUntil
         */
        TIMED_WAITING,

        /**
         * 	终止状态
         * 	线程已完成执行
         */
        TERMINATED;
    }

    /**
     * 
     * 	获取线程当前的状态
     */
    public State getState() {
        return sun.misc.VM.toThreadState(threadStatus);
    }

    // Added in JSR-166

    /**
     * Interface for handlers invoked when a <tt>Thread</tt> abruptly
     * terminates due to an uncaught exception.
     * <p>When a thread is about to terminate due to an uncaught exception
     * the Java Virtual Machine will query the thread for its
     * <tt>UncaughtExceptionHandler</tt> using
     * {@link #getUncaughtExceptionHandler} and will invoke the handler's
     * <tt>uncaughtException</tt> method, passing the thread and the
     * exception as arguments.
     * If a thread has not had its <tt>UncaughtExceptionHandler</tt>
     * explicitly set, then its <tt>ThreadGroup</tt> object acts as its
     * <tt>UncaughtExceptionHandler</tt>. If the <tt>ThreadGroup</tt> object
     * has no
     * special requirements for dealing with the exception, it can forward
     * the invocation to the {@linkplain #getDefaultUncaughtExceptionHandler
     * default uncaught exception handler}.
     *
     * @see #setDefaultUncaughtExceptionHandler
     * @see #setUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    @FunctionalInterface
    public interface UncaughtExceptionHandler {
        /**
         * Method invoked when the given thread terminates due to the
         * given uncaught exception.
         * <p>Any exception thrown by this method will be ignored by the
         * Java Virtual Machine.
         * @param t the thread
         * @param e the exception
         */
        void uncaughtException(Thread t, Throwable e);
    }

    // null unless explicitly set
    private volatile UncaughtExceptionHandler uncaughtExceptionHandler;

    // null unless explicitly set
    private static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler;

    /**
     * Set the default handler invoked when a thread abruptly terminates
     * due to an uncaught exception, and no other handler has been defined
     * for that thread.
     *
     * <p>Uncaught exception handling is controlled first by the thread, then
     * by the thread's {@link ThreadGroup} object and finally by the default
     * uncaught exception handler. If the thread does not have an explicit
     * uncaught exception handler set, and the thread's thread group
     * (including parent thread groups)  does not specialize its
     * <tt>uncaughtException</tt> method, then the default handler's
     * <tt>uncaughtException</tt> method will be invoked.
     * <p>By setting the default uncaught exception handler, an application
     * can change the way in which uncaught exceptions are handled (such as
     * logging to a specific device, or file) for those threads that would
     * already accept whatever &quot;default&quot; behavior the system
     * provided.
     *
     * <p>Note that the default uncaught exception handler should not usually
     * defer to the thread's <tt>ThreadGroup</tt> object, as that could cause
     * infinite recursion.
     *
     * @param eh the object to use as the default uncaught exception handler.
     * If <tt>null</tt> then there is no default handler.
     *
     * @throws SecurityException if a security manager is present and it
     *         denies <tt>{@link RuntimePermission}
     *         (&quot;setDefaultUncaughtExceptionHandler&quot;)</tt>
     *
     * @see #setUncaughtExceptionHandler
     * @see #getUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(
                new RuntimePermission("setDefaultUncaughtExceptionHandler")
                    );
        }

         defaultUncaughtExceptionHandler = eh;
     }

    /**
     * Returns the default handler invoked when a thread abruptly terminates
     * due to an uncaught exception. If the returned value is <tt>null</tt>,
     * there is no default.
     * @since 1.5
     * @see #setDefaultUncaughtExceptionHandler
     * @return the default uncaught exception handler for all threads
     */
    public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler(){
        return defaultUncaughtExceptionHandler;
    }

    /**
     * Returns the handler invoked when this thread abruptly terminates
     * due to an uncaught exception. If this thread has not had an
     * uncaught exception handler explicitly set then this thread's
     * <tt>ThreadGroup</tt> object is returned, unless this thread
     * has terminated, in which case <tt>null</tt> is returned.
     * @since 1.5
     * @return the uncaught exception handler for this thread
     */
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return uncaughtExceptionHandler != null ?
            uncaughtExceptionHandler : group;
    }

    /**
     * Set the handler invoked when this thread abruptly terminates
     * due to an uncaught exception.
     * <p>A thread can take full control of how it responds to uncaught
     * exceptions by having its uncaught exception handler explicitly set.
     * If no such handler is set then the thread's <tt>ThreadGroup</tt>
     * object acts as its handler.
     * @param eh the object to use as this thread's uncaught exception
     * handler. If <tt>null</tt> then this thread has no explicit handler.
     * @throws  SecurityException  if the current thread is not allowed to
     *          modify this thread.
     * @see #setDefaultUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        checkAccess();
        uncaughtExceptionHandler = eh;
    }

    /**
     * Dispatch an uncaught exception to the handler. This method is
     * intended to be called only by the JVM.
     */
    private void dispatchUncaughtException(Throwable e) {
        getUncaughtExceptionHandler().uncaughtException(this, e);
    }

    /**
     * Removes from the specified map any keys that have been enqueued
     * on the specified reference queue.
     */
    static void processQueue(ReferenceQueue<Class<?>> queue,
                             ConcurrentMap<? extends
                             WeakReference<Class<?>>, ?> map)
    {
        Reference<? extends Class<?>> ref;
        while((ref = queue.poll()) != null) {
            map.remove(ref);
        }
    }

    /**
     *  Weak key for Class objects.
     **/
    static class WeakClassKey extends WeakReference<Class<?>> {
        /**
         * saved value of the referent's identity hash code, to maintain
         * a consistent hash code after the referent has been cleared
         */
        private final int hash;

        /**
         * Create a new WeakClassKey to the given object, registered
         * with a queue.
         */
        WeakClassKey(Class<?> cl, ReferenceQueue<Class<?>> refQueue) {
            super(cl, refQueue);
            hash = System.identityHashCode(cl);
        }

        /**
         * Returns the identity hash code of the original referent.
         */
        @Override
        public int hashCode() {
            return hash;
        }

        /**
         * Returns true if the given object is this identical
         * WeakClassKey instance, or, if this object's referent has not
         * been cleared, if the given object is another WeakClassKey
         * instance with the identical non-null referent as this one.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;

            if (obj instanceof WeakClassKey) {
                Object referent = get();
                return (referent != null) &&
                       (referent == ((WeakClassKey) obj).get());
            } else {
                return false;
            }
        }
    }


    // The following three initially uninitialized fields are exclusively
    // managed by class java.util.concurrent.ThreadLocalRandom. These
    // fields are used to build the high-performance PRNGs in the
    // concurrent code, and we can not risk accidental false sharing.
    // Hence, the fields are isolated with @Contended.

    /** The current seed for a ThreadLocalRandom */
    @sun.misc.Contended("tlr")
    long threadLocalRandomSeed;

    /** Probe hash value; nonzero if threadLocalRandomSeed initialized */
    @sun.misc.Contended("tlr")
    int threadLocalRandomProbe;

    /** Secondary seed isolated from public ThreadLocalRandom sequence */
    @sun.misc.Contended("tlr")
    int threadLocalRandomSecondarySeed;

    /* Some private helper methods */
    private native void setPriority0(int newPriority);
    private native void stop0(Object o);
    private native void suspend0();
    private native void resume0();
    private native void interrupt0();
    private native void setNativeName(String name);
}
