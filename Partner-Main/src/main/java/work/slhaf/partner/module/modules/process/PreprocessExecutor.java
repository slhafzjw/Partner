package work.slhaf.partner.module.modules.process;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.factory.capability.annotation.CapabilityHolder;
import work.slhaf.partner.api.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.core.cognation.cognation.CognationCapability;
import work.slhaf.partner.core.cognation.submodule.perceive.PerceiveCapability;
import work.slhaf.partner.core.cognation.submodule.perceive.pojo.User;
import work.slhaf.partner.core.interaction.data.InteractionInputData;
import work.slhaf.partner.core.interaction.data.context.InteractionContext;
import work.slhaf.partner.core.session.SessionManager;
import work.slhaf.partner.module.common.entity.AppendPromptData;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

@Data
@Slf4j
@CapabilityHolder
public class PreprocessExecutor {

    private static volatile PreprocessExecutor preprocessExecutor;

    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;
    private SessionManager sessionManager;

    private PreprocessExecutor() {
    }

    public static PreprocessExecutor getInstance() throws IOException, ClassNotFoundException {
        if (preprocessExecutor == null) {
            synchronized (PreprocessExecutor.class) {
                if (preprocessExecutor == null) {
                    preprocessExecutor = new PreprocessExecutor();
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
        if (currentMemoryId == null || cognationCapability.getChatMessages().isEmpty()) {
            sessionManager.refreshMemoryId();
        }
    }

    private InteractionContext getInteractionContext(InteractionInputData inputData) {
        log.debug("[PreprocessExecutor] 预处理原始输入: {}", inputData);
        InteractionContext context = new InteractionContext();

        User user = perceiveCapability.getUser(inputData.getUserInfo(), inputData.getPlatform());
        if (user == null) {
            user = perceiveCapability.addUser(inputData.getUserInfo(), inputData.getPlatform(), inputData.getUserNickName());
        }
        String userId = user.getUuid();
        context.setUserId(userId);
        context.setUserNickname(inputData.getUserNickName());
        context.setUserInfo(inputData.getUserInfo());
        context.setDateTime(inputData.getLocalDateTime());
        context.setSingle(inputData.isSingle());

        String userStr = "[" + inputData.getUserNickName() + "(" + userId + ")]";
        String input = userStr + " " + inputData.getContent();
        context.setInput(input);

        setAppendedPrompt(context);
        setCoreContext(inputData, context, input, userId);

        log.debug("[PreprocessExecutor] 预处理结果: {}", context);
        return context;
    }

    private void setAppendedPrompt(InteractionContext context) {
        HashMap<String, String> map = new HashMap<>();
        map.put("text", "这部分才是真正的用户输入内容, 就像你之前收到过的输入一样。但...不会是'同一个人'。");
        map.put("datetime", "本次用户输入对应的当前时间");
        map.put("user_nick", "用户昵称");
        map.put("user_id", "用户id, 与user_nick区分, 这是用户的唯一标识");
        map.put("active_modules", "已激活的模块, 为false时为激活但未活跃; 为true时为激活且活跃");
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
