package work.slhaf.partner.module.modules.memory.selector.extractor;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.api.common.chat.pojo.MetaMessage;
import work.slhaf.partner.api.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.flow.abstracts.AgentInteractionSubModule;
import work.slhaf.partner.common.exception_handler.GlobalExceptionHandler;
import work.slhaf.partner.common.exception_handler.pojo.GlobalException;
import work.slhaf.partner.core.cognation.cognation.CognationCapability;
import work.slhaf.partner.core.cognation.submodule.memory.MemoryCapability;
import work.slhaf.partner.core.cognation.submodule.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.core.interaction.data.context.InteractionContext;
import work.slhaf.partner.core.session.SessionManager;
import work.slhaf.partner.module.common.model.ModelConstant;
import work.slhaf.partner.module.modules.memory.selector.extractor.data.ExtractorInput;
import work.slhaf.partner.module.modules.memory.selector.extractor.data.ExtractorMatchData;
import work.slhaf.partner.module.modules.memory.selector.extractor.data.ExtractorResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static work.slhaf.partner.common.util.ExtractUtil.extractJson;
import static work.slhaf.partner.common.util.ExtractUtil.fixTopicPath;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemorySelectExtractor extends AgentInteractionSubModule<InteractionContext, ExtractorResult> implements ActivateModel {

    private static volatile MemorySelectExtractor memorySelectExtractor;

    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognationCapability cognationCapability;
    private SessionManager sessionManager;

    private MemorySelectExtractor() {
        modelSettings();
    }

    public static MemorySelectExtractor getInstance() throws IOException, ClassNotFoundException {
        if (memorySelectExtractor == null) {
            synchronized (MemorySelectExtractor.class) {
                if (memorySelectExtractor == null) {
                    memorySelectExtractor = new MemorySelectExtractor();
                    memorySelectExtractor.setSessionManager(SessionManager.getInstance());
                }
            }
        }
        return memorySelectExtractor;
    }

    @Override
    public ExtractorResult execute(InteractionContext context) {
        log.debug("[MemorySelectExtractor] 主题提取模块开始...");
        //结构化为指定格式
        List<Message> chatMessages = new ArrayList<>();
        List<MetaMessage> metaMessages = sessionManager.getSingleMetaMessageMap().get(context.getUserId());
        if (metaMessages == null) {
            sessionManager.getSingleMetaMessageMap().put(context.getUserId(), new ArrayList<>());
        } else {
            for (MetaMessage metaMessage : metaMessages) {
                chatMessages.add(metaMessage.getUserMessage());
                chatMessages.add(metaMessage.getAssistantMessage());
            }
        }

        ExtractorResult extractorResult;
        try {
            List<EvaluatedSlice> activatedMemorySlices = cognationCapability.getActivatedSlices(context.getUserId());
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
            GlobalExceptionHandler.writeExceptionState(new GlobalException(e.getLocalizedMessage()));
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
        extractorResult.getMatches().removeIf(m -> m.getText().split("->")[0].isEmpty());
        return extractorResult;
    }

    @Override
    public String modelKey() {
        return "topic_extractor";
    }

    @Override
    public boolean withAwareness() {
        return false;
    }

    @Override
    public String promptModule() {
        return ModelConstant.Prompt.MEMORY;
    }
}
