package work.slhaf.partner.module.modules.action.dispatcher.executor.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;

import java.util.List;

@Data
public class ExtractorInput {
    /**
     * 目标 MetaActionInfo
     */
    private MetaActionInfo metaActionInfo;
    /**
     * 可参考的记忆切片
     */
    private List<EvaluatedSlice> evaluatedSlices;
    /**
     * 历史行动执行结果
     */
    private List<HistoryAction> historyActionResults;
    /**
     * 最近的消息列表
     */
    private List<Message> recentMessages;
    /**
     * 额外的上下文信息（可来自修复器等）
     */
    private List<String> additionalContext;
}
