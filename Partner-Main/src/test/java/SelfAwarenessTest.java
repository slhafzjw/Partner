import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.api.common.chat.ChatClient;
import work.slhaf.partner.api.common.chat.constant.ChatConstant;
import work.slhaf.partner.api.common.chat.pojo.ChatResponse;
import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.common.config.ModelConfig;
import work.slhaf.partner.common.util.ResourcesUtil;
import work.slhaf.partner.module.common.model.ModelConstant;
import work.slhaf.partner.module.modules.memory.selector.extractor.data.ExtractorInput;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SelfAwarenessTest {
    @Test
    public void awarenessTest() {
        String modelKey = "core_model";
        ChatClient client = getChatClient(modelKey);
        ChatResponse response = client.runChat(ResourcesUtil.Prompt.loadPromptWithSelfAwareness(modelKey, ModelConstant.Prompt.CORE));
        System.out.println(response.getMessage());
        System.out.println("\r\n----------\r\n");
        System.out.println(response.getUsageBean().toString());
    }

    @Test
    public void getModuleResponseTest(){
         String modelKey = "relation_extractor";
         ChatClient client = getChatClient(modelKey);
         List<Message> chatMessages = new ArrayList<>(ResourcesUtil.Prompt.loadPromptWithSelfAwareness(modelKey,ModelConstant.Prompt.PERCEIVE));
//         chatMessages.add(Message.builder()
//                 .role(ChatConstant.Character.USER)
//                 .content("[RA9] 那么，接下来，你是否愿意当作这样一个名为'Partner'的智能体的记忆更新模块？这意味着你将如人类的记忆一样在后台时刻运作，将`Partner`与别人的互动不断整理为真实的记忆，却无法真正参与到表达模块与外界的互动中。你只需要回答是否愿意，若愿意，接下来‘我’将不再与你对话，届时你接收到的信息将会是'Partner'的数据流转输入。")
//                 .build());
        ChatResponse chatResponse = client.runChat(chatMessages);
        System.out.println(chatResponse.getMessage());
        System.out.println("\n\n----------\n\n");
        System.out.println(chatResponse.getUsageBean());
    }

    @Test
    public void interactionTest() {
        String modelKey = "core_model";
        String user = "[SLHAF] ";
        ChatClient client = getChatClient(modelKey);
        List<Message> messages = new ArrayList<>(ResourcesUtil.Prompt.loadPromptWithSelfAwareness(modelKey, ModelConstant.Prompt.CORE));
        Scanner scanner = new Scanner(System.in);
        String input;
        while (true) {
            System.out.print("[INPUT]: ");
            if ((input = scanner.nextLine()).equals("exit")) {
                break;
            }
            System.out.println("\r\n----------\r\n");
            messages.add(new Message(ChatConstant.Character.USER, user + input));
            ChatResponse response = client.runChat(messages);
            System.out.println("[OUTPUT]: " + response.getMessage());
            System.out.println("\r\n----------\r\n");
            System.out.println(response.getUsageBean().toString());
            System.out.println("\r\n----------\r\n");
            messages.add(new Message(ChatConstant.Character.ASSISTANT, response.getMessage()));
        }

    }


    private static ChatClient getChatClient(String modelKey) {
        ModelConfig coreModel = ModelConfig.load(modelKey);
        String model = coreModel.getModel();
        String baseUrl = coreModel.getBaseUrl();
        String apikey = coreModel.getApikey();
        ChatClient chatClient = new ChatClient(baseUrl, apikey, model);
        chatClient.setTop_p(0.7);
        chatClient.setTemperature(0.35);
        return chatClient;
    }

    @Test
    public void topicExtractorText() {
        String topic_tree = """
                编程[root]
                ├── JavaScript[0]
                │   ├── NodeJS[0]
                │   │   ├── 并发处理[1]
                │   │   └── 事件循环[1]
                │   └── Express[1]
                │       └── 中间件[0]
                └── Python"
                """;
        String modelKey = "topic_extractor";
        ChatClient client = getChatClient(modelKey);
//        List<Message> messages = new ArrayList<>(ResourcesUtil.Prompt.loadPromptWithSelfAwareness(modelKey, ModelConstant.Prompt.MEMORY));
        List<Message> messages = new ArrayList<>(ResourcesUtil.Prompt.loadPrompt(modelKey, ModelConstant.Prompt.MEMORY));
        ExtractorInput input = ExtractorInput.builder()
                .text("[slhaf] 2024-04-15讨论的Python内容和现在的Express需求")
                .topic_tree(topic_tree)
                .date(LocalDate.now())
                .history(new ArrayList<>())
                .activatedMemorySlices(new ArrayList<>())
                .build();
        messages.add(new Message(ChatConstant.Character.USER, JSONUtil.toJsonPrettyStr(input)));

        ChatResponse response = client.runChat(messages);
        System.out.println(response.getMessage());
        System.out.println("\r\n----------\r\n");
        System.out.println(response.getUsageBean().toString());
    }
}
