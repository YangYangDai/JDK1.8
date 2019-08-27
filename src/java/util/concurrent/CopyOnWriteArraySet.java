package java.util.concurrent;
import java.util.Collection;
import java.util.Set;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.function.Consumer;

/**
 * 	CopyOnWriteArraySet
 * 	Set:数据项不允许重复的
 *	具体实现由内部属性CopyOnWriteArrayList完成
 *	array在读操作的时候会被改变 读操作操作的是旧array
 * 	写操作直接用锁锁住对应的操作，不会去直接改变之前的array，而是操作新的数组，所以读操作都是不用锁的，因为读的是旧数据
 * 	适用于读多写少的场景
 */
public class CopyOnWriteArraySet<E> extends AbstractSet<E>
        implements java.io.Serializable {
    private static final long serialVersionUID = 5457747651344034263L;
    /**
     *	具体的实现都是由CopyOnWriteArrayList完成
     */
    private final CopyOnWriteArrayList<E> al;

    /**
     * 	初始属性CopyOnWriteArrayList
     */
    public CopyOnWriteArraySet() {
        al = new CopyOnWriteArrayList<E>();
    }

    /**
     * 	将集合中的数据放入array中
     * @param c 集合
     */
    public CopyOnWriteArraySet(Collection<? extends E> c) {
    	//集合属于CopyOnWriteArraySet
        if (c.getClass() == CopyOnWriteArraySet.class) {
        	//先强转
            @SuppressWarnings("unchecked") CopyOnWriteArraySet<E> cc =
                (CopyOnWriteArraySet<E>)c;
            //初始属性CopyOnWriteArrayList 传入CopyOnWriteArraySet中的al
            al = new CopyOnWriteArrayList<E>(cc.al);
        }
        else {
        	//初始属性CopyOnWriteArrayList
            al = new CopyOnWriteArrayList<E>();
            //添加没有存在于数组中的集合数据项
            al.addAllAbsent(c);
        }
    }

    /**
     * 	返回set的大小
     *
     * @return the number of elements in this set
     */
    public int size() {
    	//调用CopyOnWriteArrayList中的方法
        return al.size();
    }

    /**
     *	判断集合是否为null
     *@param true set的为空 false 不为空
     */
    public boolean isEmpty() {
    	//调用CopyOnWriteArrayList中的方法
        return al.isEmpty();
    }

    /**
     * 	判断对象是否存在set中
     * @param o 对象
     * @return true 存在  false 不存在
     */
    public boolean contains(Object o) {
    	//调用CopyOnWriteArrayList中的方法
        return al.contains(o);
    }

    /**
     *	获取对象数组
     */
    public Object[] toArray() {
    	//调用CopyOnWriteArrayList中的方法
        return al.toArray();
    }

    /**
     *	获取T类型的数组
     * @param a 类型数组
     */
    public <T> T[] toArray(T[] a) {
    	//调用CopyOnWriteArrayList中的方法
        return al.toArray(a);
    }

    /**
     * 	清空所有数据项
     */
    public void clear() {
    	//调用CopyOnWriteArrayList中的方法
        al.clear();
    }

    /**
     *	移除数据项o
     * @param o 移除对象
     * @return true 移除成功 false 未发生移除
     */
    public boolean remove(Object o) {
    	//调用CopyOnWriteArrayList中的方法
        return al.remove(o);
    }

    /**
     *	添加数据  如果数据不存在才添加 保证数据项的唯一
     * @param e 数据项
     * @return true 不存在且添加成功 false 数据项已存在
     */
    public boolean add(E e) {
    	//调用CopyOnWriteArrayList中的方法
        return al.addIfAbsent(e);
    }

    /**
     *	判断是否包含集合中的所有数据项
     * @param  c 集合
     * @return true 包含所有  false 至少有一个不包含
     */
    public boolean containsAll(Collection<?> c) {
    	//调用CopyOnWriteArrayList中的方法
        return al.containsAll(c);
    }

    /**
     *	添加集合中的数据项  数据项之前不存在与set中
     * @param  c 集合
     * @return true 至少添加了一个数据项 false 没有添加数据项
     */
    public boolean addAll(Collection<? extends E> c) {
    	//调用CopyOnWriteArrayList中的方法
        return al.addAllAbsent(c) > 0;
    }

    /**
     *	移除集合c中存在于set中的数据项
     * @param  c 集合
     * @return true 至少移除了一个数据项 false 没有移除数据项
     * @see #remove(Object)
     */
    public boolean removeAll(Collection<?> c) {
    	//调用CopyOnWriteArrayList中的方法
        return al.removeAll(c);
    }

    /**
     *	移除集合c中不存在于set中的数据项
     * @param  c 集合
     * @return true 至少移除了一个数据项 false 没有移除数据项
     * @see #remove(Object)
     */
    public boolean retainAll(Collection<?> c) {
    	//调用CopyOnWriteArrayList中的方法
        return al.retainAll(c);
    }

    /**
     *	获取Iterator
     */
    public Iterator<E> iterator() {
    	//调用CopyOnWriteArrayList中的方法
        return al.iterator();
    }

    /**
     *	判断是否相等
     * @param o 
     * @return {@code true} if the specified object is equal to this set
     */
    public boolean equals(Object o) {
    	//内存地址相等 直接返回true
        if (o == this)
            return true;
        //o不属于set直接返回
        if (!(o instanceof Set))
            return false;
        Set<?> set = (Set<?>)(o);
        Iterator<?> it = set.iterator();
        
        Object[] elements = al.getArray();
        int len = elements.length;
        // Mark matched elements to avoid re-checking
        boolean[] matched = new boolean[len];
        int k = 0;
        //判断两个数组是数据项是否完全相等
        outer: while (it.hasNext()) {
            if (++k > len)
                return false;
            Object x = it.next();
            for (int i = 0; i < len; ++i) {
                if (!matched[i] && eq(x, elements[i])) {
                    matched[i] = true;
                    continue outer;
                }
            }
            return false;
        }
        return k == len;
    }

    public boolean removeIf(Predicate<? super E> filter) {
        return al.removeIf(filter);
    }

    public void forEach(Consumer<? super E> action) {
        al.forEach(action);
    }

    /**
     *	spliterator
     */
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator
            (al.getArray(), Spliterator.IMMUTABLE | Spliterator.DISTINCT);
    }

    /**
     * 	判断对象是否相等
     */
    private static boolean eq(Object o1, Object o2) {
        return (o1 == null) ? o2 == null : o1.equals(o2);
    }
}
