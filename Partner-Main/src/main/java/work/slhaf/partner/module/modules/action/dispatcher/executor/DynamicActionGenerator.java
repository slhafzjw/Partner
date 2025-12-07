package work.slhaf.partner.module.modules.action.dispatcher.executor;

import java.util.List;

import com.alibaba.fastjson2.JSONObject;

import lombok.Data;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.GeneratorInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.GeneratorResult;
import work.slhaf.partner.common.util.ExtractUtil;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionType;

/**
 * 负责依据输入内容生成可执行的动态行动单元，并选择是否持久化至 SandboxRunner 容器内
 */
@AgentSubModule
public class DynamicActionGenerator extends AgentRunningSubModule<GeneratorInput, GeneratorResult>
        implements ActivateModel {

    @Override
    public GeneratorResult execute(GeneratorInput input) {
        GeneratorResult result = new GeneratorResult();
        // 由于 SCRIPT 类型程序都是在 SandboxRunner 内部的磁盘上加载然后执行的，
        // 所以此处的输入内容也只需要指定输入参数、临时key、是否持久化即可，路径将按照指定规则统一构建，不可交给LLM生成
        String prompt = buildPrompt(input);
        // 响应结果需要包含几个特殊数据: 依赖项、代码内容、是否序列化、响应数据释义
        ChatResponse response = this.singleChat(prompt);
        GeneratorResponseData generatorData = JSONObject
                .parseObject(ExtractUtil.extractJson(response.getMessage()), GeneratorResponseData.class);
        MetaAction tempAction = buildAction(input);
        waitingSerialize(tempAction, generatorData);
        result.setTempAction(tempAction);
        return null;
    }

    /**
     * 将临时行动单元序列化至临时文件夹，并设置程序路径、放置在队列中，等待执行状态变化，并根据序列化选项选择是否补充 MetaActionInfo 并持久序列化
     */
    private void waitingSerialize(MetaAction tempAction, GeneratorResponseData generatorData) {

    }

    private MetaAction buildAction(GeneratorInput input) {
        MetaAction tempAction = new MetaAction();
        tempAction.setKey(input.getKey());
        tempAction.setParams(input.getParams());
        tempAction.setIo(true);
        tempAction.setOrder(-1);
        tempAction.setType(MetaActionType.SCRIPT);
        return tempAction;
    }

    private String buildPrompt(GeneratorInput data) {
        JSONObject prompt = new JSONObject();
        prompt.put("[行动描述]", data.getDescription());
        // prompt.putObject("[行动参数]").putAll(data.getParams());
        prompt.putObject("[行动参数描述]").putAll(data.getParamsDescription());
        return prompt.toString();
    }

    @Override
    public String modelKey() {
        return "dynamic_generator";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }

    @Data
    private class GeneratorResponseData {
        private List<String> dependencies;
        private String code;
        private boolean serialize;
        private JSONObject responseSchema;
    }
}
