package java.util.concurrent;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import sun.misc.SharedSecrets;

/**
 *  CopyOnWriteArrayList
 * 	array在读操作的时候会被改变 读操作很有可能操作的是旧array
 * 	写操作直接用锁锁住对应的操作 读操作都是不用锁的
 * 	适用于读多写少的场景
 */
public class CopyOnWriteArrayList<E>
    implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 8673264195747942595L;

    /**
     * 	可重入锁
     */
    final transient ReentrantLock lock = new ReentrantLock();

    /**
     * 	对象数组存储数据项
     * 	数组只能通过getArray / setArray访问 保证其原子性
     * 	volatile保证其可见性和顺序性
     */
    private transient volatile Object[] array;

    /**
     * 	获取对象数组 不是private 是为了从CopyOnwriteArraySet类访问 
     * 	CopyOnwriteArraySet 底层是通过CopyOnWriteArrayList实现
     */
    final Object[] getArray() {
        return array;
    }

    /**
     * 	设置对象数组
     */
    final void setArray(Object[] a) {
        array = a;
    }

    /**
     * 	初始化一个长度为0的对象数组
     * 	然后设置给array
     */
    public CopyOnWriteArrayList() {
        setArray(new Object[0]);
    }

    /**
     * 	将集合中的数据放入array中
     * @param c 集合
     */
    public CopyOnWriteArrayList(Collection<? extends E> c) {
        Object[] elements;
        //集合就是CopyOnWriteArrayList对象
        if (c.getClass() == CopyOnWriteArrayList.class)
            elements = ((CopyOnWriteArrayList<?>)c).getArray();
        else {
        	//集合转成数组
            elements = c.toArray();
            //数组的类型如果不是对象数组的话就要转成对象数组
            if (elements.getClass() != Object[].class)
                elements = Arrays.copyOf(elements, elements.length, Object[].class);
        }
        //把对象数组赋给array
        setArray(elements);
    }

    /**
     * 	把E类型的数组copy成对象数组 然后赋给array
     * @param toCopyIn 数组
     */
    public CopyOnWriteArrayList(E[] toCopyIn) {
    	//把E类型的数组copy成对象数组 然后赋给array
        setArray(Arrays.copyOf(toCopyIn, toCopyIn.length, Object[].class));
    }

    /**
     * 	返回集合的大小
     */
    public int size() {
    	//获取到数组的长度
        return getArray().length;
    }

    /**
     * 	判断是不是一个空集合
     *
     * @return true 表示没有任务数据 
     */
    public boolean isEmpty() {
    	//数组的长度等于0说明是空集合
        return size() == 0;
    }

    /**
     * 	判断两个对象是否相等
     */
    private static boolean eq(Object o1, Object o2) {
        return (o1 == null) ? o2 == null : o1.equals(o2);
    }

    /**
     *	 查找对象的位置 从index遍历到fence
     * @param o 查询的对象
     * @param elements 对象数组
     * @param index 开始查找的下标
     * @param fence 结束查找的下标
     * @return  找到就返回对应的下标  没有的就-1
     */
    private static int indexOf(Object o, Object[] elements,
    		int index, int fence) {
    	//o为null的话就直接查找等于null
        if (o == null) {
            for (int i = index; i < fence; i++)
                if (elements[i] == null)
                    return i;
        } else {//用对象的equals判断是否相等
            for (int i = index; i < fence; i++)
                if (o.equals(elements[i]))
                    return i;
        }
        return -1;
    }

    /**
     * 	从index位置开始向前查找
     * @param o 查找的对象
     * @param elements 数组
     * @param index 开始查找的下标
     * @return 找到就返回对应的下标  没有的就-1
     */
    private static int lastIndexOf(Object o, Object[] elements, int index) {
    	//o为null的话就直接查找等于null
    	if (o == null) {
            for (int i = index; i >= 0; i--)
                if (elements[i] == null)
                    return i;
        } else {//用对象的equals判断是否相等
            for (int i = index; i >= 0; i--)
                if (o.equals(elements[i]))
                    return i;
        }
        return -1;
    }

    /**
     *	判断是否包含某个对象
     * @param o 查找的对象
     * @return true 存在 false 不存在
     */
    public boolean contains(Object o) {
    	//指向array
        Object[] elements = getArray();
        //找到就返回对应的下标  没有的就-1  大于等于0说明存在
        return indexOf(o, elements, 0, elements.length) >= 0;
    }

    /**
     * 	找到就返回对应的下标  没有的就-1
     *	查找对象的位置 从头开始遍历
     */
    public int indexOf(Object o) {
    	//指向array
        Object[] elements = getArray();
        		//查找对象的位置 从0遍历到elements.length
        return indexOf(o, elements, 0, elements.length);
    }

    /**
     *	找到就返回对应的下标  没有的就-1
     *	查找对象的位置 从头开始遍历
     * @param e 查找的对象
     * @param index 开始查找的位置
     */
    public int indexOf(E e, int index) {
    	//指向array
        Object[] elements = getArray();
        		//查找对象的位置 从index遍历到elements.length
        return indexOf(e, elements, index, elements.length);
    }

    /**
     * 	找到就返回对应的下标  没有的就-1
     *	 从elements.length - 1位置开始向前查找
     */
    public int lastIndexOf(Object o) {
    	//指向array
        Object[] elements = getArray();
        		// 从elements.length - 1位置开始向前查找
        return lastIndexOf(o, elements, elements.length - 1);
    }

    /**
     *	找到就返回对应的下标  没有的就-1
     *	从index位置开始向前查找
     * @param e 查找的对象
     * @param index 开始查找的位置
     */
    public int lastIndexOf(E e, int index) {
    	//指向array
        Object[] elements = getArray();
        		// 从index位置开始向前查找
        return lastIndexOf(e, elements, index);
    }

    /**
     * Returns a shallow copy of this list.  (The elements themselves
     * are not copied.)
     *
     * @return a clone of this list
     */
    public Object clone() {
        try {
            @SuppressWarnings("unchecked")
            CopyOnWriteArrayList<E> clone =
                (CopyOnWriteArrayList<E>) super.clone();
            clone.resetLock();
            return clone;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError();
        }
    }

    /**
     * copy一个新的对象数组返回
     */
    public Object[] toArray() {
    	//指向array
        Object[] elements = getArray();
        //copy一个新的对象数组返回
        return Arrays.copyOf(elements, elements.length);
    }

    /**
     * 	copy一个新的T类型的对象数组返回
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T a[]) {
        Object[] elements = getArray();
        int len = elements.length;
        if (a.length < len)
            return (T[]) Arrays.copyOf(elements, len, a.getClass());
        else {
            System.arraycopy(elements, 0, a, 0, len);
            if (a.length > len)
                a[len] = null;
            return a;
        }
    }

    /**
     * 	获取对应下标的数据项
     * @param a 对象数组
     * @param index 下标
     */
    private E get(Object[] a, int index) {
        return (E) a[index];
    }

    /**
     * 	获取array中对应下标的数据
     */
    public E get(int index) {
        return get(getArray(), index);
    }

    /**
     * 	用指定的元素替换此列表中指定位置的元素
     * @param index 下标
     * @param element 数据项
     */
    public E set(int index, E element) {
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//指向array
            Object[] elements = getArray();
            //获取到index位置的旧值
            E oldValue = get(elements, index);
            //旧值不等于新值 
            if (oldValue != element) {
            	//获取到数组的长度
                int len = elements.length;
                //copy复制一个新的数组
                Object[] newElements = Arrays.copyOf(elements, len);
                //修改对应下标的值
                newElements[index] = element;
                //然后把array指向新的数组 此时就有可能很多读请求获取的到的是旧array获取到了旧数据
                setArray(newElements);
            } else {
                //不是一定需要的操作;确保易失性写语义
                setArray(elements);
            }
            //返回旧值
            return oldValue;
        } finally {
        	//解锁
            lock.unlock();
        }
    }

    /**
     * 	在尾部添加数据
     * @param e 需要添加的数据
     * @return true 表示添加成功 false 表示添加失败
     */
    public boolean add(E e) {
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//指向array
            Object[] elements = getArray();
            //获取到长度
            int len = elements.length;
            //copy到长度+1的新数组中
            Object[] newElements = Arrays.copyOf(elements, len + 1);
            //在数组尾部添加数据
            newElements[len] = e;
            //然后把array指向新的数组 此时就有可能很多读请求获取的到的是旧array获取到了旧数据
            setArray(newElements);
            return true;
        } finally {
        	//解锁
            lock.unlock();
        }
    }

    /**
     *	在指定的下标处添加数据
     * @param index 下标
     * @param e 数据项
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public void add(int index, E element) {
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//elements指向array
            Object[] elements = getArray();
            //获取到长度
            int len = elements.length;
            //下标大于长度或者小于0 直接抛出异常
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException("Index: "+index+
                                                    ", Size: "+len);
            Object[] newElements;
            //用于判断是否要分开copy到新的数组
            int numMoved = len - index;
            //numMoved说明就是在尾部插入数据
            if (numMoved == 0)
            	//copy到长度+1的新数组中
                newElements = Arrays.copyOf(elements, len + 1);
            else {
            	//新数组的长度+1
                newElements = new Object[len + 1];
                //copy两次
                //先把旧数组的0到index的数据copy到新数组中
                System.arraycopy(elements, 0, newElements, 0, index);
                //然后再把旧数组的index后的numMoved个数据copy到新数组从index + 1下标开始的位置
                System.arraycopy(elements, index, newElements, index + 1,
                                 numMoved);
            }
            //把对应的下标的数据设置为element
            newElements[index] = element;
            //array指向newElements
            setArray(newElements);
        } finally {
        	//释放锁
            lock.unlock();
        }
    }

    /**
     * 	移除指定下标的数据项 并返回该数据项
     *	@param index 下标
     */
    public E remove(int index) {
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//elements指向array
            Object[] elements = getArray();
            //获取数组长度
            int len = elements.length;
            //获取index位置的旧数据
            E oldValue = get(elements, index);
            //用于判断是否要分开copy到新的数组
            int numMoved = len - index - 1;
            //numMoved等于0说明是移除最后一个
            if (numMoved == 0)
            	//copy到长度-1的新数组中 然后array指向新数组
                setArray(Arrays.copyOf(elements, len - 1));
            else {
            	//copy两次
            	//new一个新数组长度减一
                Object[] newElements = new Object[len - 1];
                //先把旧数组的0到index的数据copy到新数组中
                System.arraycopy(elements, 0, newElements, 0, index);
                //然后再把旧数组的index + 1后的numMoved个数据copy到新数组从下标index开始的位置
                System.arraycopy(elements, index + 1, newElements, index,
                                 numMoved);
                //array指向新数组
                setArray(newElements);
            }
            //返回旧值
            return oldValue;
        } finally {
        	//解锁
            lock.unlock();
        }
    }

    /**
     *	移除对象o
     * @param o 对象
     * @return true 移除成功 false 移除失败
     */
    public boolean remove(Object o) {
    	//指向array
        Object[] snapshot = getArray();
        //查找对象的位置 从index遍历到fence
        int index = indexOf(o, snapshot, 0, snapshot.length);
        //查找不到直接返回false  
        return (index < 0) ? false : remove(o, snapshot, index);
    }

    /**
     * @param o 数据对象
     * @param snapshot 可能是过期的对象数组 
     * @param index 对象在snapshot中的下标 
     */
    private boolean remove(Object o, Object[] snapshot, int index) {
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//current指向array
            Object[] current = getArray();
            //获取到数组的长度
            int len = current.length;
            //如果两个不是同一个对象 说明快照过期了
            if (snapshot != current) findIndex: {
            	//获取到较小的值
                int prefix = Math.min(index, len);
                //循环较小的值
                for (int i = 0; i < prefix; i++) {
                	//找到相同位置新旧数组中的数据项不相等且删除对象与数据项相等的下标
                    if (current[i] != snapshot[i] && eq(o, current[i])) {
                        index = i;
                        //找到就直接跳出到findIndex:的位置
                        break findIndex;
                    }
                }
                //查找的index大于等于当前array的长度 直接返回false
                if (index >= len)
                    return false;
                //当前数组下标index的位置对象正好等于o
                if (current[index] == o)
                    break findIndex;
                //获取到对象的真实下标
                index = indexOf(o, current, index, len);
                if (index < 0)
                    return false;
            }
            //新建一个长度减一的数组
            Object[] newElements = new Object[len - 1];
            //分两次copy
            //先把当前数组copy[0,index）的数据到新的数组中
            System.arraycopy(current, 0, newElements, 0, index);
            //再把当前数组copy[index+1,len - 1）的数据到新的数组中
            System.arraycopy(current, index + 1,
                             newElements, index,
                             len - index - 1);
            //把array指向新数组
            setArray(newElements);
            return true;
        } finally {
        	//释放锁
            lock.unlock();
        }
    }

    /**
     * 从该列表中删除索引位于两者之间的所有元素
     * @param fromIndex 开始的下标
     * @param toIndex 结束的下标
     */
    void removeRange(int fromIndex, int toIndex) {
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//elements指向array
            Object[] elements = getArray();
            //获取数组长度
            int len = elements.length;
            //下标校验
            if (fromIndex < 0 || toIndex > len || toIndex < fromIndex)
                throw new IndexOutOfBoundsException();
            //新的数组长度
            int newlen = len - (toIndex - fromIndex);
            //用于判断是否要分开copy到新的数组
            int numMoved = len - toIndex;
            //不用分开copy
            if (numMoved == 0)
            	//直接一次性移动到新的数组就好了
                setArray(Arrays.copyOf(elements, newlen));
            else {
            	//新建数组 长度为newlen
            	//分两次copy
                Object[] newElements = new Object[newlen];
                //先把当前数组copy[0,fromIndex）的数据到新的数组中
                System.arraycopy(elements, 0, newElements, 0, fromIndex);
                //再把当前数组copy[toIndex,toIndex+numMoved）的数据到新的数组中
                System.arraycopy(elements, toIndex, newElements,
                                 fromIndex, numMoved);
                //把array指向新数组
                setArray(newElements);
            }
        } finally {
        	//释放锁
            lock.unlock();
        }
    }

    /**
     * 添加数据项 如果不存在才添加
     * @param e  数据项
     * @return true 添加成功 说明数据项e不存在 添加失败 说明数据项e存在
     */
    public boolean addIfAbsent(E e) {
    	//snapshot指向array
        Object[] snapshot = getArray();
        //先在数组中查找如果找到直接返回false
        //没有找到就添加
        return indexOf(e, snapshot, 0, snapshot.length) >= 0 ? false :
            addIfAbsent(e, snapshot);
    }

    /**
     * 
     */
    private boolean addIfAbsent(E e, Object[] snapshot) {
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//current指向当前最新的数组
            Object[] current = getArray();
            //获取到数组长度
            int len = current.length;
            //说明snapshot已经是旧数据了
            if (snapshot != current) {
                //获取新旧数组较小长度的那个
                int common = Math.min(snapshot.length, len);
                for (int i = 0; i < common; i++)
                	//
                    if (current[i] != snapshot[i] && eq(e, current[i]))
                        return false;
                //在最新数组[common,len)存在数据项e直接返回false
                if (indexOf(e, current, common, len) >= 0)
                        return false;
            }
            //copy到新的数组中
            Object[] newElements = Arrays.copyOf(current, len + 1);
            //尾部指向e
            newElements[len] = e;
            //array指向newElements
            setArray(newElements);
            return true;
        } finally {
        	//释放锁
            lock.unlock();
        }
    }

    /**
     *	判断是否包含集合c中所有的数据项
     * @param c 集合
     * @return true 包含所有 false 至少有一个不包含
     */
    public boolean containsAll(Collection<?> c) {
    	//elements指向array
        Object[] elements = getArray();
        //获取数组长度
        int len = elements.length;
        //表里集合
        for (Object e : c) {
        	//查找对象的位置 返回-1表示没有找到 直接返回false
            if (indexOf(e, elements, 0, len) < 0)
                return false;
        }
        return true;
    }

    /**
     *	移除集合c中存在于数组里的数据项
     * @param c 集合
     * @return true 有删除 false 没有删除
     * @see #remove(Object)
     */
    public boolean removeAll(Collection<?> c) {
    	//集合为null直接抛出异常
        if (c == null) throw new NullPointerException();
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//elements指向array
            Object[] elements = getArray();
            //获取数组长度
            int len = elements.length;
            //数组长度不为0
            if (len != 0) {
            	//用于计算新的数组长度
                int newlen = 0;
                //temp数组包含我们想要保留的那些元素 长度设置为当前array的长度
                Object[] temp = new Object[len];
                //遍历array
                for (int i = 0; i < len; ++i) {
                    Object element = elements[i];
                    //集合中不包含此数据项
                    if (!c.contains(element))
                    	//就像newlen下标指向element
                    	//然后newlen+1
                        temp[newlen++] = element;
                }
                //array的数组长度不等于newlen
                if (newlen != len) {
                	//copy到数组长度为newlen的新数组中
                	//然后array指向新数组
                    setArray(Arrays.copyOf(temp, newlen));
                    return true;
                }
            }
            return false;
        } finally {
        	//释放锁
            lock.unlock();
        }
    }

    /**
     *	保留集合c中存在于数组里的数据项
     * @param c 集合
     * @return true 有存在 false 没有存在
     */
    public boolean retainAll(Collection<?> c) {
    	//集合为null直接抛出异常
        if (c == null) throw new NullPointerException();
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (len != 0) {
            	//temp数组包含我们想要保留的那些元素 长度设置为当前array的长度
            	//用于计算新的数组长度
                int newlen = 0;
                //遍历array
                Object[] temp = new Object[len];
                for (int i = 0; i < len; ++i) {
                    Object element = elements[i];
                    //集合中包含此数据项
                    if (c.contains(element))
                        temp[newlen++] = element;
                }
                //array的数组长度不等于newlen
                if (newlen != len) {
                	//copy到数组长度为newlen的新数组中
                	//然后array指向新数组
                    setArray(Arrays.copyOf(temp, newlen));
                    return true;
                }
            }
            return false;
        } finally {
        	//释放锁
            lock.unlock();
        }
    }

    /**
     * Appends all of the elements in the specified collection that
     * are not already contained in this list, to the end of
     * this list, in the order that they are returned by the
     * specified collection's iterator.
     *
     * @param c 集合
     * @return 添加的数据项个数
     */
    public int addAllAbsent(Collection<? extends E> c) {
    	//集合转成数组
        Object[] cs = c.toArray();
        //没有数据直接返回0
        if (cs.length == 0)
            return 0;
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//指向array
            Object[] elements = getArray();
            //获取数组长度
            int len = elements.length;
            //初始化添加的数据项
            int added = 0;
            //遍历cs数组
            for (int i = 0; i < cs.length; ++i) {
                Object e = cs[i];
                //array[0, len)没有找到该数据项且cs[ 0, added)也没有找到此数据项
                if (indexOf(e, elements, 0, len) < 0 &&
                    indexOf(e, cs, 0, added) < 0)
                	//将cs数组下标为added指向e
                	//added++
                    cs[added++] = e;
            }
            //说明有要添加的数据
            if (added > 0) {
            	//将arraycopy到新的数组中长度为len + added
                Object[] newElements = Arrays.copyOf(elements, len + added);
                //然后将cs中的[0,added)的数据copy到newElements中
                System.arraycopy(cs, 0, newElements, len, added);
                //array指向newElements
                setArray(newElements);
            }
            //返回要添加数据项的数量
            return added;
        } finally {
        	//释放锁
            lock.unlock();
        }
    }

    /**
     * 移除所有的数据项
     */
    public void clear() {
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//array指向一个空数组
            setArray(new Object[0]);
        } finally {
        	//释放锁
            lock.unlock();
        }
    }

    /**
     * Appends all of the elements in the specified collection to the end
     * of this list, in the order that they are returned by the specified
     * collection's iterator.
     *
     * @param c 集合
     * @return true 数组改变 false 数组没有改变
     */
    public boolean addAll(Collection<? extends E> c) {
    	//看看是不是CopyOnWriteArrayList 是的话可以直接获取到array 否则调用toArray
        Object[] cs = (c.getClass() == CopyOnWriteArrayList.class) ?
            ((CopyOnWriteArrayList<?>)c).getArray() : c.toArray();
        //数组长度为0直接返回false
        if (cs.length == 0)
            return false;
        final ReentrantLock lock = this.lock;
        //锁住
        lock.lock();
        try {
        	//指向array
            Object[] elements = getArray();
            //获取到数组长度
            int len = elements.length;
            if (len == 0 && cs.getClass() == Object[].class)
                setArray(cs);
            else {
                Object[] newElements = Arrays.copyOf(elements, len + cs.length);
                System.arraycopy(cs, 0, newElements, len, cs.length);
                setArray(newElements);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts all of the elements in the specified collection into this
     * list, starting at the specified position.  Shifts the element
     * currently at that position (if any) and any subsequent elements to
     * the right (increases their indices).  The new elements will appear
     * in this list in the order that they are returned by the
     * specified collection's iterator.
     *
     * @param index index at which to insert the first element
     *        from the specified collection
     * @param c collection containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws NullPointerException if the specified collection is null
     * @see #add(int,Object)
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        Object[] cs = c.toArray();
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException("Index: "+index+
                                                    ", Size: "+len);
            if (cs.length == 0)
                return false;
            int numMoved = len - index;
            Object[] newElements;
            if (numMoved == 0)
                newElements = Arrays.copyOf(elements, len + cs.length);
            else {
                newElements = new Object[len + cs.length];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index,
                                 newElements, index + cs.length,
                                 numMoved);
            }
            System.arraycopy(cs, 0, newElements, index, cs.length);
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void forEach(Consumer<? super E> action) {
        if (action == null) throw new NullPointerException();
        Object[] elements = getArray();
        int len = elements.length;
        for (int i = 0; i < len; ++i) {
            @SuppressWarnings("unchecked") E e = (E) elements[i];
            action.accept(e);
        }
    }

    public boolean removeIf(Predicate<? super E> filter) {
        if (filter == null) throw new NullPointerException();
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (len != 0) {
                int newlen = 0;
                Object[] temp = new Object[len];
                for (int i = 0; i < len; ++i) {
                    @SuppressWarnings("unchecked") E e = (E) elements[i];
                    if (!filter.test(e))
                        temp[newlen++] = e;
                }
                if (newlen != len) {
                    setArray(Arrays.copyOf(temp, newlen));
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void replaceAll(UnaryOperator<E> operator) {
        if (operator == null) throw new NullPointerException();
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            Object[] newElements = Arrays.copyOf(elements, len);
            for (int i = 0; i < len; ++i) {
                @SuppressWarnings("unchecked") E e = (E) elements[i];
                newElements[i] = operator.apply(e);
            }
            setArray(newElements);
        } finally {
            lock.unlock();
        }
    }

    public void sort(Comparator<? super E> c) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            Object[] newElements = Arrays.copyOf(elements, elements.length);
            @SuppressWarnings("unchecked") E[] es = (E[])newElements;
            Arrays.sort(es, c);
            setArray(newElements);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Saves this list to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData The length of the array backing the list is emitted
     *               (int), followed by all of its elements (each an Object)
     *               in the proper order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        s.defaultWriteObject();

        Object[] elements = getArray();
        // Write out array length
        s.writeInt(elements.length);

        // Write out all elements in the proper order.
        for (Object element : elements)
            s.writeObject(element);
    }

    /**
     * Reconstitutes this list from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {

        s.defaultReadObject();

        // bind to new lock
        resetLock();

        // Read in array length and allocate array
        int len = s.readInt();
        SharedSecrets.getJavaOISAccess().checkArray(s, Object[].class, len);
        Object[] elements = new Object[len];

        // Read in all elements in the proper order.
        for (int i = 0; i < len; i++)
            elements[i] = s.readObject();
        setArray(elements);
    }

    /**
     * Returns a string representation of this list.  The string
     * representation consists of the string representations of the list's
     * elements in the order they are returned by its iterator, enclosed in
     * square brackets ({@code "[]"}).  Adjacent elements are separated by
     * the characters {@code ", "} (comma and space).  Elements are
     * converted to strings as by {@link String#valueOf(Object)}.
     *
     * @return a string representation of this list
     */
    public String toString() {
        return Arrays.toString(getArray());
    }

    /**
     * Compares the specified object with this list for equality.
     * Returns {@code true} if the specified object is the same object
     * as this object, or if it is also a {@link List} and the sequence
     * of elements returned by an {@linkplain List#iterator() iterator}
     * over the specified list is the same as the sequence returned by
     * an iterator over this list.  The two sequences are considered to
     * be the same if they have the same length and corresponding
     * elements at the same position in the sequence are <em>equal</em>.
     * Two elements {@code e1} and {@code e2} are considered
     * <em>equal</em> if {@code (e1==null ? e2==null : e1.equals(e2))}.
     *
     * @param o the object to be compared for equality with this list
     * @return {@code true} if the specified object is equal to this list
     */
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof List))
            return false;

        List<?> list = (List<?>)(o);
        Iterator<?> it = list.iterator();
        Object[] elements = getArray();
        int len = elements.length;
        for (int i = 0; i < len; ++i)
            if (!it.hasNext() || !eq(elements[i], it.next()))
                return false;
        if (it.hasNext())
            return false;
        return true;
    }

    /**
     * Returns the hash code value for this list.
     *
     * <p>This implementation uses the definition in {@link List#hashCode}.
     *
     * @return the hash code value for this list
     */
    public int hashCode() {
        int hashCode = 1;
        Object[] elements = getArray();
        int len = elements.length;
        for (int i = 0; i < len; ++i) {
            Object obj = elements[i];
            hashCode = 31*hashCode + (obj==null ? 0 : obj.hashCode());
        }
        return hashCode;
    }

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     *
     * <p>The returned iterator provides a snapshot of the state of the list
     * when the iterator was constructed. No synchronization is needed while
     * traversing the iterator. The iterator does <em>NOT</em> support the
     * {@code remove} method.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    public Iterator<E> iterator() {
        return new COWIterator<E>(getArray(), 0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned iterator provides a snapshot of the state of the list
     * when the iterator was constructed. No synchronization is needed while
     * traversing the iterator. The iterator does <em>NOT</em> support the
     * {@code remove}, {@code set} or {@code add} methods.
     */
    public ListIterator<E> listIterator() {
        return new COWIterator<E>(getArray(), 0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned iterator provides a snapshot of the state of the list
     * when the iterator was constructed. No synchronization is needed while
     * traversing the iterator. The iterator does <em>NOT</em> support the
     * {@code remove}, {@code set} or {@code add} methods.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public ListIterator<E> listIterator(int index) {
        Object[] elements = getArray();
        int len = elements.length;
        if (index < 0 || index > len)
            throw new IndexOutOfBoundsException("Index: "+index);

        return new COWIterator<E>(elements, index);
    }

    /**
     * Returns a {@link Spliterator} over the elements in this list.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#IMMUTABLE},
     * {@link Spliterator#ORDERED}, {@link Spliterator#SIZED}, and
     * {@link Spliterator#SUBSIZED}.
     *
     * <p>The spliterator provides a snapshot of the state of the list
     * when the spliterator was constructed. No synchronization is needed while
     * operating on the spliterator.
     *
     * @return a {@code Spliterator} over the elements in this list
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator
            (getArray(), Spliterator.IMMUTABLE | Spliterator.ORDERED);
    }

    static final class COWIterator<E> implements ListIterator<E> {
        /** Snapshot of the array */
        private final Object[] snapshot;
        /** Index of element to be returned by subsequent call to next.  */
        private int cursor;

        private COWIterator(Object[] elements, int initialCursor) {
            cursor = initialCursor;
            snapshot = elements;
        }

        public boolean hasNext() {
            return cursor < snapshot.length;
        }

        public boolean hasPrevious() {
            return cursor > 0;
        }

        @SuppressWarnings("unchecked")
        public E next() {
            if (! hasNext())
                throw new NoSuchElementException();
            return (E) snapshot[cursor++];
        }

        @SuppressWarnings("unchecked")
        public E previous() {
            if (! hasPrevious())
                throw new NoSuchElementException();
            return (E) snapshot[--cursor];
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor-1;
        }

        /**
         * Not supported. Always throws UnsupportedOperationException.
         * @throws UnsupportedOperationException always; {@code remove}
         *         is not supported by this iterator.
         */
        public void remove() {
            throw new UnsupportedOperationException();
        }

        /**
         * Not supported. Always throws UnsupportedOperationException.
         * @throws UnsupportedOperationException always; {@code set}
         *         is not supported by this iterator.
         */
        public void set(E e) {
            throw new UnsupportedOperationException();
        }

        /**
         * Not supported. Always throws UnsupportedOperationException.
         * @throws UnsupportedOperationException always; {@code add}
         *         is not supported by this iterator.
         */
        public void add(E e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            Object[] elements = snapshot;
            final int size = elements.length;
            for (int i = cursor; i < size; i++) {
                @SuppressWarnings("unchecked") E e = (E) elements[i];
                action.accept(e);
            }
            cursor = size;
        }
    }

    /**
     * Returns a view of the portion of this list between
     * {@code fromIndex}, inclusive, and {@code toIndex}, exclusive.
     * The returned list is backed by this list, so changes in the
     * returned list are reflected in this list.
     *
     * <p>The semantics of the list returned by this method become
     * undefined if the backing list (i.e., this list) is modified in
     * any way other than via the returned list.
     *
     * @param fromIndex low endpoint (inclusive) of the subList
     * @param toIndex high endpoint (exclusive) of the subList
     * @return a view of the specified range within this list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public List<E> subList(int fromIndex, int toIndex) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (fromIndex < 0 || toIndex > len || fromIndex > toIndex)
                throw new IndexOutOfBoundsException();
            return new COWSubList<E>(this, fromIndex, toIndex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sublist for CopyOnWriteArrayList.
     * This class extends AbstractList merely for convenience, to
     * avoid having to define addAll, etc. This doesn't hurt, but
     * is wasteful.  This class does not need or use modCount
     * mechanics in AbstractList, but does need to check for
     * concurrent modification using similar mechanics.  On each
     * operation, the array that we expect the backing list to use
     * is checked and updated.  Since we do this for all of the
     * base operations invoked by those defined in AbstractList,
     * all is well.  While inefficient, this is not worth
     * improving.  The kinds of list operations inherited from
     * AbstractList are already so slow on COW sublists that
     * adding a bit more space/time doesn't seem even noticeable.
     */
    private static class COWSubList<E>
        extends AbstractList<E>
        implements RandomAccess
    {
        private final CopyOnWriteArrayList<E> l;
        private final int offset;
        private int size;
        private Object[] expectedArray;

        // only call this holding l's lock
        COWSubList(CopyOnWriteArrayList<E> list,
                   int fromIndex, int toIndex) {
            l = list;
            expectedArray = l.getArray();
            offset = fromIndex;
            size = toIndex - fromIndex;
        }

        // only call this holding l's lock
        private void checkForComodification() {
            if (l.getArray() != expectedArray)
                throw new ConcurrentModificationException();
        }

        // only call this holding l's lock
        private void rangeCheck(int index) {
            if (index < 0 || index >= size)
                throw new IndexOutOfBoundsException("Index: "+index+
                                                    ",Size: "+size);
        }

        public E set(int index, E element) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                rangeCheck(index);
                checkForComodification();
                E x = l.set(index+offset, element);
                expectedArray = l.getArray();
                return x;
            } finally {
                lock.unlock();
            }
        }

        public E get(int index) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                rangeCheck(index);
                checkForComodification();
                return l.get(index+offset);
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                return size;
            } finally {
                lock.unlock();
            }
        }

        public void add(int index, E element) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                if (index < 0 || index > size)
                    throw new IndexOutOfBoundsException();
                l.add(index+offset, element);
                expectedArray = l.getArray();
                size++;
            } finally {
                lock.unlock();
            }
        }

        public void clear() {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                l.removeRange(offset, offset+size);
                expectedArray = l.getArray();
                size = 0;
            } finally {
                lock.unlock();
            }
        }

        public E remove(int index) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                rangeCheck(index);
                checkForComodification();
                E result = l.remove(index+offset);
                expectedArray = l.getArray();
                size--;
                return result;
            } finally {
                lock.unlock();
            }
        }

        public boolean remove(Object o) {
            int index = indexOf(o);
            if (index == -1)
                return false;
            remove(index);
            return true;
        }

        public Iterator<E> iterator() {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                return new COWSubListIterator<E>(l, 0, offset, size);
            } finally {
                lock.unlock();
            }
        }

        public ListIterator<E> listIterator(int index) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                if (index < 0 || index > size)
                    throw new IndexOutOfBoundsException("Index: "+index+
                                                        ", Size: "+size);
                return new COWSubListIterator<E>(l, index, offset, size);
            } finally {
                lock.unlock();
            }
        }

        public List<E> subList(int fromIndex, int toIndex) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                if (fromIndex < 0 || toIndex > size || fromIndex > toIndex)
                    throw new IndexOutOfBoundsException();
                return new COWSubList<E>(l, fromIndex + offset,
                                         toIndex + offset);
            } finally {
                lock.unlock();
            }
        }

        public void forEach(Consumer<? super E> action) {
            if (action == null) throw new NullPointerException();
            int lo = offset;
            int hi = offset + size;
            Object[] a = expectedArray;
            if (l.getArray() != a)
                throw new ConcurrentModificationException();
            if (lo < 0 || hi > a.length)
                throw new IndexOutOfBoundsException();
            for (int i = lo; i < hi; ++i) {
                @SuppressWarnings("unchecked") E e = (E) a[i];
                action.accept(e);
            }
        }

        public void replaceAll(UnaryOperator<E> operator) {
            if (operator == null) throw new NullPointerException();
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                int lo = offset;
                int hi = offset + size;
                Object[] elements = expectedArray;
                if (l.getArray() != elements)
                    throw new ConcurrentModificationException();
                int len = elements.length;
                if (lo < 0 || hi > len)
                    throw new IndexOutOfBoundsException();
                Object[] newElements = Arrays.copyOf(elements, len);
                for (int i = lo; i < hi; ++i) {
                    @SuppressWarnings("unchecked") E e = (E) elements[i];
                    newElements[i] = operator.apply(e);
                }
                l.setArray(expectedArray = newElements);
            } finally {
                lock.unlock();
            }
        }

        public void sort(Comparator<? super E> c) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                int lo = offset;
                int hi = offset + size;
                Object[] elements = expectedArray;
                if (l.getArray() != elements)
                    throw new ConcurrentModificationException();
                int len = elements.length;
                if (lo < 0 || hi > len)
                    throw new IndexOutOfBoundsException();
                Object[] newElements = Arrays.copyOf(elements, len);
                @SuppressWarnings("unchecked") E[] es = (E[])newElements;
                Arrays.sort(es, lo, hi, c);
                l.setArray(expectedArray = newElements);
            } finally {
                lock.unlock();
            }
        }

        public boolean removeAll(Collection<?> c) {
            if (c == null) throw new NullPointerException();
            boolean removed = false;
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                int n = size;
                if (n > 0) {
                    int lo = offset;
                    int hi = offset + n;
                    Object[] elements = expectedArray;
                    if (l.getArray() != elements)
                        throw new ConcurrentModificationException();
                    int len = elements.length;
                    if (lo < 0 || hi > len)
                        throw new IndexOutOfBoundsException();
                    int newSize = 0;
                    Object[] temp = new Object[n];
                    for (int i = lo; i < hi; ++i) {
                        Object element = elements[i];
                        if (!c.contains(element))
                            temp[newSize++] = element;
                    }
                    if (newSize != n) {
                        Object[] newElements = new Object[len - n + newSize];
                        System.arraycopy(elements, 0, newElements, 0, lo);
                        System.arraycopy(temp, 0, newElements, lo, newSize);
                        System.arraycopy(elements, hi, newElements,
                                         lo + newSize, len - hi);
                        size = newSize;
                        removed = true;
                        l.setArray(expectedArray = newElements);
                    }
                }
            } finally {
                lock.unlock();
            }
            return removed;
        }

        public boolean retainAll(Collection<?> c) {
            if (c == null) throw new NullPointerException();
            boolean removed = false;
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                int n = size;
                if (n > 0) {
                    int lo = offset;
                    int hi = offset + n;
                    Object[] elements = expectedArray;
                    if (l.getArray() != elements)
                        throw new ConcurrentModificationException();
                    int len = elements.length;
                    if (lo < 0 || hi > len)
                        throw new IndexOutOfBoundsException();
                    int newSize = 0;
                    Object[] temp = new Object[n];
                    for (int i = lo; i < hi; ++i) {
                        Object element = elements[i];
                        if (c.contains(element))
                            temp[newSize++] = element;
                    }
                    if (newSize != n) {
                        Object[] newElements = new Object[len - n + newSize];
                        System.arraycopy(elements, 0, newElements, 0, lo);
                        System.arraycopy(temp, 0, newElements, lo, newSize);
                        System.arraycopy(elements, hi, newElements,
                                         lo + newSize, len - hi);
                        size = newSize;
                        removed = true;
                        l.setArray(expectedArray = newElements);
                    }
                }
            } finally {
                lock.unlock();
            }
            return removed;
        }

        public boolean removeIf(Predicate<? super E> filter) {
            if (filter == null) throw new NullPointerException();
            boolean removed = false;
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                int n = size;
                if (n > 0) {
                    int lo = offset;
                    int hi = offset + n;
                    Object[] elements = expectedArray;
                    if (l.getArray() != elements)
                        throw new ConcurrentModificationException();
                    int len = elements.length;
                    if (lo < 0 || hi > len)
                        throw new IndexOutOfBoundsException();
                    int newSize = 0;
                    Object[] temp = new Object[n];
                    for (int i = lo; i < hi; ++i) {
                        @SuppressWarnings("unchecked") E e = (E) elements[i];
                        if (!filter.test(e))
                            temp[newSize++] = e;
                    }
                    if (newSize != n) {
                        Object[] newElements = new Object[len - n + newSize];
                        System.arraycopy(elements, 0, newElements, 0, lo);
                        System.arraycopy(temp, 0, newElements, lo, newSize);
                        System.arraycopy(elements, hi, newElements,
                                         lo + newSize, len - hi);
                        size = newSize;
                        removed = true;
                        l.setArray(expectedArray = newElements);
                    }
                }
            } finally {
                lock.unlock();
            }
            return removed;
        }

        public Spliterator<E> spliterator() {
            int lo = offset;
            int hi = offset + size;
            Object[] a = expectedArray;
            if (l.getArray() != a)
                throw new ConcurrentModificationException();
            if (lo < 0 || hi > a.length)
                throw new IndexOutOfBoundsException();
            return Spliterators.spliterator
                (a, lo, hi, Spliterator.IMMUTABLE | Spliterator.ORDERED);
        }

    }

    private static class COWSubListIterator<E> implements ListIterator<E> {
        private final ListIterator<E> it;
        private final int offset;
        private final int size;

        COWSubListIterator(List<E> l, int index, int offset, int size) {
            this.offset = offset;
            this.size = size;
            it = l.listIterator(index+offset);
        }

        public boolean hasNext() {
            return nextIndex() < size;
        }

        public E next() {
            if (hasNext())
                return it.next();
            else
                throw new NoSuchElementException();
        }

        public boolean hasPrevious() {
            return previousIndex() >= 0;
        }

        public E previous() {
            if (hasPrevious())
                return it.previous();
            else
                throw new NoSuchElementException();
        }

        public int nextIndex() {
            return it.nextIndex() - offset;
        }

        public int previousIndex() {
            return it.previousIndex() - offset;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void set(E e) {
            throw new UnsupportedOperationException();
        }

        public void add(E e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            int s = size;
            ListIterator<E> i = it;
            while (nextIndex() < s) {
                action.accept(i.next());
            }
        }
    }

    // Support for resetting lock while deserializing
    private void resetLock() {
        UNSAFE.putObjectVolatile(this, lockOffset, new ReentrantLock());
    }
    private static final sun.misc.Unsafe UNSAFE;
    private static final long lockOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = CopyOnWriteArrayList.class;
            lockOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("lock"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
