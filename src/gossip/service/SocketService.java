package gossip.service;

import gossip.node.Node;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class SocketService {
    private DatagramSocket datagramSocket;// UDP通信を行うソケットクラス
    private byte[] receivedBuffer = new byte[1024];// 受け取ったバイト配列
    private DatagramPacket receivePacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);// 受け取ったパケット
    private String csvFilePath;

    /**
     * 引数のポートのUDP通信ソケットを作成するコンストラクタ
     *
     * @param portToListen
     */
    public SocketService(int portToListen) {
        this.csvFilePath = "log/" + portToListen + ".csv";

        try {
            // UDPソケットを作成
            datagramSocket = new DatagramSocket(portToListen);
        } catch (SocketException e) {
            System.out.println("Could not create socket connection");
            e.printStackTrace();
        }
    }

    /**
     * メッセージを送信するメソッド
     * 第一引数のノードに対して，第二引数のノードからのメッセージをUDPパケットで送信する．
     *
     * @param node
     * @param message
     */
    public void sendGossip(Node node, Node message) {
        byte[] bytesToWrite = getBytesToWrite(message);
        sendGossipMessage(node, bytesToWrite);
    }

    /**
     * UDPソケットデータを受信しするメソッド
     * 受信したデータデータ（バイト配列）をNodeオブジェクトとして返す．
     *
     * @return
     */
    @SuppressWarnings("finally")
    public Node receiveGossip() {
        try {
            // UDPパケットを待ち受け，受信したらデータをreceivePacketに格納する
            datagramSocket.receive(receivePacket);
            // 受信したデータ（バイト配列）をObjectInputStreamに変換する
            ObjectInputStream objectInputStream = new ObjectInputStream(
                    new ByteArrayInputStream(receivePacket.getData()));

            Node message = null;
            try {
                // データをNodeオブジェクトとして読み込む
                message = (Node) objectInputStream.readObject();

                String csvData = "Received gossip message from [" + message.getUniqueId() + "]";
                writeData(csvFilePath, csvData);

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                objectInputStream.close();
                return message;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 引数で指定するNodeメッセージをバイト配列に変換するメソッド
     *
     * @param message
     * @return
     */
    private byte[] getBytesToWrite(Node message) {
        ByteArrayOutputStream bStream = new ByteArrayOutputStream();

        String csvData = "Writing message " + message.getNetworkMessage();
        writeData(csvFilePath, csvData);

        try {
            ObjectOutput oo = new ObjectOutputStream(bStream);
            oo.writeObject(message);
            oo.close();
        } catch (IOException e) {
            System.out.println("Could not send " + message.getNetworkMessage() + "] because: " + e.getMessage());
            e.printStackTrace();
        }
        return bStream.toByteArray();
    }

    /**
     * ゴシップメッセージを送信するメソッド
     * 第一引数のNodeに対して第二引数のバイト配列を送信する．
     *
     * @param target
     * @param data
     */
    private void sendGossipMessage(Node target, byte[] data) {
        // パケットはデータ，データ長，ターゲットのIPアドレス，ターゲットのポート番号を保持する．
        DatagramPacket packet = new DatagramPacket(data, data.length, target.getInetAddress(), target.getPort());
        try {
            datagramSocket.send(packet);

            String csvData = "Sending gossip message to [" + target.getUniqueId() + "]";
            writeData(csvFilePath, csvData);

        } catch (IOException e) {
            System.out.println("Fatal error trying to send: " + packet + " to [" + target.getSocketAddress() + "]");
            e.printStackTrace();
            // target.setFailed(true);
        }
    }

    /**
     * csvファイルにデータを書き込むメソッド
     * 第1引数のcsvファイルに第2引数の内容を追記する．
     *
     * @param csvFail
     * @param data
     */
    private void writeData(String csvFail, String data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFail, true))) {
            writer.write(data);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
        }
    }

}
