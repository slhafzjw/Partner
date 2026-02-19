package work.slhaf.partner.module.modules.memory.updater.summarizer;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.module.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.factory.module.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.chat.pojo.ChatResponse;

import java.util.HashMap;

import static work.slhaf.partner.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@AgentSubModule
public class TotalSummarizer extends AgentRunningSubModule<HashMap<String, String>, String> implements ActivateModel {

    @Init
    public void init() {
        updateChatClientSettings();
    }

    public String execute(HashMap<String, String> singleMemorySummary){
        ChatResponse response = this.singleChat(JSONUtil.toJsonPrettyStr(singleMemorySummary));
        return JSONObject.parseObject(extractJson(response.getMessage())).getString("content");
    }

    @Override
    public String modelKey() {
        return "total_summarizer";
    }

    @Override
    public boolean withBasicPrompt() {
        return true;
    }

}
