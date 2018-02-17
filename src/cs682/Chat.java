package cs682;

import chatprotos.ChatProcotol;
import com.google.protobuf.ByteString;
import concurrent.SharedDataStructure;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A peer to peer chat application developed with ZooKeeper,
 * Google's Protocol Buffers and Maven.
 *
 * Flow:
 * 1. parse args and get ip
 * 2. start listening tcp
 * 3. start listening udp
 * 4. register with zookeeper
 * 5. start ui
 * 6. send messages
 *
 * @author Brian Sung
 */
public class Chat {

    /**
     * Thread number for each thread pool.
     */
    protected static final int THREADS = 8;

    /**
     * Status of Receiver and User Interface.
     */
    protected static volatile boolean alive = true;

    /**
     * Thread-safe data structure for storing the history of broadcast messages.
     */
    protected static final SharedDataStructure<ChatProcotol.Chat> history = new SharedDataStructure<>();

    /**
     * Thread-safe data structure for storing information of nodes on ZooKeeper locally.
     */
    protected static Hashtable<String, ChatProcotol.ZKData> nodes = new Hashtable<>();

    protected static final Hashtable<String, SharedDataStructure<ByteString>> historyFromOthers = new Hashtable<>();

    /**
     * Customized ZooKeeper object.
     */
    protected static MyZooKeeper zk;

    /**
     * Static TCP Receiver.
     */
    protected static ServerSocket receiverSocket;

    /**
     * Static UDP Receiver.
     */
    protected static DatagramSocket udpSocket;

    /**
     * Main thread to start the program.
     * Read and parse input arguments from command line first.
     * Start new thread for listening.
     * Connect with ZooKeeper and register a node.
     * Finally, start new thread for UI.
     *
     * @param args
     *      - username and port
     */
    public static void main(String[] args) {

        // parse and check arguments
        Map<String, String> arguments = parseArgs(args);
        if (!arguments.containsKey("username")
                || !arguments.containsKey("port")
                || !arguments.containsKey("udpport")) {
            System.err.println("[System] Lack of username or port.");
            return; // exit
        }

        // start listening on TCP port
        new Chat().startReceiver(arguments.get("port"));

        // start listening on UDP port
        new Chat().startUDPReceiver(arguments.get("udpport"));

        // build ZooKeeper and register node
        try {
            zk = new MyZooKeeper.ZKBuilder()
                    .setUsername(arguments.get("username"))
                    .setIp(InetAddress.getLocalHost().getHostAddress())
                    .setPort(arguments.get("port"))
                    .setUdpPort(arguments.get("udpport"))
                    .setZKConnection().build();
        }
        catch (Exception e) {
            System.err.println("[System] Exception happened when building ZooKeeper: " + e);

            try { // shutdown
                Chat.receiverSocket.close();
            }
            catch (IOException ioe) {
                System.out.println("[System] Exception happened when shutting down.");
                System.exit(0); // normally, won't happen.
            }
            return;
        }
        zk.registerMe();

        // start user interface to accept commands
        new Chat().startUserInterface();
    }

    /**
     * Method to parse the input arguments.
     *
     * @param args
     * @return Map
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        int len = args.length;

        for (int i = 0; i < len; i++) {
            if (args[i].equals("-user") && i < len - 1) {
                map.put("username", args[++i]);
            }
            else if (args[i].equals("-port") && i < len - 1) {
                map.put("port", args[++i]);
            }
            else if (args[i].equals("-udpport") && i < len - 1) {
                map.put("udpport", args[++i]);
            }
        }

        // TODO: delete before deploy
        map.put("username", "csung4");
        map.put("port", "8080");
        map.put("udpport", "8081");

        return map;
    }

    /**
     * New thread and thread pool to start listening.
     * Submit new runnable into pool to handle new connection.
     *
     * @param port
     */
    private void startReceiver(String port) {
        final ExecutorService receiverPool = Executors.newFixedThreadPool(THREADS);

        Runnable receiverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    Chat.receiverSocket = new ServerSocket(Integer.parseInt(port));

                    while (Chat.alive) {
                        Socket listeningSocket = Chat.receiverSocket.accept();
                        receiverPool.submit(new Receiver(listeningSocket));
                    }
                }
                catch (IOException ignore) {
                    // exception will happened when we close receiverSocket
                    receiverPool.shutdown();
                }
            }
        };

        Thread receiverThread = new Thread(receiverTask);
        receiverThread.start();
    }

    private void startUDPReceiver(String udpport) {
        final ExecutorService udpReceiverPool = Executors.newFixedThreadPool(THREADS);

        Runnable receiverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    Chat.udpSocket = new DatagramSocket(Integer.parseInt(udpport));

                    while (Chat.alive) {
                        byte[] empty = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(empty, empty.length);

                        udpSocket.receive(packet);
                        udpReceiverPool.submit(new UDPReceiver(packet));
                    }
                }
                catch (IOException ignore) {
                    // exception will happened when we close receiverSocket
                    udpReceiverPool.shutdown();
                }
            }
        };

        Thread receiverThread = new Thread(receiverTask);
        receiverThread.start();
    }

    /**
     * New thread to create and start the user interface.
     */
    private void startUserInterface() {
        Runnable uiTask = new Runnable() {
            @Override
            public void run() {
                UserInterface ui = new UserInterface();
                ui.on();
            }
        };

        Thread uiThread = new Thread(uiTask);
        uiThread.start();
    }
}
