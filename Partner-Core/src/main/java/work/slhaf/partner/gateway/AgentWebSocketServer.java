package work.slhaf.partner.gateway;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.core.interaction.agent_interface.InputReceiver;
import work.slhaf.partner.core.interaction.data.InteractionInputData;
import work.slhaf.partner.core.interaction.data.InteractionOutputData;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AgentWebSocketServer extends WebSocketServer implements MessageSender {

    private static final long HEARTBEAT_INTERVAL = 10_000;

    @ToString.Exclude
    private final InputReceiver receiver;
    private final ConcurrentHashMap<String, WebSocket> userSessions = new ConcurrentHashMap<>();
    private final InteractionThreadPoolExecutor executor;

    // 记录最后一次收到Pong的时间
    private final ConcurrentHashMap<WebSocket, Long> lastPongTimes = new ConcurrentHashMap<>();

    public AgentWebSocketServer(int port, InputReceiver receiver) {
        super(new InetSocketAddress(port));
        this.receiver = receiver;
        this.executor = InteractionThreadPoolExecutor.getInstance();
    }

    public void launch() {
        this.start();
        setShutDownHook();
        startHeartbeatThread();
    }

    private void startHeartbeatThread() {
        executor.execute(() -> {
            while (!Thread.interrupted()){
                try{
                    Thread.sleep(HEARTBEAT_INTERVAL);
                    checkConnections();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void checkConnections() {
        long now = System.currentTimeMillis();
        for (WebSocket conn : getConnections()) {
            if (conn.isOpen()) {
                // 发送Ping
                conn.sendPing();
                log.debug("Sent Ping to {}", conn.getRemoteSocketAddress());

                // 检查上次Pong响应是否超时（2倍心跳间隔）
                Long lastPong = lastPongTimes.get(conn);
                if (lastPong != null && now - lastPong > HEARTBEAT_INTERVAL * 2) {
                    log.warn("Connection {} timed out, closing...", conn.getRemoteSocketAddress());
                    conn.close(1001, "No Pong response");
                }
            }
        }
    }

    @Override
    public void onWebsocketPong(WebSocket conn, Framedata f) {
        lastPongTimes.put(conn, System.currentTimeMillis());
        log.debug("Received Pong from {}", conn.getRemoteSocketAddress());
    }

    private void setShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                //关闭WebSocketServer
                this.stop();
                log.info("WebSocketServer 已关闭");
            } catch (Exception e) {
                log.error("WebSocketServer关闭失败: ", e);
            }
        }));
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        log.info("新连接: {}", webSocket.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        log.info("连接关闭: {}", webSocket.getRemoteSocketAddress());
        lastPongTimes.remove(webSocket);
        userSessions.values().removeIf(session -> session.equals(webSocket));
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        InteractionInputData inputData = JSONObject.parseObject(s, InteractionInputData.class);
        userSessions.put(inputData.getUserInfo(), webSocket); // 注册连接
        try {
            receiver.receiveInput(inputData);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        log.error(e.getLocalizedMessage());
    }

    @Override
    public void onStart() {
        log.info("WebSocketServer 已启动...");
    }

    @Override
    public void sendMessage(InteractionOutputData outputData) {
        WebSocket webSocket = userSessions.get(outputData.getUserInfo());
        if (webSocket != null && webSocket.isOpen()) {
            webSocket.send(JSONUtil.toJsonStr(outputData));
        } else {
            log.warn("用户不在线: {}", outputData.getUserInfo());
        }
    }
}
