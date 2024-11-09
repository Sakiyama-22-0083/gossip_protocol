package gossip;

import gossip.config.GossipConfig;
import gossip.service.GossipService;

import java.time.Duration;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.io.BufferedWriter;
import java.io.File;
import java.net.InetSocketAddress;

/**
 * メインクラス
 */
public class GossipMain {
    private static String csvFile = "log/output.csv";

    public static void main(String[] args) {
        // ゴシッププロトコル設定
        GossipConfig gossipConfig = new GossipConfig(
                Duration.ofSeconds(3),
                Duration.ofSeconds(3),
                Duration.ofMillis(500),
                Duration.ofMillis(500),
                3);

        // 最初のノードをネットワークに追加する
        GossipService initialNode = new GossipService(
                new InetSocketAddress("127.0.0.1", 9090),
                gossipConfig, csvFile);

        initialNode.start();
        resetCSVFile(csvFile);

        ArrayList<GossipService> gossipServices = new ArrayList<GossipService>();
        // 他のノードを追加し，ネットワークを構築する
        for (int i = 1; i <= 10; i++) {
            GossipService gossipService = new GossipService(
                    new InetSocketAddress("127.0.0.1", 9090 + i),
                    new InetSocketAddress("127.0.0.1", 9090 + i - 1),
                    gossipConfig, csvFile);
            gossipService.start();
            gossipServices.add(gossipService);
        }
        // 3秒間通信
        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        initialNode.stop();
        for (GossipService gossipService : gossipServices) {
            gossipService.stop();
        }
        // プログラム終了
        System.exit(0);
    }

    /**
     * csvファイルの内容をリセットするメソッド
     *
     * @param file
     */
    private static void resetCSVFile(String filePath) {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        // logディレクトリが存在しない場合，新たにディレクトリを作成する．
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs(); // ディレクトリを再帰的に作成
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("");
        } catch (IOException e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
        }
    }

}
