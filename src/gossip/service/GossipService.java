package gossip.service;

import gossip.config.GossipConfig;
import gossip.node.Node;

import java.util.List;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.io.BufferedWriter;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ゴシッププロトコルサービスを行うクラス
 * 各ノードでこの
 */
public class GossipService {
    public final InetSocketAddress inetSocketAddress;// 自身のノードのアドレス
    private SocketService socketService;
    private Node self = null;// 自身のノードを表すNodeオブジェクト
    // ネットワーク内の全ノードオブジェクトを保持する
    private ConcurrentHashMap<String, Node> nodes = new ConcurrentHashMap<>();
    private boolean stopped = false;
    // ゴシッププロトコルの設定情報を保持するオブジェクト
    private GossipConfig gossipConfig = null;
    // イベント発生時のコールバック処理を行うインスタンス
    private GossipUpdater onNewMember = null;
    private GossipUpdater onFailedMember = null;
    private GossipUpdater onRemovedMember = null;
    private GossipUpdater onRevivedMember = null;
    private String csvFile = "output.csv";

    /**
     * 最初のノードのコンストラクタ
     *
     * @param inetSocketAddress
     * @param gossipConfig
     */
    public GossipService(InetSocketAddress inetSocketAddress, GossipConfig gossipConfig) {
        this.inetSocketAddress = inetSocketAddress;
        this.gossipConfig = gossipConfig;
        this.socketService = new SocketService(inetSocketAddress.getPort());
        // まだ登録されていなければ自身のノードを配列に追加する
        self = new Node(inetSocketAddress, 0, gossipConfig);
        nodes.putIfAbsent(self.getUniqueId(), self);
        setEventHandler();
    }

    /**
     * 最初以外のノードのコンストラクタ
     *
     * @param listeningAddress
     * @param targetAddress
     * @param gossipConfig
     */
    public GossipService(InetSocketAddress listeningAddress,
            InetSocketAddress targetAddress,
            GossipConfig gossipConfig) {
        this(listeningAddress, gossipConfig);
        // 最初に接続するターゲットノードを配列に追加する
        Node initialTarget = new Node(targetAddress, 0, gossipConfig);
        nodes.putIfAbsent(initialTarget.getUniqueId(), initialTarget);
    }

    /**
     * ゴシッププロトコルに必要な各スレッドを起動するメソッド
     */
    public void start() {
        startSenderThread();
        startReceiverThread();
        startFailureDetectionThread();
        printNodes(3000);
    }

    /**
     * 現在生存しているノードのリストを取得するメソッド
     *
     * @return
     */
    public ArrayList<InetSocketAddress> getAliveMembers() {
        int initialSize = nodes.size();
        ArrayList<InetSocketAddress> aliveMembers = new ArrayList<>(initialSize);

        for (String key : nodes.keySet()) {
            Node node = nodes.get(key);
            if (!node.hasFailed()) {
                String ipAddress = node.getAddress();
                int port = node.getPort();
                aliveMembers.add(new InetSocketAddress(ipAddress, port));
            }
        }

        return aliveMembers;
    }

    /**
     * 故障しているノードのリストを取得するメソッド
     *
     * @return
     */
    public ArrayList<InetSocketAddress> getFailedMembers() {
        ArrayList<InetSocketAddress> failedMembers = new ArrayList<>();

        for (String key : nodes.keySet()) {
            Node node = nodes.get(key);
            node.checkIfFailed();
            if (node.hasFailed()) {
                String ipAddress = node.getAddress();
                int port = node.getPort();
                failedMembers.add(new InetSocketAddress(ipAddress, port));
            }
        }

        return failedMembers;
    }

