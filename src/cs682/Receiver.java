package cs682;

import chatprotos.ChatProcotol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A runnable Receiver to handle single connection.
 */
public class Receiver implements Runnable {

    private final Socket listeningSocket;

    /**
     * Receiver constructor.
     *
     * @param listeningSocket
     */
    public Receiver(Socket listeningSocket) {
        this.listeningSocket = listeningSocket;
    }

    /**
     * Parse, receive and display a message with Chat protocol.
     * If a message is broadcast message, store it into thread-safe data structure.
     * Create a message with Reply protocol to send back.
     * If user received a message from someone not in the nodes list, get the list again.
     */
    @Override
    public void run() {
        try (InputStream inStream = this.listeningSocket.getInputStream();
             OutputStream outStream = this.listeningSocket.getOutputStream()) {

            SimpleDateFormat sdf = new SimpleDateFormat("(yyyy-MM-dd HH:mm:ss)");
            String date = sdf.format(new Date());

            ChatProcotol.Chat request = ChatProcotol.Chat.parseDelimitedFrom(inStream);
            System.out.println((request.getIsBcast() ? "Broadcast" : "Private message")
                    + " from " + request.getFrom() + ": " + request.getMessage() + " " + date);

            if (request.getIsBcast()) {
                Chat.history.add(
                        ChatProcotol.Chat.newBuilder()
                                .setFrom(request.getFrom())
                                .setMessage(request.getMessage() + " " + date)
                                .setIsBcast(request.getIsBcast()).build()
                );
            }

            // if received message from unknown nodes, refresh local nodes data
            if (!Chat.nodes.containsKey(request.getFrom())) {
                UserInterface ui = new UserInterface();
                ui.collectDetail();
            }

            ChatProcotol.Reply response = ChatProcotol.Reply.newBuilder()
                    .setStatus(200).setMessage("OK").build();
            response.writeDelimitedTo(outStream);

            this.listeningSocket.close();
        }
        catch (IOException ioe) {
            System.err.println("IOException occurred to thread "
                    + Thread.currentThread().getId() + " in Receiver: " + ioe);
        }
    }
}
