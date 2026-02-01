package work.slhaf.partner.module.modules.action.dispatcher.executor;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore.ExecutorType;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaAction.Result;
import work.slhaf.partner.core.action.entity.MetaAction.ResultStatus;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.GeneratorInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.GeneratorResult;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.RepairerInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.RepairerResult;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.RepairerResult.RepairerStatus;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 负责识别行动链的修复
 * <ol>
 * <li>
 * 可通过协调 {@link DynamicActionGenerator} 生成新的行动单元并调用，获取所需的参数信息（必要时持久化）;
 * </li>
 * <li>
 * 也可以直接调用已存在的行动程序获取信息;
 * </li>
 * <li>
 * 如果上述都无法满足，将发起自对话借助干预模块进行操作或者借助自对话通道向用户发起沟通请求，该请求的目的一般为行动程序生成/调用指导或者用户侧的信息补充，后续还需要再走一遍参数修复流程
 * </li>
 * </ol>
 */
@Slf4j
@AgentSubModule
public class ActionRepairer extends AgentRunningSubModule<RepairerInput, RepairerResult> implements ActivateModel {

    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private CognationCapability cognationCapability;

    @InjectModule
    private DynamicActionGenerator dynamicActionGenerator;

    private final AssemblyHelper assemblyHelper = new AssemblyHelper();
    private RunnerClient runnerClient;

    @Init
    void init() {
        runnerClient = actionCapability.runnerClient();
    }

    @Override
    public RepairerResult execute(RepairerInput data) {
        RepairerResult result;
        try {
            String prompt = assemblyHelper.buildPrompt(data, null);
            ChatResponse response = this.singleChat(prompt);
            RepairerData repairerData = JSONObject.parseObject(response.getMessage(), RepairerData.class);
            result = switch (repairerData.getRepairerType()) {
                case ACTION_GENERATION ->
                    handleActionGeneration(JSONObject.parseObject(repairerData.getData(), GeneratorInput.class));
                case ACTION_INVOCATION -> handleActionInvocation(
                        JSONObject.parseObject(repairerData.getData(), new TypeReference<List<String>>() {
                        }));
                case USER_INTERACTION -> handleUserInteraction(repairerData.getData());
            };
            if (!repairerData.getRepairerType().equals(RepairerType.USER_INTERACTION)
                    && result.getStatus().equals(RepairerResult.RepairerStatus.FAILED)) {
                log.warn("常规行动修复失败，将尝试自对话通道");
                prompt = assemblyHelper.buildPrompt(data, "常规行动修复失败，请尝试通过自对话通道获取必要的信息以完成行动参数的修复");
                response = this.singleChat(prompt);
                repairerData = JSONObject.parseObject(response.getMessage(), RepairerData.class);
                handleUserInteraction(repairerData.getData());
            }
        } catch (Exception e) {
            result = new RepairerResult();
            result.setStatus(RepairerStatus.FAILED);
        }
        return result;
    }

    /**
     * 负责根据输入内容进行行动单元的参数信息修复
     *
     * @param generatorInput 生成的行动单元参考内容，最好包含行动单元的执行逻辑
     * @return 修复后的行动单元结果
     */
    private RepairerResult handleActionGeneration(GeneratorInput generatorInput) {
        RepairerResult result = new RepairerResult();
        GeneratorResult generatorResult = dynamicActionGenerator.execute(generatorInput);
        MetaAction tempAction = generatorResult.getTempAction();
        if (tempAction == null) {
            result.setStatus(RepairerStatus.FAILED);
            return result;
        }

        runnerClient.submit(tempAction);
        // 根据 tempAction 的执行状态设置修复结果
        Result actionResult = tempAction.getResult();
        if (actionResult.getStatus() != ResultStatus.SUCCESS) {
            result.setStatus(RepairerStatus.FAILED);
            return result;
        }

        result.setStatus(RepairerStatus.OK);
        result.getFixedData().add(actionResult.getData());
        return result;
    }

    /**
     * 负责根据输入内容进行行动单元的参数信息修复
     *
     * @param actionKeys 需要调用的行动单元Key列表
     * @return 修复后的行动单元结果
     */
    private RepairerResult handleActionInvocation(List<String> actionKeys) {
        RepairerResult result = new RepairerResult();
        CountDownLatch latch = new CountDownLatch(actionKeys.size());
        ExecutorService virtual = actionCapability.getExecutor(ExecutorType.VIRTUAL);
        ExecutorService platform = actionCapability.getExecutor(ExecutorType.PLATFORM);
        ExecutorService executor;
        AtomicInteger failedCount = new AtomicInteger(0);
        for (String key : actionKeys) {
            MetaAction action = actionCapability.loadMetaAction(key);
            executor = action.isIo() ? virtual : platform;
            executor.execute(() -> {
                try {
                    runnerClient.submit(action);
                    result.getFixedData().add(action.getResult().getData());
                } catch (Exception e) {
                    log.error("行动单元执行失败: {}", key, e);
                    failedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (Exception e) {
            log.warn("CountDownLatch 已中断");
        }
        if (actionKeys.size() - failedCount.get() > 0) {
            result.setStatus(RepairerStatus.OK);
        } else {
            result.setStatus(RepairerStatus.FAILED);
        }
        return result;
    }

    private RepairerResult handleUserInteraction(String acquireContent) {
        RepairerResult result = new RepairerResult();
        result.setStatus(RepairerStatus.ACQUIRE);
        // 发送自对话请求
        return result;
    }

    @Override
    public String modelKey() {
        return "action_repairer";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    @Data
    private class RepairerData {
        private RepairerType repairerType;
        private String data;
    }

    private enum RepairerType {
        ACTION_GENERATION,
        ACTION_INVOCATION,
        USER_INTERACTION
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class AssemblyHelper {
        private AssemblyHelper() {
        }

        private String buildPrompt(RepairerInput data, String specialInstruction) {
            JSONObject prompt = new JSONObject();

            JSONObject actionData = prompt.putObject("[本次行动信息]");
            actionData.put("[行动描述]", data.getActionDescription());
            JSONObject actionParamsData = actionData.putObject("[行动参数说明]");
            actionParamsData.putAll(data.getParams());

            JSONArray historyData = prompt.putArray("[历史行动执行结果]");
            data.getHistoryActionResults().forEach(historyAction -> {
                JSONObject historyItem = new JSONObject();
                historyItem.put("[行动Key]", historyAction.getActionKey());
                historyItem.put("[行动描述]", historyAction.getDescription());
                historyItem.put("[行动结果]", historyAction.getResult());
                historyData.add(historyItem);
            });

            JSONArray messageData = prompt.putArray("[最近消息列表]");
            messageData.addAll(data.getRecentMessages());

            if (specialInstruction != null) {
                prompt.put("[特殊指令]", specialInstruction);
            }

            return prompt.toString();
        }

    }
}
