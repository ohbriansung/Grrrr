package cs682;

import chatprotos.ChatProcotol;
import concurrent.SharedDataStructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * A runnable UDPSender to send Datagram to other nodes.
 */
public class UDPSender implements Runnable {

    private final Map<ChatProcotol.Data.packetType, Runnable> map;
    private final ChatProcotol.Data.packetType type;
    private final String ip;
    private final String port;
    private ChatProcotol.Data data;
    private int seqNo;

    /**
     * Overloading UDPSender constructor.
     * REQUEST packet type.
     *
     * @param ip
     * @param port
     */
    public UDPSender(String ip, String port) {
        this.map = new HashMap<>();
        this.type = ChatProcotol.Data.packetType.REQUEST;
        this.ip = ip;
        this.port = port;
    }

    /**
     * Overloading UDPSender constructor.
     * ACK packet type.
     *
     * @param ip
     * @param port
     * @param seqNo
     */
    public UDPSender(String ip, String port, int seqNo) {
        this.map = new HashMap<>();
        this.type = ChatProcotol.Data.packetType.ACK;
        this.ip = ip;
        this.port = port;
        this.seqNo = seqNo;
    }

    /**
     * Overloading UDPSender constructor.
     * DATA packet type.
     *
     * @param ip
     * @param port
     * @param data
     */
    public UDPSender(String ip, String port, ChatProcotol.Data data) {
        this.map = new HashMap<>();
        this.type = ChatProcotol.Data.packetType.DATA;
        this.ip = ip;
        this.port = port;
        this.data = data;
    }

    /**
     * Run the method base on the type of packet.
     */
    @Override
    public void run() {
        initMap();

        this.map.get(this.type).run();
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
     * Create an thread-safe data structure to store incoming data packet.
     * Send the request to target node.
     * Wait 5 seconds and check the result.
     * If the data structure no longer exists, means the download approach has completed.
     * If the data structure exists, but the size didn't increase, could be losing request.
     * If the data structure exists and the size increased, could be in the progress.
     * Try to check three times.
     * If the data structure still exists, could be losing request or data, abort.
     */
    private synchronized void request() {
        ChatProcotol.Data data = ChatProcotol.Data.newBuilder().setType(this.type).build();

        String target = this.ip + ":" + this.port;
        if (!Chat.historyFromOthers.containsKey(target)) {
            Chat.historyFromOthers.put(target, new SharedDataStructure<>());
        }

        // try three times
        boolean success = false;
        for (int i = 0; i < 3; i++) {
            if (!success) {
                send(data);
            }

            try {
                wait(5000);
            }
            catch (InterruptedException ignore) {}

            if (!Chat.historyFromOthers.containsKey(target)) {
                // task completed
                break;
            }
            else if (Chat.historyFromOthers.containsKey(target)
                    && Chat.historyFromOthers.get(target).size() == 0) {
                System.out.println("[System] hasn't received a reply, resending request...");
            }
            else {
                // still receiving data
                success = true;
            }
        }

        // if didn't finish receiving a complete history data and didn't remove the data structure
        if (Chat.historyFromOthers.containsKey(target)) {
            Chat.historyFromOthers.remove(target);
            System.out.println("[System] waiting too long, aborted.");
        }
    }

    /**
     * Send an acknowledgement back to target node.
     */
    private void ack() {
        ChatProcotol.Data data = ChatProcotol.Data.newBuilder()
                .setType(this.type).setSeqNo(this.seqNo).build();

        send(data);
    }

    /**
     * Send a Data packet.
     */
    private void data() {
        send(this.data);
    }

    /**
     * Pack data into ByteArrayOutputStream and send it via UDP.
     *
     * @param data
     */
    private void send(ChatProcotol.Data data) {
        try (ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
            data.writeDelimitedTo(outStream);
            byte[] packet = outStream.toByteArray();
            DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length
                    , InetAddress.getByName(this.ip), Integer.parseInt(this.port));

            Chat.udpSocket.send(datagramPacket);
        }
        catch (IOException ignore) {}
    }
}
