package work.slhaf.partner.module.modules.memory.updater.summarizer;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.module.modules.memory.updater.summarizer.data.SummarizeInput;
import work.slhaf.partner.module.modules.memory.updater.summarizer.data.SummarizeResult;

import java.util.HashMap;

@Data
@Slf4j
public class MemorySummarizer {

    private static volatile MemorySummarizer memorySummarizer;
    public static final String MODEL_KEY = "memory_summarizer";

    private InteractionThreadPoolExecutor executor;
    private SingleSummarizer singleSummarizer;
    private MultiSummarizer multiSummarizer;
    private TotalSummarizer totalSummarizer;

    public static MemorySummarizer getInstance() {
        if (memorySummarizer == null) {
            synchronized (MemorySummarizer.class) {
                if (memorySummarizer == null) {
                    memorySummarizer = new MemorySummarizer();
                    memorySummarizer.setExecutor(InteractionThreadPoolExecutor.getInstance());
                    memorySummarizer.setSingleSummarizer(SingleSummarizer.getInstance());
                    memorySummarizer.setMultiSummarizer(MultiSummarizer.getInstance());
                    memorySummarizer.setTotalSummarizer(TotalSummarizer.getInstance());
                }
            }
        }
        return memorySummarizer;
    }

    public SummarizeResult execute(SummarizeInput input) {
        //进行长文本批量摘要
        singleSummarizer.execute(input.getChatMessages());
        //进行整体摘要并返回结果
        return multiSummarizer.execute(input);
    }

    public String executeTotalSummary(HashMap<String, String> singleMemorySummary) {
        return totalSummarizer.execute(singleMemorySummary);
    }
}
