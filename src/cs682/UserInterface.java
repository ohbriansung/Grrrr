package cs682;

import chatprotos.ChatProcotol;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User interface to handle user's commands.
 */
public class UserInterface {

    private final Map<String, Runnable> com;
    private ExecutorService senderPool;
    private ExecutorService collectorPool;
    private String commandStyle = ">> ";
    private List<String> inputArgs;

    /**
     * UserInterface constructor.
     */
    public UserInterface() {
        this.com = new HashMap<>();
        createCommands();
    }

    /**
     * Initialize methods in HashMap for user to call.
     */
    private void createCommands() {
        this.com.put("help", this::help); // java7 ==> this.com.put("help", () -> help());
        this.com.put("list", this::list);
        this.com.put("send", this::send);
        this.com.put("broadcast", this::broadcast);
        this.com.put("history", this::history);
        this.com.put("style", this::style);
        this.com.put("detail", this::detail);
        this.com.put("exit", this::exit);
    }

    /**
     * Display welcome message and start user interface.
     * Wait for user to input commands.
     * For each command, start a Runnable in command map.
     */
    public void on() {
        startInfo();
        this.senderPool = Executors.newFixedThreadPool(Chat.THREADS);
        this.collectorPool = Executors.newFixedThreadPool(Chat.THREADS);

        Scanner reader = new Scanner(System.in);
        String input;
        System.out.printf(commandStyle);
        while (Chat.alive && (input = reader.nextLine()) != null){
            if (input.length() > 0) {
                this.inputArgs = parseArgs(input);
                String command = this.inputArgs.get(0);

                if (this.com.containsKey(command)) {
                    this.com.get(command).run();

                    if (command.equals("exit")) {
                        reader.close();
                        break;
                    }
                } else {
                    errorMessage();
                }
            }

            System.out.printf(commandStyle);
        }
    }

    /**
     * Parse the command and arguments inputted by user.
     *
     * @param input
     * @return List
     *      - command and arguments
     */
    private List<String> parseArgs(String input) {
        List<String> args = new ArrayList<>();

        Pattern p = Pattern.compile("\\s?((\"[^\"]*\")|(\\[[^\\]]*\\])|([^\\s]+))");
        Matcher m = p.matcher(input);

        while (m.find()) {
            args.add(m.group(1));
        }

        return args;
    }

    /**
     * Welcome message.
     */
    private void startInfo() {
        System.out.println("**************************************");
        System.out.println("*                                    *");
        System.out.println("*   Welcome to Grrrr!                *");
        System.out.println("*   Enter command or type \"help\".    *");
        System.out.println("*                                    *");
        System.out.println("**************************************");
    }

    /**
     * Display the commands in this system.
     */
    private void help() {
        System.out.println("[System] All commands:");
        System.out.println("(1) help");
        System.out.println("(2) list");
        System.out.println("(3) send [username] \"message\"");
        System.out.println("(4) broadcast \"message\"");
        System.out.println("(5) history");
        System.out.println("(6) style content");
        System.out.println("(7) detail [username]");
        System.out.println("(8) exit");
        System.out.println("* message example: send [csung4] \"hello!\"");
    }

    /**
     * Get the list of nodes on ZooKeeper, store the list into thread-safe data structure.
     */
    private void list() {
        // load nodes and details into thread-safe data structure in Chat
        List<String> nodes = collectDetail();

        StringBuilder sb = new StringBuilder();
        int len = nodes.size();

        for (String username : nodes) {
            sb.append("[" + username + "], ");
        }

        if (len > 0) {
            sb.setLength(sb.length() - 2);
        }

        System.out.println("[System] Nodes in group " + Chat.zk.getGroup() + ":");
        System.out.println(sb.toString());
    }

    /**
     * Create a message with Chat protocol and a list of names.
     * Pass those components to a new thread to schedule sending approach.
     */
    private void send() {
        if (this.inputArgs.size() == 3) {
            String sendTo = this.inputArgs.get(1);
            sendTo = sendTo.substring(1, sendTo.length() - 1);
            String message = this.inputArgs.get(2);
            message = message.substring(1, message.length() - 1);

            ChatProcotol.Chat chat = createChat(message, false);
            List<String> sendList = new ArrayList<>();
            sendList.add(sendTo);

            startSender(sendList, chat);
        }
        else {
            errorMessage();
        }
    }

