package java.util;
/**
 * 栈
 * 先进后出
 * 继承Vector 底层是由数组实现 用synchronized关键字保证线程安全
 * 已不推荐使用
 */
public
class Stack<E> extends Vector<E> {
    
    public Stack() {
    	
    }
    /**
     * 入栈 栈顶入栈
     */
    public E push(E item) {
    	//移除对应下标的数据
    	//添加数据 从后添加
        addElement(item);
        return item;
    }

    /**
     * 出栈  栈顶出栈
     */
    public synchronized E pop() {
        E       obj;
        int     len = size();
        //获取数组
        obj = peek();
        //父类Vector中的方法
        //移除对应下标的数据
        removeElementAt(len - 1);
        return obj;
    }

    /**
     *	获取栈顶数据
     */
    public synchronized E peek() {
        int     len = size();

        if (len == 0)
            throw new EmptyStackException();
        //父类Vector中的方法
        //获取对应下标的值
        return elementAt(len - 1);
    }

    /**
     * 判断栈是否为空
     */
    public boolean empty() {
        return size() == 0;
    }

    /**
     * 在栈中查找o的下标
     */
    public synchronized int search(Object o) {
        int i = lastIndexOf(o);
        if (i >= 0) {
            return size() - i;
        }
        return -1;
    }

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 1224463164541339165L;
}
