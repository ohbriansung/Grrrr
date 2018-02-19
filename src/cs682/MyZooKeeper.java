package cs682;

import com.google.protobuf.InvalidProtocolBufferException;
import chatprotos.ChatProcotol.ZKData;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Customized ZooKeeper class to handle all ZooKeeper methods.
 */
public class MyZooKeeper {

    private static final String ZK_HOST = "mc01.cs.usfca.edu";
    private static final int ZK_PORT = 2181;
    private static final String ZK_GROUP = "/zkdemojuzi";// = "/CS682_Chat";
    private final String username;
    private final String ip;
    private final String port;
    private final String udpport;
    private final ZooKeeper zookeeper;

    /**
     * Builder Pattern to implement ZooKeeper object.
     */
    public static class ZKBuilder {
        private String username;
        private String ip;
        private String port;
        private String udpport;
        private ZooKeeper zookeeper;

        /**
         * Builder constructor.
         */
        public ZKBuilder() {}

        /**
         * Set znode name.
         *
         * @param username
         * @return ZKBuilder
         */
        public ZKBuilder setUsername(String username) {
            this.username = username;
            return this;
        }

        /**
         * Set listening ip.
         *
         * @param ip
         * @return ZKBuilder
         */
        public ZKBuilder setIp(String ip) {
            this.ip = ip;
            return this;
        }

        /**
         * Set listening port.
         *
         * @param port
         * @return ZKBuilder
         */
        public ZKBuilder setPort(String port) {
            this.port = port;
            return this;
        }

        /**
         * Set listening udp port.
         *
         * @param udpport
         * @return ZKBuilder
         */
        public ZKBuilder setUdpPort(String udpport) {
            this.udpport = udpport;
            return this;
        }

        /**
         * Set the connection with ZooKeeper.
         *
         * @return ZKBuilder
         * @throws IOException
         * @throws InterruptedException
         */
        public ZKBuilder setZKConnection() throws IOException, InterruptedException {
            final CountDownLatch connectedSignal = new CountDownLatch(1);

            this.zookeeper = new ZooKeeper(MyZooKeeper.ZK_HOST + ":" + MyZooKeeper.ZK_PORT
                    , 1000, new Watcher() {
                public void process(WatchedEvent event) {
                    if (event.getState() == Event.KeeperState.SyncConnected) {
                        connectedSignal.countDown();
                    }
                }
            });

            connectedSignal.await();
            return this;
        }

        /**
         * Build method to finish builder and return MyZooKeeper object.
         *
         * @return MyZooKeeper
         */
        public MyZooKeeper build() {
            return new MyZooKeeper(this);
        }
    }

    /**
     * MyZooKeeper constructor.
     *
     * @param zkBuilder
     */
    private MyZooKeeper(ZKBuilder zkBuilder) {
        this.username = zkBuilder.username;
        this.ip = zkBuilder.ip;
        this.port = zkBuilder.port;
        this.udpport = zkBuilder.udpport;
        this.zookeeper = zkBuilder.zookeeper;
    }

    /**
     * Build ZKData to send to ZooKeeper for other nodes to read.
     *
     * @return ZKData
     */
    private ZKData createZKData() {
        return ZKData.newBuilder().setIp(this.ip)
                .setPort(this.port).setUdpport(this.udpport).build();
    }

    /**
     * Register user to ZooKeeper.
     *
     * @return boolean
     *      - success or not
     */
    public boolean registerMe() {
        try {
            ZKData zkData = createZKData();

            // check if user existed.
            if (this.zookeeper.exists(this.ZK_GROUP + "/" + this.username, false) != null) {
                this.zookeeper.delete(this.ZK_GROUP + "/" + this.username, 0);
            }

            // create znode in zookeeper
            this.zookeeper.create(this.ZK_GROUP + "/" + this.username, zkData.toByteArray()
                    , ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        }
        catch (KeeperException ke) {
            System.err.println("[System] Unable to join group " + this.ZK_GROUP + " as " + this.username);
            return false;
        }
        catch (Exception e) {
            System.err.println("[System] Exception happened in registerMe method: " + e);
            return false;
        }

        return true;
    }

    /**
     * Delete user on ZooKeeper.
     *
     * @return boolean
     *      - success or not
     */
    public boolean deleteMe() {
        try {
            if (this.zookeeper.exists(this.ZK_GROUP + "/" + this.username, false) != null) {
                this.zookeeper.delete(this.ZK_GROUP + "/" + this.username, 0);
            }

            this.zookeeper.close();
        }
        catch (KeeperException ke) {
            System.err.println("[System] Unable to delete "+  this.username + " from group " + this.ZK_GROUP);
            return false;
        }
        catch (InterruptedException ie) {
            System.err.println("[System] Been interrupted in deleteMe method: " + ie);
            return false;
        }

        return true;
    }

    /**
     * Get the children nodes in current ZooKeeper group.
     *
     * @return List
     *      - list of nodes
     */
    public List<String> getNodes() {
        List<String> nodes = null;

        try {
            nodes = this.zookeeper.getChildren(this.ZK_GROUP, false);
        }
        catch (KeeperException ke) {
            System.err.println("[System] Unable to get nodes from " + this.ZK_GROUP);
        }
        catch (InterruptedException ie) {
            System.err.println("[System] Been interrupted in getNodes method: " + ie);
        }

        return nodes;
    }

    /**
     * Parse the raw bytes data in a particular node to readable protocol.
     *
     * @param username
     * @return ZKData
     */
    public ZKData getNodeDetail(String username) {
        ZKData zkData = null;

        try {
            Stat s = new Stat();
            byte[] detail = zookeeper.getData(this.ZK_GROUP + "/" + username, false, s);
            zkData = ZKData.parseFrom(detail);
        }
        catch (KeeperException ke) {
            System.err.println("[System] Unable to get detail of " + this.ZK_GROUP + "/" + username);
        }
        catch (InterruptedException ie) {
            System.err.println("[System] Been interrupted in getNodeDetail method: " + ie);
        }
        catch (InvalidProtocolBufferException ipbe) {
            System.err.println("[System] " + this.ZK_GROUP + "/" + username + "'s ZKData is invalid.");
        }

        return zkData;
    }

    /**
     * Get the using user name.
     *
     * @return String
     *      - username
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * Get the using group name.
     *
     * @return String
     *      - current group
     */
    public String getGroup() {
        return this.ZK_GROUP;
    }
}
