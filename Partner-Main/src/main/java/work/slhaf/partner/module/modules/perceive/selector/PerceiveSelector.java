package work.slhaf.partner.module.modules.perceive.selector;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.core.submodule.perceive.PerceiveCapability;
import work.slhaf.partner.core.submodule.perceive.pojo.User;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;
import work.slhaf.partner.module.common.module.PreRunningModule;

import java.io.IOException;
import java.util.HashMap;

@Slf4j
@Setter
public class PerceiveSelector extends PreRunningModule {

    private static volatile PerceiveSelector perceiveSelector;

    @InjectCapability
    private PerceiveCapability perceiveCapability;

    public static PerceiveSelector getInstance() throws IOException, ClassNotFoundException {
        if (perceiveSelector == null) {
            synchronized (PerceiveSelector.class) {
                if (perceiveSelector == null) {
                    perceiveSelector = new PerceiveSelector();
                }
            }
        }
        return perceiveSelector;
    }

    @Override
    public void execute(PartnerRunningFlowContext context) throws IOException, ClassNotFoundException {
        log.debug("[PerceiveSelector] 感知模块处理流程开始...");
        //处理思路: 根据用户id,查询用户相关身份感知数据，直接添加到appendPrompt中，这直接执行appendPrompt方法应该可以
        setAppendedPrompt(context);
        setActiveModule(context);
        log.debug("[PerceiveSelector] 感知模块处理流程结束...");
    }

    @Override
    protected HashMap<String, String> getPromptDataMap(String userId) {
        HashMap<String, String> map = new HashMap<>();
        User user = perceiveCapability.getUser(userId);
        map.put("[关系] <你与最新聊天用户的关系>", user.getRelation());
        map.put("[态度] <你对于最新聊天用户的态度>", user.getAttitude().toString());
        map.put("[印象] <你对于最新聊天用户的印象>", user.getImpressions().toString());
        map.put("[静态记忆] <你关于最新聊天用户的静态记忆>", user.getStaticMemory().toString());
        return map;
    }

    @Override
    public String moduleName() {
        return "[感知模块]";
    }
}
