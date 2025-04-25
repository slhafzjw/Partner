package work.slhaf.agent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.core.InteractionHub;
import work.slhaf.agent.core.interaction.InputReceiver;
import work.slhaf.agent.core.interaction.TaskCallback;
import work.slhaf.agent.core.interaction.data.InteractionInputData;
import work.slhaf.agent.core.interaction.data.InteractionOutputData;
import work.slhaf.agent.gateway.AgentWebSocketServer;
import work.slhaf.agent.gateway.MessageSender;

import java.io.IOException;
import java.time.LocalDateTime;

@Data
@Slf4j
public class Agent implements TaskCallback, InputReceiver {

    private static Agent agent;
    private InteractionHub interactionHub;
    private MessageSender messageSender;

    public static Agent initialize() throws IOException {
        if (agent == null) {
            //加载配置
            Config config = Config.getConfig();
            agent = new Agent();
            agent.setInteractionHub(InteractionHub.initialize());
            agent.registerTaskCallback();
            AgentWebSocketServer server = new AgentWebSocketServer(config.getWebSocketConfig().getPort(),agent);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    for (WebSocket conn : server.getConnections()) {
                        conn.close();
                    }
                    server.stop();
                    log.info("WebSocketServer 已优雅关闭");
                } catch (Exception e) {
                    log.error("关闭失败", e);
                }
            }));

            agent.setMessageSender(server);

            log.info("Agent 加载完毕..");
        }
        return agent;
    }

    /**
     * 接收用户输入，包装为标准输入数据类
     */
    public void receiveInput(InteractionInputData inputData) throws IOException, ClassNotFoundException, InterruptedException {
        inputData.setLocalDateTime(LocalDateTime.now());
        interactionHub.call(inputData);
    }


    /**
     * 向用户返回输出内容
     */
    public void sendToUser(String userInfo,String output){
        System.out.println(output);
        messageSender.sendMessage(new InteractionOutputData(output,userInfo));
    }

    @Override
    public void onTaskFinished(String userInfo, String output) {
        sendToUser(userInfo,output);
    }

    private void registerTaskCallback(){
        interactionHub.setCallback(this);
    }
}
