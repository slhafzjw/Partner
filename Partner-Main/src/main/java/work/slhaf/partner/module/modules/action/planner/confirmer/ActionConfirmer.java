package work.slhaf.partner.module.modules.action.planner.confirmer;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.ConfirmerInput;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.ConfirmerResult;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import static work.slhaf.partner.common.util.ExtractUtil.extractJson;

@Slf4j
@AgentSubModule
public class ActionConfirmer extends AgentRunningSubModule<ConfirmerInput, ConfirmerResult> implements ActivateModel {

    @InjectCapability
    private ActionCapability actionCapability;

    @Override
    public ConfirmerResult execute(ConfirmerInput data) {
        List<ActionData> actionDataList = data.getActionData();
        ExecutorService executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        CountDownLatch latch = new CountDownLatch(actionDataList.size());

        ConfirmerResult result = new ConfirmerResult();
        List<String> uuids = result.getUuids();

        for (ActionData actionData : actionDataList) {
            executor.execute(() -> {
                try {
                    String prompt = buildPrompt(actionData, data.getInput(), data.getRecentMessages());
                    ChatResponse response = this.singleChat(prompt);
                    JSONObject tempResult = JSONObject.parseObject(extractJson(response.getMessage()));
                    if (tempResult.getBoolean("confirmed")) {
                        actionData.setStatus(ActionData.ActionStatus.PREPARE);
                        synchronized (uuids) {
                            uuids.add(actionData.getUuid());
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.warn("CountDownLatch阻塞已中断");
        }
        return null;
    }

    private String buildPrompt(ActionData data, String input, List<Message> recentMessages) {
        JSONObject prompt = new JSONObject();
        prompt.put("[用户输入]", input);

        JSONObject actionData = prompt.putObject("[行动数据]");
        actionData.put("[行动倾向]", data.getTendency());
        actionData.put("[行动原因]", data.getReason());
        actionData.put("[行动来源]", data.getSource());
        actionData.put("[行动描述]", data.getDescription());

        JSONArray messageData = prompt.putArray("[近期对话]");
        messageData.addAll(recentMessages);

        return prompt.toString();
    }

    @Override
    public String modelKey() {
        return "action-confirmer";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
