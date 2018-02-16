package cs682;

import chatprotos.ChatProcotol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;
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
     * Thread-safe data structure for storing broadcast messages in order.
     */
    protected static final Vector<ChatProcotol.Chat> history = new Vector<>();

    /**
     * Thread-safe data structure for storing information of nodes on ZooKeeper locally.
     */
    protected static Hashtable<String, ChatProcotol.ZKData> nodes = new Hashtable<>();

    /**
     * Customized ZooKeeper object.
     */
    protected static MyZooKeeper zk;

    /**
     * Static Receiver.
     */
    protected static ServerSocket receiverSocket;

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

        // start listening TCP
        new Chat().startReceiver(arguments.get("port"));

        // start listening UDP

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

        //ChatProcotol.History h = ChatProcotol.History.newBuilder().addHistory().build();
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
        }

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
