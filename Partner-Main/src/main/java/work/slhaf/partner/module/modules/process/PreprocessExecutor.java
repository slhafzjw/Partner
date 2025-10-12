package work.slhaf.partner.module.modules.process;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.core.perceive.pojo.User;
import work.slhaf.partner.module.common.module.PreRunningModule;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;
import work.slhaf.partner.runtime.interaction.data.context.subcontext.CoreContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@AgentModule(name = "preprocess_executor", order = 1)
public class PreprocessExecutor extends PreRunningModule {

    private static volatile PreprocessExecutor preprocessExecutor;

    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;

    @Override
    public void doExecute(PartnerRunningFlowContext context) {
        checkAndSetMemoryId();
        getInteractionContext(context);
    }

    private void checkAndSetMemoryId() {
        String currentMemoryId = cognationCapability.getCurrentMemoryId();
        if (currentMemoryId == null || cognationCapability.getChatMessages().isEmpty()) {
            cognationCapability.refreshMemoryId();
        }
    }

    private void getInteractionContext(PartnerRunningFlowContext context) {
        log.debug("[PreprocessExecutor] 预处理原始输入: {}", context);
        User user = perceiveCapability.getUser(context.getUserInfo(), context.getPlatform());
        if (user == null) {
            user = perceiveCapability.addUser(context.getUserInfo(), context.getPlatform(), context.getUserNickname());
        }
        String userId = user.getUuid();
        context.setUserId(userId);

        String userStr = "[" + context.getUserNickname() + "(" + userId + ")]";
        String input = userStr + " " + context.getInput();
        context.setInput(input);
        setCoreContext(context);
        log.debug("[PreprocessExecutor] 预处理结果: {}", context);
    }


    @Override
    protected HashMap<String, String> getPromptDataMap(String userId) {
        HashMap<String, String> map = new HashMap<>();
        map.put("text", "这部分才是真正的用户输入内容, 就像你之前收到过的输入一样。但...不会是'同一个人'。");
        map.put("datetime", "本次用户输入对应的当前时间");
        map.put("user_nick", "用户昵称");
        map.put("user_id", "用户id, 与user_nick区分, 这是用户的唯一标识");
        map.put("active_modules", "已激活的模块, 为false时为激活但未活跃; 为true时为激活且活跃");
        map.put("其他", "历史对话中将在用户消息的最后一行标注时间");
        return map;
    }

    @Override
    protected String moduleName() {
        return "[基础模块]";
    }

    private void setCoreContext(PartnerRunningFlowContext context) {
        CoreContext coreContext = context.getCoreContext();
        coreContext.setText(context.getInput());
        coreContext.setDateTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        coreContext.setUserNick(context.getUserNickname());
        coreContext.setUserId(context.getUserId());
    }
}
