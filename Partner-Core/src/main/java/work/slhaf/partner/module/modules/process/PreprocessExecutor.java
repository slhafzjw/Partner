package work.slhaf.partner.module.modules.process;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.core.perceive.pojo.User;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

@EqualsAndHashCode(callSuper = true)
@Data
public class PreprocessExecutor extends AbstractAgentModule.Running<PartnerRunningFlowContext> {
    private static final String INFO_PLATFORM = "platform";
    private static final String INFO_NICKNAME = "nickname";

    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;

    @Override
    public void execute(PartnerRunningFlowContext context) {
        getInteractionContext(context);
    }


    private void getInteractionContext(PartnerRunningFlowContext context) {
        log.debug("[PreprocessExecutor] 预处理原始输入: {}", context);
        String platform = context.getAdditionalUserInfo().getOrDefault(INFO_PLATFORM, "");
        String nickName = context.getAdditionalUserInfo().getOrDefault(INFO_NICKNAME, "");
        String sourceUserId = parseSourceUserId(context.getSource());
        User user = perceiveCapability.getUser(sourceUserId, platform);
        if (user == null) {
            user = perceiveCapability.addUser(sourceUserId, platform, nickName);
        }
        String userId = user.getUuid();
        String userStr = "[" + nickName + "(" + userId + ")]";
        log.debug("[PreprocessExecutor] 已识别用户: {} {}", userStr, context.getSource());
        log.debug("[PreprocessExecutor] 预处理结果: {}", context);
    }

    private String parseSourceUserId(String source) {
        int split = source.indexOf(':');
        if (split < 0 || split + 1 >= source.length()) {
            return source;
        }
        return source.substring(split + 1).trim();
    }

    @Override
    public int order() {
        return 1;
    }
}
