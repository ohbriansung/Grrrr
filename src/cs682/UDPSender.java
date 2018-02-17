package cs682;

import chatprotos.ChatProcotol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class UDPSender implements Runnable {

    private final Map<ChatProcotol.Data.packetType, Runnable> map;
    private final ChatProcotol.Data.packetType type;
    private final String ip;
    private final String port;
    private int seqNo;

    public UDPSender(String ip, String port) {
        this.map = new HashMap<>();
        this.type = ChatProcotol.Data.packetType.REQUEST;
        this.ip = ip;
        this.port = port;
    }

    public UDPSender(ChatProcotol.Data.packetType type, String ip, String port, int seqNo) {
        this.map = new HashMap<>();
        this.type = type;
        this.ip = ip;
        this.port = port;
        this.seqNo = seqNo;
    }

    @Override
    public void run() {
        initMap();

        this.map.get(this.type).run();
    }

    private void initMap() {
        this.map.put(ChatProcotol.Data.packetType.ACK, this::ack);
        this.map.put(ChatProcotol.Data.packetType.REQUEST, this::request);
    }

    private void ack() {
        ChatProcotol.Data data = ChatProcotol.Data.newBuilder()
                .setType(this.type).setSeqNo(this.seqNo).build();

        send(data);
    }

    private void request() {
        ChatProcotol.Data data = ChatProcotol.Data.newBuilder().setType(this.type).build();

        send(data);
    }

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