    /**
     * 全てのノードのリストを取得するメソッド
     *
     * @return
     */
    public ArrayList<InetSocketAddress> getAllMembers() {
        // used to prevent resizing of ArrayList.
        int initialSize = nodes.size();
        ArrayList<InetSocketAddress> allMembers = new ArrayList<>(initialSize);

        for (String key : nodes.keySet()) {
            Node node = nodes.get(key);
            String ipAddress = node.getAddress();
            int port = node.getPort();
            allMembers.add(new InetSocketAddress(ipAddress, port));
        }
        return allMembers;
    }

    /**
     * スレッドを停止するためのメソッド
     */
    public void stop() {
        stopped = true;
    }

    /**
     * 新しいメソッドが追加された場合のコールバック設定メソッド
     *
     * @param onNewMember
     */
    public void setOnNewNodeHandler(GossipUpdater onNewMember) {
        this.onNewMember = onNewMember;
    }

    /**
     * ノードが故障した場合のコールバックを設定するメソッド
     *
     * @param onFailedMember
     */
    public void setOnFailedNodeHandler(GossipUpdater onFailedMember) {
        this.onFailedMember = onFailedMember;
    }

    /**
     * ノードが復活した場合のコールバックを設定するメソッド
     *
     * @param onRevivedMember
     */
    public void setOnRevivedNodeHandler(GossipUpdater onRevivedMember) {
        this.onRevivedMember = onRevivedMember;
    }

    /**
     * ノードが削除された場合のコールバックを設定するメソッド
     *
     * @param onRemovedMember
     */
    public void setOnRemoveNodeHandler(GossipUpdater onRemovedMember) {
        this.onRemovedMember = onRemovedMember;
    }

