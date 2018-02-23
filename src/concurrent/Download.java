package concurrent;

import chatprotos.ChatProcotol;
import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe data structure to support UDP data sender.
 */
public class Download {

    private final ChatProcotol.Data.packetType type;
    private final int windowSize;
    private final List<ChatProcotol.Data> dataPackets;
    private List<ChatProcotol.Chat> data;
    private byte[] bytes;
    private Thread currentThread;
    private ReentrantReadWriteLock lock;
    private int state;
    private volatile boolean waked;

    /**
     * Download Constructor for storing current history data.
     *
     * @param data
     * @param windowSize
     */
    public Download(List<ChatProcotol.Chat> data, int windowSize) {
        this.type = ChatProcotol.Data.packetType.DATA;
        this.windowSize = windowSize;
        this.dataPackets = new ArrayList<>();
        this.data = data;

        this.lock = new ReentrantReadWriteLock();
        this.state = 1;
        this.waked = false;

        convertIntoBytes();
        packIntoTenBytes();
    }

    /**
     * Convert all Chat messages into a History data and store it into byte array.
     */
    private void convertIntoBytes() {
        this.bytes = ChatProcotol.History.newBuilder().addAllHistory(this.data).build().toByteArray();
    }

    /**
     * Pack every ten bytes into a Data packet and store into ArrayList for sending purpose.
     */
    private void packIntoTenBytes() {
        int len = this.bytes.length;

        for (int i = 0, j = 10; i * 10 < len; i++) {
            if (i * 10 + j >= len) {
                j = len - i * 10;
            }

            ByteString tenBytes = ByteString.copyFrom(this.bytes, i * 10, j);
            ChatProcotol.Data packet = ChatProcotol.Data.newBuilder().setType(this.type)
                    .setData(tenBytes).setSeqNo(i + 1).setIsLast(i * 10 + j == len).build();

            this.dataPackets.add(packet);
        }
    }

    /**
     * Store the reference of the thread which is sending data and waiting.
     * So we can wake it when receiving an acknowledgement.
     *
     * @param currentThread
     */
    public void setThread(Thread currentThread) {
        this.currentThread = currentThread;
    }

    /**
     * Return the reference of current thread.
     *
     * @return Thread
     *      - a reference of a thread
     */
    public Thread getThread() {
        return this.currentThread;
    }

    /**
     * Return the list of Data packets for sending purpose.
     *
     * @return List
     *      - the list of Data packets
     */
    public List<ChatProcotol.Data> get() {
        return this.dataPackets;
    }

    /**
     * Return the window size for Go-Back-N algorithm.
     *
     * @return int
     */
    public int getWindowSize() {
        return this.windowSize;
    }

    /**
     * Return the current state of sending approach.
     *
     * @return int
     *      - a state number
     */
    public int currentState() {
        this.lock.readLock().lock();
        int state = this.state;
        this.lock.readLock().unlock();

        return state;
    }

    /**
     * Change the current state of sending approach.
     *
     * @param state
     */
    public void changeState(int state) {
        this.lock.writeLock().lock();
        if (state > this.state) {
            this.state = state;
        }
        this.lock.writeLock().unlock();
    }

    /**
     * Check if the thread was waked or timeout.
     *
     * @return boolean
     */
    public boolean isWaked() {
        return this.waked;
    }

    /**
     * Set waked condition back to false.
     */
    public void resetWake() {
        this.waked = false;
    }

    /**
     * Set waked condition.
     */
    public void setWake() {
        this.waked = true;
    }
}
