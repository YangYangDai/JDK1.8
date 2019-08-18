package java.util;

import java.util.function.Supplier;

/**
 * 	对象的工具类
 */
public final class Objects {
	//不给实例化
    private Objects() {
        throw new AssertionError("No java.util.Objects instances for you!");
    }

    /**
     * 	判断两个对象是否想相等
     * @param a 对象a
     * @param b 对象b
     * @return true 说明两个对象相等 false 说明不想等
     */
    public static boolean equals(Object a, Object b) {
    	//1.判断两个对象是不是同一个对象 是的话直接返回true
    	//2.先判断a不null 才能调用equals方法判断是不是相等
        return (a == b) || (a != null && a.equals(b));
    }
    
   /**
    *	对象的话就和上面的一样 但如果对象是数组就会确定数据的类型 对比两个数组 要完全相同的
    * @param a an object
    * @param b an object to be compared with {@code a} for deep equality
    * @return  true 说明两个对象相等 false 说明不想等
    */
    public static boolean deepEquals(Object a, Object b) {
        if (a == b)
            return true;
        else if (a == null || b == null)
            return false;
        else
            return Arrays.deepEquals0(a, b);
    }

    /**
     *	获取对象的hashCode
     * @param o an object
     * @return 0 表示o为null 
     */
    public static int hashCode(Object o) {
        return o != null ? o.hashCode() : 0;
    }

   /**
    *	计算数组的hashCode	
    * @return 0 表示o为null 
    */
    public static int hash(Object... values) {
        return Arrays.hashCode(values);
    }

    /**
     *	对象toString
     * @param o an object
     * @return  null的话返回字符串"null" 如果对象自己实现了 就调用自己的 不然就会调用Object-》 类名称 + "@" + 16进制的hashcode
     * @see Object#toString
     * @see String#valueOf(Object)
     */
    public static String toString(Object o) {
        return String.valueOf(o);
    }

    /**
     * 	对象toString
     * @param o an object
     * @param nullDefault 
     * @return null的话返回字符串返回nullDefault 其它的和上面一个方法一样
     * argument if it is not {@code null} and the second argument
     * otherwise.
     * @see Objects#toString(Object)
     */
    public static String toString(Object o, String nullDefault) {
        return (o != null) ? o.toString() : nullDefault;
    }

    /**	
     *	用比较器比较两个对象 
     * @return a<b -1 a=b 0 a>b 1
     */
    public static <T> int compare(T a, T b, Comparator<? super T> c) {
        return (a == b) ? 0 :  c.compare(a, b);
    }

    /**
     *	获取一个非空的对象  null的话 会抛出异常
     */
    public static <T> T requireNonNull(T obj) {
        if (obj == null)
            throw new NullPointerException();
        return obj;
    }

    /**
     * 	获取一个非空的对象  null的话 会抛出异常
     *
     * @param obj     the object reference to check for nullity
     * @param message 可以带上信息message
     * @param <T> the type of the reference
     * @return {@code obj} if not {@code null}
     * @throws NullPointerException if {@code obj} is {@code null}
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null)
            throw new NullPointerException(message);
        return obj;
    }

    /**
     *	判断当前对象是不是空
     *	@return true 对象为null 
     */
    public static boolean isNull(Object obj) {
        return obj == null;
    }

    /**
     *	判断当前对象是不是空不为null
     *	@return true 对象不为null 
     */
    public static boolean nonNull(Object obj) {
        return obj != null;
    }

    /**
     *	获取一个不为null的对象
     *	null的时候会带上messageSupplier里面的信息
     */
    public static <T> T requireNonNull(T obj, Supplier<String> messageSupplier) {
        if (obj == null)
            throw new NullPointerException(messageSupplier.get());
        return obj;
    }
}
