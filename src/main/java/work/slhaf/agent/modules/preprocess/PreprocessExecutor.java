package work.slhaf.agent.modules.preprocess;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.interaction.data.InteractionInputData;
import work.slhaf.agent.core.memory.MemoryManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class PreprocessExecutor {

    private static PreprocessExecutor preprocessExecutor;

    private MemoryManager memoryManager;

    private PreprocessExecutor() {
    }

    public static PreprocessExecutor getInstance() throws IOException, ClassNotFoundException {
        if (preprocessExecutor == null) {
            preprocessExecutor = new PreprocessExecutor();
            preprocessExecutor.setMemoryManager(MemoryManager.getInstance());
        }
        return preprocessExecutor;
    }

    public InteractionContext execute(InteractionInputData inputData) {
        InteractionContext context = new InteractionContext();
        String userId = memoryManager.getUserId(inputData.getUserInfo(), inputData.getUserNickName());

        context.setUserId(userId);
        context.setUserNickname(inputData.getUserNickName());
        context.setUserInfo(inputData.getUserInfo());
        context.setDateTime(inputData.getLocalDateTime());

        context.setFinished(false);
        context.setInput(inputData.getContent());

        context.setCoreContext(new JSONObject());
        context.getCoreContext().put("text", inputData.getContent());
        context.getCoreContext().put("datetime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        context.getCoreContext().put("character", memoryManager.getCharacter());
        context.getCoreContext().put("user_nick", inputData.getUserNickName());
        context.getCoreContext().put("user_id", userId);

        context.setModuleContext(new JSONObject());

        context.setModulePrompt(new JSONObject());

        return context;
    }
}
