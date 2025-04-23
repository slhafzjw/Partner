package work.slhaf.agent.modules.memory;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.modules.memory.data.extractor.ExtractorInput;
import work.slhaf.agent.modules.memory.data.extractor.ExtractorResult;

import java.io.IOException;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemorySelectExtractor extends Model {
    public static final String MODEL_KEY = "topic_extractor";
    private static MemorySelectExtractor memorySelectExtractor;

    private MemoryManager memoryManager;

    private MemorySelectExtractor() {
    }

    public static MemorySelectExtractor getInstance() throws IOException, ClassNotFoundException {
        if (memorySelectExtractor == null) {
            Config config = Config.getConfig();
            memorySelectExtractor = new MemorySelectExtractor();
            memorySelectExtractor.setMemoryManager(MemoryManager.getInstance());
            setModel(config, memorySelectExtractor, MODEL_KEY, ModelConstant.TOPIC_EXTRACTOR_PROMPT);
        }

        return memorySelectExtractor;
    }

    public ExtractorResult execute(InteractionContext context) {
        //结构化为指定格式
        ExtractorInput extractorInput = ExtractorInput.builder()
                .text(context.getInput())
                .date(context.getDateTime().toLocalDate())
                .history(memoryManager.getChatMessages())
                .topic_tree(memoryManager.getTopicTree())
                .build();
        String responseStr = singleChat(JSONUtil.toJsonPrettyStr(extractorInput)).getMessage();

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
