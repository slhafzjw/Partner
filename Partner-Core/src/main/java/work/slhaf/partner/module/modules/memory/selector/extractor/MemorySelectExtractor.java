package work.slhaf.partner.module.modules.memory.selector.extractor;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.module.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.pojo.MetaMessage;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.module.modules.memory.selector.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.modules.memory.selector.extractor.entity.ExtractorMatchData;
import work.slhaf.partner.module.modules.memory.selector.extractor.entity.ExtractorResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.ArrayList;
import java.util.List;

import static work.slhaf.partner.common.util.ExtractUtil.extractJson;
import static work.slhaf.partner.common.util.ExtractUtil.fixTopicPath;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySelectExtractor extends AbstractAgentModule.Sub<PartnerRunningFlowContext, ExtractorResult>
        implements ActivateModel {
    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognationCapability cognationCapability;

    @Override
    public ExtractorResult execute(PartnerRunningFlowContext context) {
        log.debug("[MemorySelectExtractor] 主题提取模块开始...");
        // 结构化为指定格式
        List<Message> chatMessages = new ArrayList<>();
        List<MetaMessage> metaMessages = cognationCapability.getSingleMetaMessageMap().get(context.getUserId());
        if (metaMessages == null) {
            cognationCapability.getSingleMetaMessageMap().put(context.getUserId(), new ArrayList<>());
        } else {
            for (MetaMessage metaMessage : metaMessages) {
                chatMessages.add(metaMessage.getUserMessage());
                chatMessages.add(metaMessage.getAssistantMessage());
            }
        }
        ExtractorResult extractorResult;
        try {
            List<EvaluatedSlice> activatedMemorySlices = memoryCapability.getActivatedSlices(context.getUserId());
            ExtractorInput extractorInput = ExtractorInput.builder()
                    .text(context.getInput())
                    .date(context.getDateTime().toLocalDate())
                    .history(chatMessages)
                    .topic_tree(memoryCapability.getTopicTree())
                    .activatedMemorySlices(activatedMemorySlices)
                    .build();
            log.debug("[MemorySelectExtractor] 主题提取输入: {}", JSONObject.toJSONString(extractorInput));
            String responseStr = extractJson(singleChat(JSONUtil.toJsonPrettyStr(extractorInput)).getMessage());
            extractorResult = JSONObject.parseObject(responseStr, ExtractorResult.class);
            log.debug("[MemorySelectExtractor] 主题提取结果: {}", extractorResult);
        } catch (Exception e) {
            log.error("[MemorySelectExtractor] 主题提取出错: ", e);
            extractorResult = new ExtractorResult();
            extractorResult.setRecall(false);
            extractorResult.setMatches(List.of());
        }
        return fix(extractorResult);
    }

    private ExtractorResult fix(ExtractorResult extractorResult) {
        extractorResult.getMatches().forEach(m -> {
            if (m.getType().equals(ExtractorMatchData.Constant.DATE)) {
                return;
            }
            m.setText(fixTopicPath(m.getText()));
        });
        if (extractorResult.getMatches().isEmpty()) {
            return extractorResult;
        }
        extractorResult.getMatches().removeIf(m -> m.getText().split("->")[0].isEmpty());
        return extractorResult;
    }

    @Override
    public String modelKey() {
        return "topic_extractor";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
