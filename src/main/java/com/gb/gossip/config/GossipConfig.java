package com.gb.gossip.config;

import java.time.Duration;
import java.io.Serializable;

/**
 * 各ノードの設定を表すクラス
 */
public class GossipConfig implements Serializable {
    public final Duration failureTimeout;// 故障時間のタイムアウト
    public final Duration cleanupTimeout;// 故障した情報の削除タイムアウト
    public final Duration updateFrequency;// 情報更新周期
    public final Duration failureDetectionFrequency;// 故障検知周期
    public final int peersToUpdatePerInterval;// 情報を送信する相手の数

    public GossipConfig(Duration failureTimeout, Duration cleanupTimeout,
            Duration updateFrequency, Duration failureDetectionFrequency,
            int peersToUpdatePerInterval) {
        this.failureTimeout = failureTimeout;
        this.cleanupTimeout = cleanupTimeout;
        this.updateFrequency = updateFrequency;
        this.failureDetectionFrequency = failureDetectionFrequency;
        this.peersToUpdatePerInterval = peersToUpdatePerInterval;
    }
}
