package cs682;

import chatprotos.ChatProcotol;
import concurrent.Download;

import java.util.List;

/**
 * A runnable DownloadHandler to handle the download request from other nodes.
 */
public class DownloadHandler implements Runnable {

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

            while (state <= size) {
                for (int i = state; i <= size && i < state + window; i++) {
                    Runnable sendTask = new UDPSender(this.ip, this.port, dataPackets.get(i - 1));
                    Thread sendThread = new Thread(sendTask);
                    sendThread.start();
                }

                try {
                    this.wait(200);
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
