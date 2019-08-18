package java.util;

/**
 * 	LinkedHashSet
 * 	把父类HashSet中的属性map初始化为了LinkedHashMap 可以保证插入或者读取的顺序 
 * 	这里默认是accessOrder以插入的顺序  
 * 	
 */
public class LinkedHashSet<E>
    extends HashSet<E>
    implements Set<E>, Cloneable, java.io.Serializable {

    private static final long serialVersionUID = -2851667679971038690L;

    /**
     *	把父类HashSet中的属性map初始化为了LinkedHashMap
     * @param      initialCapacity 初始化容量
     * @param      loadFactor      加载因子
     */
    public LinkedHashSet(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);
    }

    /**
     *	把父类HashSet中的属性map初始化为了LinkedHashMap
     *	加载因子默认为0.75f
     * @param   initialCapacity  初始化容量
     */
    public LinkedHashSet(int initialCapacity) {
        super(initialCapacity, .75f, true);
    }

    /**
     * 	把父类HashSet中的属性map初始化为了LinkedHashMap
     * 	初始化容量为16  加载因子为0.75
     */
    public LinkedHashSet() {
        super(16, .75f, true);
    }

    /**
     *	先初始化 然后把集合中的数据添加到set中
     * @param c  集合
     */
    public LinkedHashSet(Collection<? extends E> c) {
    	//初始化
        super(Math.max(2*c.size(), 11), .75f, true);
        //添加数据
        addAll(c);
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@code Spliterator} over the elements in this set.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED},
     * {@link Spliterator#DISTINCT}, and {@code ORDERED}.  Implementations
     * should document the reporting of additional characteristic values.
     *
     * @implNote
     * The implementation creates a
     * <em><a href="Spliterator.html#binding">late-binding</a></em> spliterator
     * from the set's {@code Iterator}.  The spliterator inherits the
     * <em>fail-fast</em> properties of the set's iterator.
     * The created {@code Spliterator} additionally reports
     * {@link Spliterator#SUBSIZED}.
     *
     * @return a {@code Spliterator} over the elements in this set
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(this, Spliterator.DISTINCT | Spliterator.ORDERED);
    }
}
