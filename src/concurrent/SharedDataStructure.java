package concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A parameterized thread-safe data structure.
 *
 * @param <T>
 */
public class SharedDataStructure<T> {

    private List<T> data;
    private ReentrantReadWriteLock lock;

    /**
     * SharedDataStructure Constructor.
     */
    public SharedDataStructure() {
        this.data = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Thread-safe add method.
     *
     * @param element
     */
    public void add(T element) {
        this.lock.writeLock().lock();
        this.data.add(element);
        this.lock.writeLock().unlock();
    }

    /**
     * Thread-safe add method base on sequence number.
     * Use for receiving Data packet, and this
     * method makes sure that we add data in order.
     *
     * @param seqNo
     * @param element
     * @return boolean
     *      - success or not
     */
    public boolean addOnSeq(int seqNo, T element) {
        boolean result = false;

        this.lock.writeLock().lock();
        if (seqNo == this.data.size() + 1) {
            this.data.add(element);
            result = true;
        }
        this.lock.writeLock().unlock();

        return result;
    }

    /**
     * Return the current size of the data structure.
     *
     * @return int
     *      - current size
     */
    public int size() {
        this.lock.readLock().lock();
        int size = this.data.size();
        this.lock.readLock().unlock();

        return size;
    }

    /**
     * Deep copy the data list into a new ArrayList to return.
     * Shallow copy the elements since we are storing immutable objects in this data structure.
     *
     * @return List
     *      - a list of objects
     */
    public List<T> get() {
        List<T> data = new ArrayList<>();

        this.lock.readLock().lock();
        data.addAll(this.data);
        this.lock.readLock().unlock();

        return data;
    }

    /**
     * Deep copy the entire new data into this data structure.
     *
     * @param data
     */
    public void replaceAll(List<T> data) {
        this.lock.writeLock().lock();
        this.data = new ArrayList<>();
        this.data.addAll(data);
        this.lock.writeLock().unlock();
    }
}
