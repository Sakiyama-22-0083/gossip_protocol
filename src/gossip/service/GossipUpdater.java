package gossip.service;

import java.net.InetSocketAddress;

/**
 * アップデート処理を表すインターフェース
 */
public interface GossipUpdater {
    void update(InetSocketAddress inetSocketAddress);
}
