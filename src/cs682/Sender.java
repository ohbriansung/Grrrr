package cs682;

import chatprotos.ChatProcotol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * A runnable Sender to send message to particular host and port.
 */
public class Sender implements Runnable {

    private final String sendTo;
    private final ChatProcotol.ZKData zkData;
    private final ChatProcotol.Chat chat;

    /**
     * Sender constructor.
     *
     * @param sendTo
     * @param zkData
     * @param chat
     */
    public Sender(String sendTo, ChatProcotol.ZKData zkData, ChatProcotol.Chat chat) {
        this.sendTo = sendTo;
        this.zkData = zkData;
        this.chat = chat;
    }

    /**
     * First, create a new thread to interrupt this Sender if timeout.
     * Send the message to a particular node with Chat protocol.
     * Check if the node return a response with Reply protocol.
     * Notify user if a private message has been received by a node.
     * Report error message to user if a message didn't send to a node.
     */
    @Override
    public void run() {
        try (Socket sendingSocket = new Socket()) {
            SocketAddress endPoint = new InetSocketAddress(this.zkData.getIp(), Integer.parseInt(this.zkData.getPort()));
            sendingSocket.connect(endPoint, 300); // timeout when a node has issue receiving message

            OutputStream outStream = sendingSocket.getOutputStream();
            InputStream inStream = sendingSocket.getInputStream();

            this.chat.writeDelimitedTo(outStream);

            try {
                ChatProcotol.Reply response = ChatProcotol.Reply.parseDelimitedFrom(inStream);

                // report if a node has received message successful
                if (!this.chat.getIsBcast() && response.getStatus() == 200) {
                    System.out.println("[System] " + this.sendTo + " has received your private message.");
                }
            }
            catch (Exception ignore) {
                // message has been delivered but no reply.
            }

            sendingSocket.close();
        }
        catch (IOException ioe) {
            // a node's ZKData is invalid or it is no longer exist, remove it locally
            System.err.println("[System] issue occurred when sending message to " + this.sendTo);
            Chat.nodes.remove(this.sendTo);
        }
    }
}
