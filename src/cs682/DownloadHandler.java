package cs682;

import chatprotos.ChatProcotol;
import concurrent.Download;

import java.util.List;

public class DownloadHandler implements Runnable {

    private final Download download;
    private final String ip;
    private final String port;

    public DownloadHandler(Download download, String ip, String port) {
        this.download = download;
        this.ip = ip;
        this.port = port;
    }

    @Override
    public void run() {
        List<ChatProcotol.Data> dataPackets = this.download.get();
        int window = this.download.getWindowSize();
        int size = dataPackets.size();
        int state = this.download.currentState();
        int fail = 0;

        System.out.println("[System] data: " + size + ".");
        while (state <= size) {
            System.out.println("[System] send data from " + state + ".");

            for (int i = state; i < size && i < state + window; i++) {
                Runnable sendTask = new UDPSender(this.ip, this.port, dataPackets.get(i - 1));
                Thread sendThread = new Thread(sendTask);
                sendThread.start();
            }

            try {
                wait(1000);
            }
            catch (Exception ignore) {}

            if (state == this.download.currentState()) {
                fail++;
            }
            else {
                state = this.download.currentState();
                fail = 0;
            }

            if (fail >= 3) {
                System.err.println("[System] failed to send data.");
                break;
            }
        }

        Chat.currentDownloads.remove(this.ip + ":" + this.port);
    }
}
