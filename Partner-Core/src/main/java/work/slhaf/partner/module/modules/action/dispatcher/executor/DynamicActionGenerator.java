package work.slhaf.partner.module.modules.action.dispatcher.executor;

import com.alibaba.fastjson2.JSONObject;
import lombok.val;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentSubModule;
import work.slhaf.partner.api.agent.factory.module.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.common.util.ExtractUtil;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.GeneratedData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.GeneratorInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.GeneratorResult;

/**
 * 负责依据输入内容生成可执行的动态行动单元，并选择是否持久化至 SandboxRunner 容器内
 */
@AgentSubModule
public class DynamicActionGenerator extends AbstractAgentSubModule<GeneratorInput, GeneratorResult>
        implements ActivateModel {

    @InjectCapability
    private ActionCapability actionCapability;

    private RunnerClient runnerClient;

    @Init
    void init() {
        runnerClient = actionCapability.runnerClient();
    }

    @Override
    public GeneratorResult execute(GeneratorInput input) {
        GeneratorResult result = new GeneratorResult();
        try {
            // 由于 SCRIPT 类型程序都是在 SandboxRunner 内部的磁盘上加载然后执行的，
            // 所以此处的输入内容也只需要指定输入参数、临时key、是否持久化即可，路径将按照指定规则统一构建，不可交给LLM生成
            String prompt = buildPrompt(input);

            // 响应结果需要包含几个特殊数据: 依赖项、代码内容、是否序列化、响应数据释义
            ChatResponse response = this.singleChat(prompt);
            GeneratedData generatorData = JSONObject
                    .parseObject(ExtractUtil.extractJson(response.getMessage()), GeneratedData.class);

            val location = runnerClient.buildTmpPath(input.getActionName(), generatorData.getCodeType());
            MetaAction tempAction = new MetaAction(
                    input.getActionName(),
                    true,
                    MetaAction.Type.ORIGIN,
                    location
            );
            // 将临时行动单元序列化至临时文件夹，并设置程序路径、放置在队列中，等待执行状态变化，并根据序列化选项选择是否补充 MetaActionInfo 并持久序列化
            // 通过 ActionCapability 暴露的接口，序列化至临时文件夹，同时返回Path对象并设置。队列建议交给 SandboxRunner
            // 持有，包括监听与序列化线程
            runnerClient.tmpSerialize(tempAction, generatorData.getCode(), generatorData.getCodeType());
            if (generatorData.isSerialize()) {
                waitingSerialize();
            }
            result.setTempAction(tempAction);
        } catch (Exception e) {
            result.setTempAction(null);
        }
        return result;
    }

    private void waitingSerialize() {
        throw new UnsupportedOperationException("Unimplemented method 'waitingSerialize'");
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
}
