package com.gb.gossip.node;

import com.gb.gossip.config.GossipConfig;

import java.time.Duration;
import java.io.Serializable;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.net.InetSocketAddress;

/**
 * ノードを表すクラス
 */
public class Node implements Serializable {

    private final InetSocketAddress address;// ソケットのアドレス
    private long heartbeatSequenceNumber = 0;// シーケンス番号
    private LocalDateTime lastUpdateTime = null;// 最後のアップデート時間
    private volatile boolean failed = false;// ノードが故障しているかのbool値
    private GossipConfig config;// ゴシッププロトコルの設定情報を保持するオブジェクト

    public Node(InetSocketAddress address, long initialSequenceNumber, GossipConfig config) {
        this.address = address;
        this.heartbeatSequenceNumber = initialSequenceNumber;
        this.config = config;

        setLastUpdatedTime();
    }

    public void setConfig(GossipConfig config) {
        this.config = config;
    }

    /**
     * HostNameのゲッター
     * 
     * @return
     */
    public String getAddress() {
        return address.getHostName();
    }

    /**
     * ノードのIPアドレスのゲッター
     *
     * @return
     */
    public InetAddress getInetAddress() {
        return address.getAddress();
    }

    /**
     * ソケットアドレスのゲッター
     *
     * @return
     */
    public InetSocketAddress getSocketAddress() {
        return address;
    }

    /**
     * ソケットが使用するポート番号のゲッター
     *
     * @return
     */
    public int getPort() {
        return address.getPort();
    }

    /**
     * ノードの識別番号のゲッター
     *
     * @return
     */
    public String getUniqueId() {
        return address.toString();
    }

    /**
     * シーケンス番号を返すメソッド
     * 
     * @return
     */
    public long getSequenceNumber() {
        return heartbeatSequenceNumber;
    }

    /**
     * シークエンス番号のセッター
     * 新しいゴシップが現在保有している情報よりも新しい場合，新しいゴシップのシークエンス番号を
     * heartbeatSequenceNumberとして代入する．
     *
     * @param newSequenceNumber
     */
    public void updateSequenceNumber(long newSequenceNumber) {
        if (newSequenceNumber > heartbeatSequenceNumber) {
            heartbeatSequenceNumber = newSequenceNumber;
            setLastUpdatedTime();

            // System.out.println("Sequence number of current node "
            // + this.getUniqueId() + " is " + this.getSequenceNumber()
            // + " updated to " + newSequenceNumber);
        }
    }

    /**
     * ノードの故障状態のゲッター
     *
     * @return
     */
    public boolean hasFailed() {
        return failed;
    }

    /**
     * 故障状態のセッター
     *
     * @param failed
     */
    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    /**
     * アップデート時間を更新するメソッド
     */
    public void setLastUpdatedTime() {
        LocalDateTime updatedTime = LocalDateTime.now();
        lastUpdateTime = updatedTime;

        // System.out.println("Node " + this.getUniqueId() + " at " + updatedTime);
    }

    /**
     * ノードのシーケンス番号をインクリメントするメソッド
     */
    public void incrementSequenceNumber() {
        heartbeatSequenceNumber++;
        setLastUpdatedTime();
    }

    /**
     * ノードが故障しているか判定するメソッド
     */
    public void checkIfFailed() {
        LocalDateTime failureTime = lastUpdateTime.plus(config.failureTimeout);
        LocalDateTime now = LocalDateTime.now();
        failed = now.isAfter(failureTime);
    }

    /**
     * 故障したノードのデータを削除するべきか判定するメソッド
     *
     * @return
     */
    public boolean shouldCleanup() {
        if (failed) {
            Duration cleanupTimeout = config.failureTimeout.plus(config.cleanupTimeout);
            LocalDateTime cleanupTime = lastUpdateTime.plus(cleanupTimeout);
            LocalDateTime now = LocalDateTime.now();
            return now.isAfter(cleanupTime);
        } else {
            return false;
        }
    }

    /**
     * ネットワークに関する情報を表示するメソッド
     *
     * @return
     */
    public String getNetworkMessage() {
        return "[" + address.getHostName() + ":" + address.getPort() + "-" + heartbeatSequenceNumber + "]";
    }
}
