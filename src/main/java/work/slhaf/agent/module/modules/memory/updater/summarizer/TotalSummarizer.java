package work.slhaf.agent.module.modules.memory.updater.summarizer;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;

import java.util.HashMap;

import static work.slhaf.agent.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class TotalSummarizer extends Model {

    private static volatile TotalSummarizer totalSummarizer;

    public static TotalSummarizer getInstance() {
        if (totalSummarizer == null) {
            synchronized (TotalSummarizer.class) {
                if (totalSummarizer == null) {
                    totalSummarizer = new TotalSummarizer();
                    setModel(totalSummarizer, ModelConstant.Prompt.MEMORY, true);
                    totalSummarizer.updateChatClientSettings();
                }
            }
        }
        return totalSummarizer;
    }

    public String execute(HashMap<String, String> singleMemorySummary){
        ChatResponse response = this.singleChat(JSONUtil.toJsonPrettyStr(singleMemorySummary));
        return JSONObject.parseObject(extractJson(response.getMessage())).getString("content");
    }

    @Override
    protected String modelKey() {
        return "total_summarizer";
    }
}
