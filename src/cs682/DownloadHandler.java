package cs682;

import chatprotos.ChatProcotol;
import concurrent.Download;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.List;

/**
 * A runnable DownloadHandler to handle the download request from other nodes.
 */
public class DownloadHandler implements Runnable {

    private final static int WINDOW_SIZE = 4;
    private final Download download;
    private final String ip;
    private final String port;

    /**
     * DownloadHandler constructor.
     *
     * @param download
     * @param ip
     * @param port
     */
    public DownloadHandler(Download download, String ip, String port) {
        this.download = download;
        this.ip = ip;
        this.port = port;
    }

    /**
     * Prepare the list of Data packets.
     * Keep sending until the state goes to the end of the list,
     * or until failing for five times in the same state.
     * The number of Data packets we send in each state is base on the window size.
     * After sending all Data packets of current state, wait for waking up by
     * an acknowledgement from target node for 200 ms.
     * After waking or 200 ms, if the state didn't change, count a failure.
     * If the state exceed the end of the list, sending completed.
     */
    @Override
    public void run() {
        synchronized (this) {
            List<ChatProcotol.Data> dataPackets = this.download.get();
            int window = this.download.getWindowSize();
            int size = dataPackets.size();
            int state = this.download.currentState();
            int fail = 0;
            int preEnd = 0;

            while (state <= size) {
                int i = (this.download.isWaked() ? preEnd + 1 : state);
                this.download.resetWake();
                for (; i <= size && i < state + window; i++) {
                    send(dataPackets.get(i - 1));
                    preEnd = i;
                }

                try {
                    this.wait(200);
                }
                catch (InterruptedException ignore) {}

                if (state == this.download.currentState()) {
                    fail++;
                    if (Chat.debug) {
                        System.out.println("[Debug] didn't get any acknowledgement, resending...");
                    }

                    if (fail >= 5) {
                        System.err.println("[System] failed to send history data.");
                        break;
                    }
                }
                else {
                    state = Math.min(this.download.currentState(), state + this.WINDOW_SIZE);
                    fail = 0;
                }
            }

            if (state > size) {
                System.out.println("[System] history data has been successfully delivered.");
            }
            Chat.currentDownloads.remove(this.ip + ":" + this.port);
        }
    }

    private void send(ChatProcotol.Data data) {
        if (Chat.debug) {
            System.out.println("[Debug] sending DATA packet, sequence number: " + data.getSeqNo() + ".");
        }

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
