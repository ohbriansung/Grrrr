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

/**
 * A runnable UDPReceiver to handle Datagram packets.
 */
public class UDPReceiver implements Runnable {

    private final static int WINDOW_SIZE = 4;
    private final DatagramPacket packet;
    private final HashMap<ChatProcotol.Data.packetType, Runnable> map;
    private ChatProcotol.Data data;
    private String from;

    /**
     * UDPReceiver constructor.
     *
     * @param packet
     */
    public UDPReceiver(DatagramPacket packet) {
        this.packet = packet;
        this.map = new HashMap<>();
    }

    /**
     * Parse the received packet.
     * Create a signature for the node sent this packet.
     * Run the method base on the type of packet.
     */
    @Override
    public void run() {
        this.data = parsePacket();
        this.from = this.packet.getAddress().getHostAddress() + ":" + this.packet.getPort();
        initMap();

        // lost ack debug mode: when a node sent a acknowledgement and we just drop it
        if (Chat.debug == 2 && this.data.getType() == ChatProcotol.Data.packetType.ACK) {
            System.out.println("[Debug] dropping ACK packet no: " + this.data.getSeqNo() + ".");
            return;
        }
        // lost request debug mode: when a node sent request and we just drop it
        else if (Chat.debug == 3 && this.data.getType() == ChatProcotol.Data.packetType.REQUEST) {
            System.out.println("[Debug] dropping REQUEST packet.");
            return;
        }

        map.get(data.getType()).run();
    }

    /**
     * Parse the Datagram packet into a ChatProcotol Data object.
     *
     * @return ChatProcotol.Data
     *      - Data in the packet
     */
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

    /**
     * Initialize methods in HashMap to handle different kinds of Data.
     */
    private void initMap() {
        this.map.put(ChatProcotol.Data.packetType.REQUEST, this::request);
        this.map.put(ChatProcotol.Data.packetType.ACK, this::ack);
        this.map.put(ChatProcotol.Data.packetType.DATA, this::data);
    }

    /**
     * Notify user that there is a download request.
     * Create a thread-save data structure storing current history data.
     * Set the window size for Go-Back-N algorithm, let's say 4.
     * Create a new thread to handle this download approach.
     * Register the reference of that thread so we can wake it later.
     * Set up internal state to keep track of the in-progress download.
     * Start download approach.
     */
    private void request() {
        System.out.println("[System] someone just request a history data!");

        if (!Chat.currentDownloads.containsKey(this.from)) {
            Download download = new Download(Chat.history.get(), WINDOW_SIZE);
            String[] host = this.from.split(":");

            Runnable dowTask = new DownloadHandler(download, host[0], host[1]);
            Thread dowThread = new Thread(dowTask);

            download.setThread(dowThread);
            Chat.currentDownloads.put(this.from, download);
            dowThread.start();
        }
        else {
            System.out.println("[System] ignore since his/her previous request hasn't finished.");
        }
    }

    /**
     * Update the internal state if the sequence number in the acknowledgement
     * is equal or larger than the current state of this download approach,
     * and also inside the window size.
     * Wake the in-progress download handler up to proceed.
     */
    private void ack() {
        if (Chat.currentDownloads.containsKey(this.from)) {
            Download download = Chat.currentDownloads.get(this.from);

            int state = this.data.getSeqNo();
            if (state >= download.currentState() && state <= download.currentState() + this.WINDOW_SIZE) {
                download.changeState(state + 1);
                download.getThread().notifyAll();
            }
        }
    }

    /**
     * If the sequence number of a new Data is the one we expected,
     * store it into the thread-save data structure.
     * Send back acknowledgement with same sequence number.
     * Notify user receiving a valid Data.
     * If the packet is the last one, build up a new history
     * and replace user's history with this one.
     */
    private void data() {
        // lost data debug mode: receiving one data packet and drop the rest
        if (Chat.debug == 1 && this.data.getSeqNo() != 1) {
            System.out.println("[Debug] dropping DATA packet no: " + this.data.getSeqNo() + ".");
            return;
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
                System.out.println("[System] received data, sequence number: " + this.data.getSeqNo() + ".");
            }

            if (this.data.getIsLast() && byteStrings.size() == this.data.getSeqNo()) {
                finishData(byteStrings);
            }
        }
    }

    /**
     * Recover the original byte array of history data we received.
     * Parse the byte array into a list of Chat history.
     * Replace user's history with this one.
     * Notify user.
     *
     * @param byteStrings
     */
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

    /**
     * Create a thread to send back an acknowledgement.
     *
     * @param seqNo
     */
    private void sendAcknowledgement(int seqNo) {
        String[] host = this.from.split(":");

        Runnable ackTask = new UDPSender(host[0], host[1], seqNo);

        Thread ackThread = new Thread(ackTask);
        ackThread.start();
    }
}
