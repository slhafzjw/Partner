package work.slhaf.partner.runtime.interaction;

import com.alibaba.fastjson2.JSONObject;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigLoader;
import work.slhaf.partner.api.agent.runtime.interaction.AgentGateway;
import work.slhaf.partner.api.agent.runtime.interaction.data.InputData;
import work.slhaf.partner.api.agent.runtime.interaction.data.InteractionEvent;
import work.slhaf.partner.common.config.PartnerAgentConfigLoader;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class WebSocketGateway extends WebSocketServer implements AgentGateway<InputData, PartnerRunningFlowContext> {

    private static final long HEARTBEAT_INTERVAL = 10_000;

    @ToString.Exclude
    private final ConcurrentHashMap<String, WebSocket> userSessions = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    // 记录最后一次收到Pong的时间
    private final ConcurrentHashMap<WebSocket, Long> lastPongTimes = new ConcurrentHashMap<>();

    public WebSocketGateway() {
        this(((PartnerAgentConfigLoader) AgentConfigLoader.INSTANCE).getConfig().getWebSocketConfig().getPort());
    }

    private WebSocketGateway(int port) {
        super(new InetSocketAddress(port));
        this.setReuseAddr(true);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void launch() {
        this.start();
        setShutDownHook();
        startHeartbeatThread();
        register();
    }

    @Override
    public PartnerRunningFlowContext parseRunningFlowContext(InputData inputData) {
        PartnerRunningFlowContext context = PartnerRunningFlowContext.fromUser(inputData.getSource(), inputData.getContent());
        inputData.getMeta().forEach(context::putUserInfo);
        return context;
    }

    @Override
    public void response(@NotNull InteractionEvent event) {
        userSessions.forEach((userInfo, webSocket) -> {
            if (webSocket.isOpen()) {
                webSocket.send(JSONObject.toJSONString(event));
            } else {
                log.warn("用户不在线: {}", userInfo);
            }
        });
    }

    private void startHeartbeatThread() {
        executor.execute(() -> {
            while (!Thread.interrupted()) {
                try {
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
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // 先断开所有客户端连接
                    for (WebSocket webSocket : getConnections()) {
                        try {
                            webSocket.close(1001, "Server shutting down");
                        } catch (Exception e) {
                            log.warn("关闭客户端连接时出错: ", e);
                        }
                    }
                    //关闭WebSocketServer，给10秒超时时间确保连接正确关闭
                    this.stop(10000);
                    log.info("WebSocketServer 已关闭");
                } catch (IllegalStateException e) {
                    log.warn("无法添加关闭钩子，JVM可能已在关闭过程中: ", e);
                } catch (Exception e) {
                    log.error("WebSocketServer关闭失败: ", e);
                }
            }));
        } catch (IllegalStateException e) {
            log.warn("无法添加关闭钩子，JVM可能已在关闭过程中: ", e);
        }
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
        InputData inputData = JSONObject.parseObject(s, InputData.class);
        userSessions.put(inputData.getSource(), webSocket); // 注册连接
        receive(inputData);
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
    @NotNull
    public String getChannelName() {
        return "websocket_channel";
    }
}
