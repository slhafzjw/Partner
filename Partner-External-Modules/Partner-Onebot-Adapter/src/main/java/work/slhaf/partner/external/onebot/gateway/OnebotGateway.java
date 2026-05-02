package work.slhaf.partner.external.onebot.gateway;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.external.onebot.v11.*;
import work.slhaf.partner.framework.agent.interaction.AgentGateway;
import work.slhaf.partner.framework.agent.interaction.data.*;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class OnebotGateway extends WebSocketServer implements AgentGateway<InputData, PartnerRunningFlowContext> {

    private final AtomicReference<WebSocket> activeConnection = new AtomicReference<>();
    private final AtomicBoolean launched = new AtomicBoolean(false);
    private final String path;
    private final String accessToken;
    private final AtomicLong lastHeartbeatAt = new AtomicLong();

    public OnebotGateway(int port, @NotNull String hostname, @NotNull String path, String accessToken) {
        super(new InetSocketAddress(hostname, port));
        this.setReuseAddr(true);
        this.path = path;
        this.accessToken = accessToken;

        OneBotV11ActionExecutor.bindConnectionReference(this.activeConnection);
        log.info("Onebot will start with {}: {}, {}", hostname, port, path);
    }

    @Override
    public void launch() {
        if (!launched.compareAndSet(false, true)) {
            return;
        }
        this.start();
    }

    @Override
    public PartnerRunningFlowContext parseRunningFlowContext(InputData inputData) {
        PartnerRunningFlowContext context = PartnerRunningFlowContext.fromUser(inputData.getSource(), inputData.getContent(),System.currentTimeMillis(), getChannelName());
        inputData.getMeta().forEach(context::putUserInfo);
        return context;
    }

    @Override
    public void close() {
        try {
            for (WebSocket webSocket : getConnections()) {
                if (webSocket != null && webSocket.isOpen()) {
                    webSocket.close(1001, "Server shutting down");
                }
            }
            if (launched.get()) {
                super.stop(1000);
            }
        } catch (Exception e) {
            log.warn("关闭 OneBotGateway 失败", e);
        } finally {
            activeConnection.set(null);
            launched.set(false);
        }
    }

    @Override
    @NotNull
    public String getChannelName() {
        return "onebot_channel";
    }

    @Override
    public void response(@NotNull InteractionEvent event) {
        String content = extractResponseContent(event);
        if (content == null || content.isBlank()) {
            return;
        }

        WebSocket conn = activeConnection.get();
        if (conn == null || !conn.isOpen()) {
            log.warn("No active OneBot connection for response target: {}", event.getTarget());
            return;
        }

        boolean sent = OneBotV11ActionExecutor.sendMessage(event.getTarget(), content);
        if (!sent) {
            log.warn("Unsupported OneBot response target: {}", event.getTarget());
        }
    }

    private String extractResponseContent(InteractionEvent event) {
        if (event instanceof ReplyEvent replyEvent) {
            return replyEvent.getContent();
        }
        if (event instanceof ModuleEvent moduleEvent) {
            return moduleEvent.getData().getContent();
        }
        if (event instanceof SystemEvent systemEvent) {
            return systemEvent.getTitle() + "\n" + systemEvent.getContent();
        }
        return null;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String actualPath = handshake.getResourceDescriptor();

        if (!actualPath.equals(path)) {
            conn.close(1008, "Unsupported OneBot path: " + actualPath);
            return;
        }

        if (!verifyAccessToken(handshake)) {
            conn.close(1008, "Invalid access token");
            return;
        }

        WebSocket old = activeConnection.getAndSet(conn);
        if (old != null && old != conn && old.isOpen()) {
            old.close(1001, "Replaced by new OneBot connection");
        }

        log.info("OneBot connected: {}", conn.getRemoteSocketAddress());
    }

    private boolean verifyAccessToken(ClientHandshake handshake) {
        if (accessToken == null || accessToken.isBlank()) {
            return true;
        }

        String authorization = handshake.getFieldValue("Authorization");
        return ("Bearer " + accessToken).equals(authorization);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        log.info("OneBot connection closed: {}, code={}, reason={}, remote={}",
                conn.getRemoteSocketAddress(), code, reason, remote);
        activeConnection.compareAndSet(conn, null);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JSONObject json = JSON.parseObject(message);
            if (json.containsKey("echo")) {
                handleActionResponse(json);
                return;
            }

            OneBotV11Event event = OneBotV11EventCodec.parse(json);
            if (event == null) {
                log.debug("Ignore unsupported OneBot payload: {}", message);
                return;
            }
            handleEvent(event);
        } catch (Exception e) {
            log.warn("Invalid OneBot payload: {}", message, e);
        }
    }

    private void handleActionResponse(JSONObject response) {
        log.debug("OneBot action response: {}", response);
    }

    private void handleEvent(OneBotV11Event event) {
        if (event instanceof OneBotV11MetaEvent metaEvent) {
            if (metaEvent.isHeartbeat()) {
                lastHeartbeatAt.set(System.currentTimeMillis());
                log.debug("OneBot heartbeat received");
            }
            return;
        }

        if (event instanceof OneBotV11MessageEvent messageEvent) {
            handleMessageEvent(messageEvent);
            return;
        }

        log.debug("Ignore unsupported OneBot event: {}", event);
    }

    private void handleMessageEvent(OneBotV11MessageEvent event) {
        if (!event.isPrivateMessage()) {
            log.debug("Ignore non-private OneBot message, type={}", event.getMessageType().getDisplayName());
            return;
        }

        InputData inputData = OneBotV11EventCodec.toInputData(event);
        if (inputData == null) {
            log.debug("Ignore empty OneBot private message");
            return;
        }
        receive(inputData);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.error("OneBotGateway error", ex);
    }

    @Override
    public void onStart() {
        log.info("OneBotGateway 已启动...");
    }
}
