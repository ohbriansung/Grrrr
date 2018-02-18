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
        synchronized (this) {
            List<ChatProcotol.Data> dataPackets = this.download.get();
            int window = this.download.getWindowSize();
            int size = dataPackets.size();
            int state = this.download.currentState();
            int fail = 0;

            while (state <= size) {
                for (int i = state; i <= size && i < state + window; i++) {
                    Runnable sendTask = new UDPSender(this.ip, this.port, dataPackets.get(i - 1));
                    Thread sendThread = new Thread(sendTask);
                    sendThread.start();
                }

                try {
                    this.wait(500);
                }
                catch (Exception ignore) {}

                if (state == this.download.currentState()) {
                    fail++;
                    if (fail >= 5) {
                        System.err.println("[System] failed to send history data.");
                        break;
                    }
                }
                else {
                    state = this.download.currentState();
                    fail = 0;
                }

                if (state > size) {
                    System.out.println("[System] history data has been successfully delivered.");
                }
            }

            Chat.currentDownloads.remove(this.ip + ":" + this.port);
        }
    }
}
