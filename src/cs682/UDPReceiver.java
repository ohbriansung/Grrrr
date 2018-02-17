package cs682;

import chatprotos.ChatProcotol;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashMap;

public class UDPReceiver implements Runnable {

    private final DatagramPacket packet;
    private final HashMap<String, Runnable> map;

    public UDPReceiver(DatagramPacket packet) {
        this.packet = packet;
        this.map = new HashMap<>();
    }

    @Override
    public void run() {
        ChatProcotol.Data data = parsePacket();

        System.out.println(this.packet.getAddress().getHostAddress() + ":" + this.packet.getPort());
        System.out.println(data.getType());
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

    private void data() {

    }


}
