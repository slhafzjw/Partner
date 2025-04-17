package work.slhaf.agent.gateway;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import work.slhaf.agent.Agent;
import work.slhaf.agent.core.interation.data.InteractionInputData;
import work.slhaf.agent.core.interation.data.InteractionOutputData;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AgentWebSocketServer extends WebSocketServer implements MessageSender {

    private final Agent agent;
    private final ConcurrentHashMap<String, WebSocket> userSessions = new ConcurrentHashMap<>();

    public AgentWebSocketServer(int port, Agent agent) {
        super(new InetSocketAddress(port));
        this.agent = agent;
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        log.info("新连接: {}",webSocket.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        log.info("连接关闭: {}",webSocket.getRemoteSocketAddress());
        userSessions.values().removeIf(session -> session.equals(webSocket));
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        InteractionInputData inputData = JSONObject.parseObject(s, InteractionInputData.class);
        userSessions.put(inputData.getUserInfo(), webSocket); // 注册连接
        try {
            agent.receiveUserInput(inputData.getUserNickName(), inputData.getUserInfo(), inputData.getContent());
        } catch (IOException e) {
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
    public void sendMessage(String userInfo,String message) {
        WebSocket webSocket = userSessions.get(userInfo);
        if (webSocket != null && webSocket.isOpen()) {
            webSocket.send(JSONUtil.toJsonStr(new InteractionOutputData(message)));
        }else {
            log.warn("用户不在线: {}",userInfo);
        }
    }
}
