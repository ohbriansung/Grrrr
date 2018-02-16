package concurrent;

import chatprotos.ChatProcotol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SharedDataStructure {

    private List<ChatProcotol.Chat> history;
    private ReentrantReadWriteLock lock;

    public SharedDataStructure() {
        this.history = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public void add(ChatProcotol.Chat chat) {
        this.lock.writeLock().lock();
        this.history.add(chat);
        this.lock.writeLock().unlock();
    }

    /**
     * Deep copy the history into a new ArrayList to return.
     *
     * @return List
     *      - a list of chat
     */
    public List<ChatProcotol.Chat> get() {
        List<ChatProcotol.Chat> history = new ArrayList<>();

        this.lock.readLock().lock();
        for (ChatProcotol.Chat chat : this.history) {
            history.add(chat);
        }
        this.lock.readLock().unlock();

        return history;
    }

    public void newHistory(List<ChatProcotol.Chat> history) {
        this.lock.writeLock().lock();
        this.history = new ArrayList<>();
        for (ChatProcotol.Chat chat : history) {
            this.history.add(chat);
        }
        this.lock.writeLock().unlock();
    }
}
