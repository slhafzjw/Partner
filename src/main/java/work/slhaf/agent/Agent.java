package work.slhaf.agent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.monitor.DebugMonitor;
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

    private static volatile Agent agent;
    private InteractionHub interactionHub;
    private MessageSender messageSender;

    public static void initialize() throws IOException {
        if (agent == null) {
            synchronized (Agent.class) {
                if (agent == null) {
                    //加载配置
                    Config config = Config.getConfig();
                    agent = new Agent();
                    agent.setInteractionHub(InteractionHub.initialize());
                    agent.registerTaskCallback();
                    AgentWebSocketServer server = new AgentWebSocketServer(config.getWebSocketConfig().getPort(), agent);
                    server.launch();
                    agent.setMessageSender(server);
                    log.info("Agent 加载完毕..");

                    //启动监测线程
                    DebugMonitor.initialize();
                }
            }
        }
    }

    public static Agent getInstance() throws IOException {
        initialize();
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
    public void sendToUser(String userInfo, String output) {
        messageSender.sendMessage(new InteractionOutputData(output, userInfo));
    }

    @Override
    public void onTaskFinished(String userInfo, String output) {
        sendToUser(userInfo, output);
    }

    private void registerTaskCallback() {
        interactionHub.setCallback(this);
    }
}
