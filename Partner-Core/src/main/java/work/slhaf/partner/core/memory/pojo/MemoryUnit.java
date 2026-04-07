package work.slhaf.partner.core.memory.pojo;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.state.State;
import work.slhaf.partner.framework.agent.state.StateSerializable;
import work.slhaf.partner.framework.agent.state.StateValue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class MemoryUnit implements StateSerializable {

    private final String id;
    private final List<Message> conversationMessages = new ArrayList<>();
    private Long timestamp = 0L;
    private final List<MemorySlice> slices = new ArrayList<>();

    public MemoryUnit(String id) {
        this.id = id;
        this.register();
    }

    public void updateTimestamp() {
        timestamp = System.currentTimeMillis();
    }

    @Override
    public @NotNull Path statePath() {
        return Path.of("core", "memory", "memory-unit" + id + ".json");
    }

    @Override
    public void load(@NotNull JSONObject state) {
        Long loadedTimestamp = state.getLong("update_timestamp");
        this.timestamp = loadedTimestamp != null ? loadedTimestamp : 0L;

        this.conversationMessages.clear();
        this.slices.clear();

        JSONArray messageArray = state.getJSONArray("conversation_messages");
        if (messageArray != null) {
            for (int i = 0; i < messageArray.size(); i++) {
                JSONObject messageObject = messageArray.getJSONObject(i);
                if (messageObject == null) {
                    continue;
                }

                String role = messageObject.getString("role");
                String content = messageObject.getString("content");
                if (role == null || content == null) {
                    continue;
                }

                Message message = new Message(Message.Character.fromValue(role), content);

                this.conversationMessages.add(message);
            }
        }

        var sliceArray = state.getJSONArray("memory_slices");
        if (sliceArray != null) {
            for (int i = 0; i < sliceArray.size(); i++) {
                JSONObject sliceObject = sliceArray.getJSONObject(i);
                if (sliceObject == null) {
                    continue;
                }

                String sliceId = sliceObject.getString("id");
                Integer startIndex = sliceObject.getInteger("start_index");
                Integer endIndex = sliceObject.getInteger("end_index");
                String summary = sliceObject.getString("summary");
                Long createdTimestamp = sliceObject.getLong("created_timestamp");

                if (sliceId == null || startIndex == null || endIndex == null || summary == null || createdTimestamp == null) {
                    continue;
                }

                MemorySlice slice = MemorySlice.restore(sliceId, startIndex, endIndex, summary, createdTimestamp);

                this.slices.add(slice);
            }
        }
    }

    @Override
    public @NotNull State convert() {
        State state = new State();
        state.append("id", StateValue.str(id));
        state.append("update_timestamp", StateValue.num(timestamp));

        List<StateValue.Obj> convertedMessageList = conversationMessages.stream().map(message -> {
            Map<String, StateValue> convertedMap = Map.of(
                    "role", StateValue.str(message.roleValue()),
                    "content", StateValue.str(message.getContent())
            );
            return StateValue.obj(convertedMap);
        }).toList();
        state.append("conversation_messages", StateValue.arr(convertedMessageList));

        List<StateValue.Obj> convertedSliceList = slices.stream().map(slice -> {
            Map<String, StateValue> convertedMap = Map.of(
                    "id", StateValue.str(slice.getId()),
                    "start_index", StateValue.num(slice.getStartIndex()),
                    "end_index", StateValue.num(slice.getEndIndex()),
                    "summary", StateValue.str(slice.getSummary()),
                    "created_timestamp", StateValue.num(slice.getTimestamp())
            );
            return StateValue.obj(convertedMap);
        }).toList();
        state.append("memory_slices", StateValue.arr(convertedSliceList));
        return state;
    }

    @Override
    public boolean autoLoadOnRegister() {
        return false;
    }
}
