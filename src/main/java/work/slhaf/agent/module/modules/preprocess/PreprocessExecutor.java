package work.slhaf.agent.module.modules.preprocess;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.core.interaction.data.InteractionInputData;
import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.session.SessionManager;
import work.slhaf.agent.module.common.AppendPromptData;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

@Data
@Slf4j
public class PreprocessExecutor {

    private static volatile PreprocessExecutor preprocessExecutor;

    private MemoryManager memoryManager;
    private SessionManager sessionManager;

    private PreprocessExecutor() {
    }

    public static PreprocessExecutor getInstance() throws IOException, ClassNotFoundException {
        if (preprocessExecutor == null) {
            synchronized (PreprocessExecutor.class) {
                if (preprocessExecutor == null) {
                    preprocessExecutor = new PreprocessExecutor();
                    preprocessExecutor.setMemoryManager(MemoryManager.getInstance());
                    preprocessExecutor.setSessionManager(SessionManager.getInstance());
                }
            }
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
        log.debug("[PreprocessExecutor] 预处理原始输入: {}", inputData);
        InteractionContext context = new InteractionContext();

        String userId = memoryManager.getUserId(inputData.getUserInfo(), inputData.getUserNickName());
        context.setUserId(userId);
        context.setUserNickname(inputData.getUserNickName());
        context.setUserInfo(inputData.getUserInfo());
        context.setDateTime(inputData.getLocalDateTime());
        context.setSingle(inputData.isSingle());

        String user = "[" + inputData.getUserNickName() + "(" + userId + ")]";
        String input = user + " " + inputData.getContent();
        context.setInput(input);

        setAppendedPrompt(context);
        setCoreContext(inputData, context, input, userId);

        log.debug("[PreprocessExecutor] 预处理结果: {}", context);
        return context;
    }

    private void setAppendedPrompt(InteractionContext context) {
        HashMap<String, String> map = new HashMap<>();
        map.put("text", "用户输入内容");
        map.put("datetime", "本次用户输入对应的当前时间");
        map.put("user_nick", "用户昵称");
        map.put("user_id", "用户id, 与user_nick区分, 这是用户的唯一标识");
        map.put("active_modules","已激活的模块, 为false时为激活但未活跃; 为true时为激活且活跃");
        map.put("其他", "历史对话中将在用户消息的最后一行标注时间");
        AppendPromptData data = new AppendPromptData();
        data.setModuleName("[基础模块]");
        data.setAppendedPrompt(map);
        context.setAppendedPrompt(data);
    }

    private void setCoreContext(InteractionInputData inputData, InteractionContext context, String input, String userId) {
        context.getCoreContext().setText(input);
        context.getCoreContext().setDateTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        context.getCoreContext().setUserNick(inputData.getUserNickName());
        context.getCoreContext().setUserId(userId);
    }
}
