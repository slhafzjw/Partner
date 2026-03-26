package work.slhaf.partner.module.action.executor.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.memory.pojo.ActivatedMemorySlice;

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
    private List<ActivatedMemorySlice> activatedMemorySlices;
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
