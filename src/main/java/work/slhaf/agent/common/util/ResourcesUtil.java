package work.slhaf.agent.common.util;

import com.alibaba.fastjson2.JSONArray;
import work.slhaf.agent.Agent;
import work.slhaf.agent.common.chat.pojo.Message;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ResourcesUtil {

    private static final ClassLoader classloader = Agent.class.getClassLoader();

    public static class Prompt {
        private static final String SELF_AWARENESS_PATH = "prompt/self_awareness.json";
        private static final String MODULE_PROMPT_PREFIX_PATH = "prompt/module/";

        public static List<Message> loadPrompt(String modelKey) {
            //加载人格引导
            List<Message> messages = new ArrayList<>(loadSelfAwareness());
            //加载常规提示
            String path = MODULE_PROMPT_PREFIX_PATH + modelKey + ".json";
            messages.addAll(readPromptFromResources(path));
            return messages;
        }

        private static List<Message> loadSelfAwareness() {
            return readPromptFromResources(SELF_AWARENESS_PATH);
        }

        private static List<Message> readPromptFromResources(String filePath) {
            try {
                InputStream inputStream = classloader.getResourceAsStream(filePath);
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                JSONArray array = JSONArray.parse(content);
                inputStream.close();
                return array.toJavaList(Message.class);
            } catch (Exception e) {
                throw new RuntimeException("读取Resource失败: " + filePath, e);
            }
        }
    }

}
