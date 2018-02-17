package cs682;

import chatprotos.ChatProcotol;
import com.google.protobuf.ByteString;
import concurrent.Download;
import concurrent.SharedDataStructure;

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
        this.map.put(ChatProcotol.Data.packetType.REQUEST, this::request);
        this.map.put(ChatProcotol.Data.packetType.ACK, this::ack);
        this.map.put(ChatProcotol.Data.packetType.DATA, this::data);
    }

    private void request() {
        System.out.println("[System] someone just sent a request!");

        if (!Chat.currentDownloads.containsKey(this.from)) {
            Download download = new Download(Chat.history.get(), 4);
            String[] host = this.from.split(":");

            Runnable dowTask = new DownloadHandler(download, host[0], host[1]);
            Thread dowThread = new Thread(dowTask);

            download.setThread(dowThread);
            Chat.currentDownloads.put(this.from, download);
            dowThread.start();
        }
    }

    private void ack() {
        System.out.println("[System] received acknowledgement " + this.data.getSeqNo() + ".");

        if (Chat.currentDownloads.containsKey(this.from)) {
            Download download = Chat.currentDownloads.get(this.from);
            if (this.data.getSeqNo() >= download.currentState()) {
                download.changeState(this.data.getSeqNo() + 1);
                download.getThread().notify();
            }
        }
    }

    private void data() {
        System.out.println("[System] received data " + this.data.getSeqNo() + ".");

        if (this.data.getSeqNo() == 1 && !Chat.historyFromOthers.containsKey(this.from)) {
            Chat.historyFromOthers.put(this.from, new SharedDataStructure<>());
        }

        if (Chat.historyFromOthers.containsKey(this.from)) {
            SharedDataStructure<ByteString> byteStrings = Chat.historyFromOthers.get(this.from);

            if (this.data.getSeqNo() == byteStrings.size() + 1) {
                int len = this.data.getData().toByteArray().length;

                // if not the last packet, the data size should be 10
                if (len == 10 || this.data.getIsLast()) {
                    byteStrings.add(this.data.getData());
                }

                sendAcknowledgement(this.data.getSeqNo());
            }

            if (this.data.getIsLast() && byteStrings.size() == this.data.getSeqNo()) {
                finishData(byteStrings);
            }
        }
    }

    private void finishData(SharedDataStructure<ByteString> byteStrings) {
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
            System.out.println("[System] new history has been loaded.");

            Chat.historyFromOthers.remove(this.from);
        }
        catch (IOException ioe) {
            System.err.println("[System] issue occurred when parsing a history packet.");
        }
    }

    private void sendAcknowledgement(int seqNo) {
        String[] host = this.from.split(":");

        Runnable ackTask = new UDPSender(host[0], host[1], seqNo);

        Thread ackThread = new Thread(ackTask);
        ackThread.start();
    }
}
