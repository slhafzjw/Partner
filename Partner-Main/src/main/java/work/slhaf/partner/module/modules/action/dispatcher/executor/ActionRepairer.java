package work.slhaf.partner.module.modules.action.dispatcher.executor;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.RepairerInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.RepairerResult;

/**
 * 负责识别行动链的修复
 * <ol>
 *     <li>
 *         可通过协调 {@link  DynamicActionGenerator} 生成新的行动单元并调用，获取所需的参数信息（必要时持久化）;
 *     </li>
 *     <li>
 *         也可以直接调用已存在的行动程序获取信息;
 *     </li>
 *     <li>
 *         如果上述都无法满足，将发起自对话借助干预模块进行操作或者借助自对话通道向用户发起沟通请求，该请求的目的一般为行动程序生成/调用指导或者用户侧的信息补充，后续还需要再走一遍参数修复流程
 *     </li>
 * </ol>
 */
@AgentSubModule
public class ActionRepairer extends AgentRunningSubModule<RepairerInput, RepairerResult> implements ActivateModel {

    @Override
    public RepairerResult execute(RepairerInput data) {
        return null;
    }

    @Override
    public String modelKey() {
        return "";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
