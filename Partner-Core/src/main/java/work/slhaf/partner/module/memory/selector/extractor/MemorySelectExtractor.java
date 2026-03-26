package work.slhaf.partner.module.memory.selector.extractor;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.ActivatedMemorySlice;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorMatchData;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.List;

import static work.slhaf.partner.common.util.ExtractUtil.fixTopicPath;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySelectExtractor extends AbstractAgentModule.Sub<PartnerRunningFlowContext, ExtractorResult>
        implements ActivateModel {
    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectModule
    private MemoryRuntime memoryRuntime;

    @Override
    public ExtractorResult execute(PartnerRunningFlowContext context) {
        log.debug("[MemorySelectExtractor] 主题提取模块开始...");
        List<Message> chatMessages = cognitionCapability.snapshotChatMessages();
        ExtractorResult extractorResult;
        try {
            List<ActivatedMemorySlice> activatedMemorySlices = memoryCapability.getActivatedSlices();
            ExtractorInput extractorInput = ExtractorInput.builder()
                    .text(context.getInput())
                    .date(context.getInfo().getDateTime().toLocalDate())
                    .history(chatMessages)
                    .topic_tree(memoryRuntime.getTopicTree())
                    .activatedMemorySlices(activatedMemorySlices)
                    .build();
            log.debug("[MemorySelectExtractor] 主题提取输入: {}", JSONUtil.toJsonStr(extractorInput));
            extractorResult = formattedChat(
                    List.of(new Message(Message.Character.USER, JSONUtil.toJsonPrettyStr(extractorInput))),
                    ExtractorResult.class
            );
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
}
