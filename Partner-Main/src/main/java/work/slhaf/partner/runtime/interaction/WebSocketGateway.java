package work.slhaf.partner.runtime.interaction;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.api.agent.runtime.interaction.AgentGateway;
import work.slhaf.partner.api.agent.runtime.interaction.AgentInteractionAdapter;
import work.slhaf.partner.common.config.PartnerAgentConfigManager;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.runtime.interaction.data.PartnerInputData;
import work.slhaf.partner.runtime.interaction.data.PartnerOutputData;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class WebSocketGateway extends WebSocketServer implements AgentGateway<PartnerInputData, PartnerOutputData, PartnerRunningFlowContext> {

    private static final long HEARTBEAT_INTERVAL = 10_000;

    @ToString.Exclude
    private final ConcurrentHashMap<String, WebSocket> userSessions = new ConcurrentHashMap<>();
    private final InteractionThreadPoolExecutor executor;

    // 记录最后一次收到Pong的时间
    private final ConcurrentHashMap<WebSocket, Long> lastPongTimes = new ConcurrentHashMap<>();

    public static WebSocketGateway initialize() {
        PartnerAgentConfigManager configManager = (PartnerAgentConfigManager) AgentConfigManager.INSTANCE;
        return new WebSocketGateway(configManager.getConfig().getPort());
    }

    private WebSocketGateway(int port) {
        super(new InetSocketAddress(port));
        this.executor = InteractionThreadPoolExecutor.getInstance();
    }

    public void launch() {
        this.start();
        setShutDownHook();
        startHeartbeatThread();
    }

    @Override
    public void send(PartnerOutputData outputData) {
        userSessions.forEach((userInfo, webSocket) -> {
            if (webSocket.isOpen()) {
                webSocket.send(JSONUtil.toJsonStr(outputData));
            } else {
                log.warn("用户不在线: {}", userInfo);
            }
        });
    }

    @Override
    public AgentInteractionAdapter<PartnerInputData, PartnerOutputData, PartnerRunningFlowContext> adapter() {
        return new PartnerInteractionAdapter();
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
        PartnerInputData inputData = JSONObject.parseObject(s, PartnerInputData.class);
        userSessions.put(inputData.getUserInfo(), webSocket); // 注册连接
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

}
