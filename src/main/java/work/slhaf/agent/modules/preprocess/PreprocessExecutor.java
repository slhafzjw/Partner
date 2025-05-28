package work.slhaf.agent.modules.preprocess;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.interaction.data.InteractionInputData;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.session.SessionManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

@Data
@Slf4j
public class PreprocessExecutor {

    private static PreprocessExecutor preprocessExecutor;

    private MemoryManager memoryManager;
    private SessionManager sessionManager;

    private PreprocessExecutor() {
    }

    public static PreprocessExecutor getInstance() throws IOException, ClassNotFoundException {
        if (preprocessExecutor == null) {
            preprocessExecutor = new PreprocessExecutor();
            preprocessExecutor.setMemoryManager(MemoryManager.getInstance());
            preprocessExecutor.setSessionManager(SessionManager.getInstance());
        }
        return preprocessExecutor;
    }

    public InteractionContext execute(InteractionInputData inputData) {
        checkAndSetMemoryId();
        return getInteractionContext(inputData);
    }

    private void checkAndSetMemoryId() {
        String currentMemoryId = sessionManager.getCurrentMemoryId();
        if (currentMemoryId == null || memoryManager.getChatMessages().isEmpty()) {
            sessionManager.refreshMemoryId();
        }
    }

    private InteractionContext getInteractionContext(InteractionInputData inputData) {
        log.debug("[PreprocessExecutor] 预处理原始输入: {}",inputData);
        InteractionContext context = new InteractionContext();

        String userId = memoryManager.getUserId(inputData.getUserInfo(), inputData.getUserNickName());
        context.setUserId(userId);
        context.setUserNickname(inputData.getUserNickName());
        context.setUserInfo(inputData.getUserInfo());
        context.setDateTime(inputData.getLocalDateTime());

        context.setFinished(false);
        String user = "[" + inputData.getUserNickName() + "(" + userId + ")]";
        String input = user + " " + inputData.getContent();
        context.setInput(input);

        context.setCoreContext(new JSONObject());
        context.getCoreContext().put("text", input);
        context.getCoreContext().put("datetime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        context.getCoreContext().put("character", memoryManager.getCharacter());
        context.getCoreContext().put("user_nick", inputData.getUserNickName());
        context.getCoreContext().put("user_id", userId);

        context.setModuleContext(new JSONObject());

        context.setAppendPrompt(new ArrayList<>());

        context.setSingle(inputData.isSingle());
        context.setFinished(false);

        log.debug("[PreprocessExecutor] 预处理结果: {}",context);
        return context;
    }
}