    /**
     * Create a message with Chat protocol and a list of all the names on ZooKeeper.
     * Pass those components to a new thread to schedule sending approach.
     */
    private void broadcast() {
        if (this.inputArgs.size() == 2) {
            String message = this.inputArgs.get(1);
            message = message.substring(1, message.length() - 1);

            ChatProcotol.Chat chat = createChat(message, true);

            List<String> sendList = new ArrayList<>();
            for (String sendTo : Chat.nodes.keySet()) {
                sendList.add(sendTo);
            }

            startSender(sendList, chat);
        }
        else {
            errorMessage();
        }
    }

    /**
     * Display all broadcast message received by the user in order.
     */
    private void history() {
        if (this.inputArgs.size() == 1) {
            System.out.println("[System] Broadcast history:");
            for (ChatProcotol.Chat chat : Chat.history) {
                System.out.println(chat.getFrom() + ": " + chat.getMessage());
            }
        }
        else {
            errorMessage();
        }
    }

    /**
     * Change the style of command line.
     */
    private void style() {
        if (this.inputArgs.size() == 2) {
            this.commandStyle = this.inputArgs.get(1) + " ";
            System.out.println("[System] Style has been changed.");
        }
        else {
            errorMessage();
        }
    }

    /**
     * Display the detail of a node on ZooKeeper.
     */
    private void detail() {
        if (this.inputArgs.size() == 2) {
            String username = this.inputArgs.get(1);
            username = username.substring(1, username.length() - 1);

            if (!Chat.nodes.containsKey(username)) {
                System.out.println("[System] " + username + " is no longer there.");
            }
            else {
                ChatProcotol.ZKData zkData = Chat.nodes.get(username);
                System.out.println("[System] " + username + " is listening on " + zkData.getIp() + ":" + zkData.getPort());
            }
        }
        else {
            errorMessage();
        }
    }

    /**
     * Close the connections and interrupt the threads, then exit this program.
     */
    private void exit() {
        try {
            System.out.println("[System] Closing...");

            Chat.alive = false;
            Chat.zk.deleteMe();
            Chat.receiverSocket.close();
            this.collectorPool.shutdown();
            this.senderPool.shutdown();

            System.out.println("[System] Completed! Buy!");
        }
        catch (IOException ioe) {
            System.out.println("[System] Exception happened when shutting down.");
            System.exit(0); // normally, won't happen.
        }
    }

    /**
     * Report invalid commands to user.
     */
    private void errorMessage() {
        System.err.println("[System] The command \"" + inputToString() + "\" is invalid, try \"help\".");
    }

    /**
     * Make arguments readable.
     *
     * @return String
     *      - arguments string
     */
    private String inputToString() {
        StringBuilder sb = new StringBuilder();

        for (String str : this.inputArgs) {
            sb.append(str).append(" ");
        }

        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    /**
     * Create a message with Chat protocol.
     *
     * @param message
     * @param isBcast
     * @return DataServerMessages.Chat
     *      - Chat protocol message
     */
    private ChatProcotol.Chat createChat(String message, boolean isBcast) {
        return ChatProcotol.Chat.newBuilder().setFrom(Chat.zk.getUsername())
                .setMessage(message).setIsBcast(isBcast).build();
    }

    /**
     * Collector thread pool to get all the names and ZKData
     * on ZooKeeper and put them into thread-safe data structure locally.
     * Submit new runnable into pool to collect ZKData.
     *
     * @return List
     *      - list of names on ZooKeeper
     */
    public synchronized List<String> collectDetail() {
        List<String> nodes = Chat.zk.getNodes();
        Chat.nodes.clear();

        for (String username : nodes) {
            collectorPool.submit(new Runnable() {
                @Override
                public void run() {
                    Chat.nodes.put(username, Chat.zk.getNodeDetail(username));
                }
            });
        }

        return nodes;
    }

    /**
     * New thread and thread pool to start sending messages.
     * Submit new runnable into pool to handle send a message.
     *
     * @param sendTo
     * @param chat
     */
    private void startSender(List<String> sendTo, ChatProcotol.Chat chat) {

        Runnable senderTask = new Runnable() {
            @Override
            public void run() {
                try {
                    for (String name : sendTo) {
                        senderPool.submit(new Sender(name, Chat.nodes.get(name), chat));
                    }
                }
                catch (Exception ignore) {}
            }
        };

        Thread senderThread = new Thread(senderTask);
        senderThread.start();
    }
}
