package work.slhaf.agent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.core.InteractionHub;
import work.slhaf.agent.core.interation.TaskCallback;
import work.slhaf.agent.core.interation.data.InteractionInputData;
import work.slhaf.agent.gateway.AgentWebSocketServer;
import work.slhaf.agent.gateway.MessageSender;

import java.io.IOException;
import java.time.LocalDateTime;

@Data
@Slf4j
public class Agent implements TaskCallback {

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
            agent.setMessageSender(new AgentWebSocketServer(config.getWebSocketConfig().getPort(),agent));
            log.info("Agent 加载完毕..");
        }
        return agent;
    }

    /**
     * 接收用户输入，包装为标准输入数据类
     * @param input
     */
    public void receiveUserInput(String userNickName,String userInfo,String input) throws IOException {
        InteractionInputData inputData = new InteractionInputData();
        inputData.setContent(input);
        inputData.setUserInfo(userInfo);
        inputData.setUserNickName(userNickName);
        inputData.setLocalDateTime(LocalDateTime.now());
        interactionHub.call(inputData);
    }


    /**
     * 向用户返回输出内容
     * @param output
     */
    public void sendToUser(String userInfo,String output){
        System.out.println(output);
//        messageSender.sendMessage(userInfo,output);
    }

    @Override
    public void onTaskFinished(String userInfo, String output) {
        sendToUser(userInfo,output);
    }

    private void registerTaskCallback(){
        interactionHub.setCallback(this);
    }
}