    /**
     * メッセージを定期的に送信するためのスレッドを起動するメソッド
     */
    private void startSenderThread() {
        new Thread(() -> {
            while (!stopped) {
                sendGossipToRandomNode();
                try {
                    Thread.sleep(gossipConfig.updateFrequency.toMillis());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 他のノードからメソッドを受信するためのスレッドを起動するメソッド
     */
    private void startReceiverThread() {
        new Thread(() -> {
            while (!stopped) {
                receivePeerMessage();
            }
        }).start();
    }

    /**
     * 各ノードの状態を確認し，故障しているノードを検出するためのスレッドを起動するメソッド
     */
    private void startFailureDetectionThread() {
        new Thread(() -> {
            while (!stopped) {
                detectFailedNodes();

                try {
                    Thread.sleep(gossipConfig.failureDetectionFrequency.toMillis());
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * ランダムなノードに対してメッセージを送信するメソッド
     */
    private void sendGossipToRandomNode() {
        self.incrementSequenceNumber();
        List<String> peersToUpdate = new ArrayList<>();
        Object[] keys = nodes.keySet().toArray();

        if (keys.length < gossipConfig.peersToUpdatePerInterval) {
            for (int i = 0; i < keys.length; i++) {
                String key = (String) keys[i];
                if (!key.equals(self.getUniqueId())) {
                    peersToUpdate.add(key);
                }
            }
        } else {
            for (int i = 0; i < gossipConfig.peersToUpdatePerInterval; i++) {
                boolean newTargetFound = false;
                while (!newTargetFound) {
                    String targetKey = (String) keys[getRandomIndex(nodes.size())];
                    if (!targetKey.equals(self.getUniqueId())) {
                        newTargetFound = true;
                        peersToUpdate.add(targetKey);
                    }
                }
            }
        }

        for (String targetAddress : peersToUpdate) {
            Node node = nodes.get(targetAddress);
            new Thread(() -> socketService.sendGossip(node, self)).start();
        }
    }

    /**
     * ランダムなインデックスを生成するメソッド
     *
     * @param size
     * @return
     */
    private int getRandomIndex(int size) {
        int randomIndex = (int) (Math.random() * size);
        return randomIndex;
    }

    /**
     * 他のノードから受信したメッセージを処理するメソッド
     * 受信したノードが新規ノードであればonNewMemberコールバックを実行し、
     * 既存ノードであればシーケンス番号を更新する．
     */
    private void receivePeerMessage() {
        Node newNode = socketService.receiveGossip();// 受信したノードオブジェクト
        Node existingMember = nodes.get(newNode.getUniqueId());
        if (existingMember == null) {// 受信したノードの情報を保持にしていない場合
            synchronized (nodes) {
                newNode.setConfig(gossipConfig);
                newNode.setLastUpdatedTime();
                nodes.putIfAbsent(newNode.getUniqueId(), newNode);
                // 新規ノード追加時のコールバックを実行
                if (onNewMember != null) {
                    onNewMember.update(newNode.getSocketAddress());
                }
            }
        } else {// 受信したノードの情報をすでに保持にしている場合
            // ノードのシーケンス番号更新
            existingMember.updateSequenceNumber(newNode.getSequenceNumber());

            // System.out.println("Updating sequence number for node " +
            // existingMember.getUniqueId());
        }
    }

    /**
     * ノードが故障したか検出し，適切なコールバックメソッドを実行するメソッド
     */
    private void detectFailedNodes() {
        String[] keys = new String[nodes.size()];
        nodes.keySet().toArray(keys);

        for (String key : keys) {
            Node node = nodes.get(key);
            boolean hadFailed = node.hasFailed();
            node.checkIfFailed();
            // 故障情報が更新されていれば適切なコールバックを実行する
            if (hadFailed != node.hasFailed() && node.hasFailed()) {
                // nodes.remove(key);
                if (onFailedMember != null) {
                    onFailedMember.update(node.getSocketAddress());
                } else if (onRevivedMember != null) {
                    onRevivedMember.update(node.getSocketAddress());
                }
            }
            // ノードの情報を削除するか判定する
            if (node.shouldCleanup()) {
                synchronized (nodes) {
                    nodes.remove(key);
                    // ノード削除時コールバックを実行
                    if (onRemovedMember != null) {
                        onRemovedMember.update(node.getSocketAddress());
                    }
                }
            }
        }
    }

    /**
     * 現在のノードの状態を出力するメソッド
     * frequencyの時間ごとにログを出力する．
     * ログには各ノードのローカルIPアドレス，ポート番号，故障の有無を出力する．
     */
    private void printNodes(int frequency) {
        new Thread(() -> {
            while (!stopped) {
                // getAliveMembers().forEach(node -> System.out.println(
                // "Health status: " + node.getHostName() + ":" + node.getPort() + "- alive"));

                // getFailedMembers().forEach(node -> System.out.println(
                // "Health status: " + node.getHostName() + ":" + node.getPort() + "- failed"));

                System.out.println("Health status: " + inetSocketAddress.getHostName() + ":"
                        + inetSocketAddress.getPort() + ", " + self.hasFailed());

                writeData(inetSocketAddress.getHostName() + "," + inetSocketAddress.getPort()
                        + "," + self.hasFailed());

                try {
                    Thread.sleep(frequency);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void writeData(String data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile, true))) {
            writer.write(data);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
        }
    }

    private void setEventHandler() {
        // イベントハンドラの設定
        setOnNewNodeHandler((inetSocketAddress) -> {
            System.out.println("Connected to " +
                    inetSocketAddress.getHostName() + ":"
                    + inetSocketAddress.getPort());
        });
        setOnFailedNodeHandler((inetSocketAddress) -> {
            System.out.println("Node " + inetSocketAddress.getHostName() + ":"
                    + inetSocketAddress.getPort() + " failed");
        });
        setOnRemoveNodeHandler((inetSocketAddress) -> {
            System.out.println("Node " + inetSocketAddress.getHostName() + ":"
                    + inetSocketAddress.getPort() + " removed");
        });
        setOnRevivedNodeHandler((inetSocketAddress) -> {
            System.out.println("Node " + inetSocketAddress.getHostName() + ":"
                    + inetSocketAddress.getPort() + " revived");
        });
    }

}
