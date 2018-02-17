package concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SharedDataStructure<T> {

    private List<T> data;
    private ReentrantReadWriteLock lock;

    public SharedDataStructure() {
        this.data = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public void add(T element) {
        this.lock.writeLock().lock();
        this.data.add(element);
        this.lock.writeLock().unlock();
    }

    public int size() {
        this.lock.readLock().lock();
        int size = this.data.size();
        this.lock.readLock().unlock();

        return size;
    }

    /**
     * Deep copy the data list into a new ArrayList to return.
     * Shallow copy the elements since we are storing immutable objects in this application.
     *
     * @return List
     *      - a list of chat
     */
    public List<T> get() {
        List<T> data = new ArrayList<>();

        this.lock.readLock().lock();
        data.addAll(this.data);
        this.lock.readLock().unlock();

        return data;
    }

    public void replaceAll(List<T> data) {
        this.lock.writeLock().lock();
        this.data = new ArrayList<>();
        this.data.addAll(data);
        this.lock.writeLock().unlock();
    }
}
