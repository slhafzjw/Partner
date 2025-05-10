package work.slhaf.agent.modules.memory.selector.extractor;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.chat.pojo.MetaMessage;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.session.SessionManager;
import work.slhaf.agent.modules.memory.selector.extractor.data.ExtractorInput;
import work.slhaf.agent.modules.memory.selector.extractor.data.ExtractorResult;
import work.slhaf.agent.shared.memory.EvaluatedSlice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static work.slhaf.agent.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemorySelectExtractor extends Model {
    public static final String MODEL_KEY = "topic_extractor";
    private static MemorySelectExtractor memorySelectExtractor;

    private MemoryManager memoryManager;
    private SessionManager sessionManager;

    private MemorySelectExtractor() {
    }

    public static MemorySelectExtractor getInstance() throws IOException, ClassNotFoundException {
        if (memorySelectExtractor == null) {
            Config config = Config.getConfig();
            memorySelectExtractor = new MemorySelectExtractor();
            memorySelectExtractor.setMemoryManager(MemoryManager.getInstance());
            memorySelectExtractor.setSessionManager(SessionManager.getInstance());
            setModel(config, memorySelectExtractor, MODEL_KEY, ModelConstant.SELECT_EXTRACTOR_PROMPT);
        }

        return memorySelectExtractor;
    }

    public ExtractorResult execute(InteractionContext context) {
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

        List<EvaluatedSlice> activatedMemorySlices = memoryManager.getActivatedSlices().get(context.getUserId());

        ExtractorInput extractorInput = ExtractorInput.builder()
                .text(context.getInput())
                .date(context.getDateTime().toLocalDate())
                .history(chatMessages)
                .topic_tree(memoryManager.getTopicTree())
                .activatedMemorySlices(activatedMemorySlices)
                .build();
        String responseStr = extractJson(singleChat(JSONUtil.toJsonPrettyStr(extractorInput)).getMessage());

        ExtractorResult extractorResult;
        try {
            extractorResult = JSONObject.parseObject(responseStr, ExtractorResult.class);
        } catch (Exception e) {
            log.error("主题提取出错: {}", e.getLocalizedMessage());
            extractorResult = new ExtractorResult();
            extractorResult.setRecall(false);
            extractorResult.setMatches(List.of());
        }
        return extractorResult;
    }

    public static class Constant {
        public static final String NONE = "none";
        public static final String DATE = "date";
        public static final String TOPIC = "topic";
    }

}
