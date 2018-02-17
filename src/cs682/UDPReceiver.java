package cs682;

import chatprotos.ChatProcotol;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import concurrent.SharedDataStructure;
import sun.nio.cs.US_ASCII;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashMap;
import java.util.List;

public class UDPReceiver implements Runnable {

    private final DatagramPacket packet;
    private final HashMap<ChatProcotol.Data.packetType, Runnable> map;
    private ChatProcotol.Data data;
    private String from;

    public UDPReceiver(DatagramPacket packet) {
        this.packet = packet;
        this.map = new HashMap<>();
    }

    @Override
    public void run() {
        this.data = parsePacket();
        this.from = this.packet.getAddress().getHostAddress() + ":" + this.packet.getPort();
        initMap();

        map.get(data.getType()).run();
    }

    private ChatProcotol.Data parsePacket() {
        byte[] receivedData = this.packet.getData();

        ChatProcotol.Data data = null;
        try (ByteArrayInputStream inStream = new ByteArrayInputStream(receivedData)) {
            data = ChatProcotol.Data.parseDelimitedFrom(inStream);
        }
        catch (IOException ioe) {
            System.err.println(ioe);
        }

        return data;
    }

    private void initMap() {
        this.map.put(ChatProcotol.Data.packetType.DATA, this::data);
    }

    private void data() {
        if (!Chat.historyFromOthers.containsKey(this.from)) {
            Chat.historyFromOthers.put(this.from, new SharedDataStructure<>());
        }

        SharedDataStructure<ByteString> byteStrings = Chat.historyFromOthers.get(this.from);
        if (this.data.getSeqNo() == byteStrings.size() + 1) {
            int len = this.data.getData().toByteArray().length;

            // if not the last, the packet size should be 10
            if (len == 10 || this.data.getIsLast()) {
                byteStrings.add(this.data.getData());
            }

        }

        if (this.data.getIsLast() && byteStrings.size() == this.data.getSeqNo()) {
            List<ByteString> list = byteStrings.get();
            int len = (list.size() - 1) * 10 + this.data.getData().toByteArray().length;
            int index = 0;
            byte[] temp = new byte[len];

            for (ByteString byteString : list) {
                for (byte oneByte : byteString.toByteArray()) {
                    temp[index++] = oneByte;
                }
            }

            try {
                List<ChatProcotol.Chat> history = ChatProcotol.History.parseFrom(temp).getHistoryList();
                Chat.history.replaceAll(history);
                System.out.println("finished");
            }
            catch (IOException ioe) {
                System.err.println(ioe);
            }
        }
    }
}
