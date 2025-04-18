package work.slhaf.agent.core.interaction.data;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.core.memory.pojo.MemorySlice;
import work.slhaf.agent.modules.task.data.TaskData;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InteractionContext {
    protected String userInfo;
    protected String userNickname;
    protected LocalDateTime dateTime;

    protected boolean finished;
    protected String input;
    protected JSONObject tempResult;
    protected ChatResponse coreResponse;

    protected List<MemorySlice> memorySlices;
    protected List<String> topicPath;
    protected List<TaskData> taskDataList;
}
