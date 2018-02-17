package concurrent;

import chatprotos.ChatProcotol;
import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Download {

    private final ChatProcotol.Data.packetType type;
    private final int windowSize;
    private final List<ChatProcotol.Data> dataPackets;
    private List<ChatProcotol.Chat> data;
    private byte[] bytes;

    private ReentrantReadWriteLock lock;
    private int state;

    public Download(List<ChatProcotol.Chat> data, int windowSize) {
        this.type = ChatProcotol.Data.packetType.DATA;
        this.windowSize = windowSize;
        this.dataPackets = new ArrayList<>();
        this.data = data;

        this.lock = new ReentrantReadWriteLock();
        this.state = 1;

        convertIntoBytes();
        packIntoTenBytes();
    }

    private void convertIntoBytes() {
        this.bytes = ChatProcotol.History.newBuilder().addAllHistory(data).build().toByteArray();
    }

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

    public List<ChatProcotol.Data> get() {
        return this.dataPackets;
    }

    public int getWindowSize() {
        return this.windowSize;
    }

    public int currentState() {
        this.lock.readLock().lock();
        int state = this.state;
        this.lock.readLock().unlock();

        return state;
    }

    public void changeState(int state) {
        this.lock.writeLock().lock();
        if (state >= this.state) {
            this.state = state + 1;
        }
        this.lock.writeLock().lock();
    }
}
