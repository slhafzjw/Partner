package work.slhaf.partner.module.memory.runtime;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.core.memory.pojo.SliceRef;
import work.slhaf.partner.framework.agent.state.State;
import work.slhaf.partner.framework.agent.state.StateValue;
import work.slhaf.partner.module.memory.pojo.ActivationProfile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
final class MemoryRuntimeStateCodec {

    void load(JSONObject state, TopicMemoryIndex topicIndex, DateMemoryIndex dateIndex) {
        topicIndex.reset();
        dateIndex.reset();

        JSONArray topicSlicesArray = state.getJSONArray("topic_slices");
        if (topicSlicesArray != null) {
            for (int i = 0; i < topicSlicesArray.size(); i++) {
                JSONObject topicObject = topicSlicesArray.getJSONObject(i);
                if (topicObject == null) {
                    continue;
                }
                String topicPath = topicObject.getString("topic_path");
                if (topicPath == null) {
                    continue;
                }
                topicIndex.ensureTopicPath(topicPath);
                decodeTopicBindings(topicIndex, topicPath, topicObject.getJSONArray("bindings"));
            }
        }

        JSONArray dateIndexArray = state.getJSONArray("date_index");
        if (dateIndexArray != null) {
            for (int i = 0; i < dateIndexArray.size(); i++) {
                JSONObject dateObject = dateIndexArray.getJSONObject(i);
                if (dateObject == null) {
                    continue;
                }
                String date = dateObject.getString("date");
                if (date == null) {
                    continue;
                }
                try {
                    dateIndex.restore(LocalDate.parse(date), decodeSliceRefs(dateObject.getJSONArray("refs")));
                } catch (Exception e) {
                    log.warn("skip invalid date index: {}", date, e);
                }
            }
        }
    }

    State convert(TopicMemoryIndex topicIndex, DateMemoryIndex dateIndex) {
        State state = new State();

        List<StateValue.Obj> topicSliceStates = new ArrayList<>();
        for (Map.Entry<String, TopicMemoryIndex.TopicTreeNode> entry : topicIndex.roots().entrySet()) {
            collectTopicStates(entry.getKey(), entry.getValue(), topicSliceStates);
        }
        state.append("topic_slices", StateValue.arr(topicSliceStates));

        List<StateValue.Obj> dateIndexStates = dateIndex.entries().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> StateValue.obj(Map.of(
                        "date", StateValue.str(entry.getKey().toString()),
                        "refs", StateValue.arr(encodeSliceRefs(entry.getValue()))
                )))
                .toList();
        state.append("date_index", StateValue.arr(dateIndexStates));

        return state;
    }

    private void collectTopicStates(String path,
                                    TopicMemoryIndex.TopicTreeNode topicNode,
                                    List<StateValue.Obj> topicStates) {
        topicStates.add(StateValue.obj(Map.of(
                "topic_path", StateValue.str(path),
                "bindings", StateValue.arr(encodeTopicBindings(topicNode.bindings()))
        )));
        for (Map.Entry<String, TopicMemoryIndex.TopicTreeNode> childEntry : topicNode.children().entrySet()) {
            collectTopicStates(path + "->" + childEntry.getKey(), childEntry.getValue(), topicStates);
        }
    }

    private List<StateValue> encodeTopicBindings(List<TopicMemoryIndex.TopicBinding> bindings) {
        return bindings.stream()
                .map(binding -> (StateValue) StateValue.obj(Map.of(
                        "unit_id", StateValue.str(binding.sliceRef().getUnitId()),
                        "slice_id", StateValue.str(binding.sliceRef().getSliceId()),
                        "timestamp", StateValue.num(binding.timestamp()),
                        "activation_profile", StateValue.obj(Map.of(
                                "activation_weight", StateValue.num(binding.activationProfile().getActivationWeight()),
                                "diffusion_weight", StateValue.num(binding.activationProfile().getDiffusionWeight()),
                                "context_independence_weight",
                                StateValue.num(binding.activationProfile().getContextIndependenceWeight())
                        )),
                        "related_topic_paths", StateValue.arr(binding.relatedTopicPaths().stream()
                                .map(StateValue::str)
                                .toList())
                )))
                .toList();
    }

    private void decodeTopicBindings(TopicMemoryIndex topicIndex, String topicPath, JSONArray bindingsArray) {
        if (bindingsArray == null) {
            return;
        }
        for (int i = 0; i < bindingsArray.size(); i++) {
            JSONObject bindingObject = bindingsArray.getJSONObject(i);
            if (bindingObject == null) {
                continue;
            }
            String unitId = bindingObject.getString("unit_id");
            String sliceId = bindingObject.getString("slice_id");
            if (unitId == null || sliceId == null) {
                continue;
            }
            Long timestamp = bindingObject.getLong("timestamp");
            if (timestamp == null) {
                log.warn("skip topic binding without timestamp: {}:{}", unitId, sliceId);
                continue;
            }
            List<String> relatedTopicPaths = topicIndex.normalizeTopicPaths(
                    bindingObject.getList("related_topic_paths", String.class)
            );
            topicIndex.recordBinding(
                    topicPath,
                    new SliceRef(unitId, sliceId),
                    timestamp,
                    relatedTopicPaths,
                    decodeActivationProfile(bindingObject.getJSONObject("activation_profile"))
            );
            topicIndex.ensureTopicPaths(relatedTopicPaths);
        }
    }

    private ActivationProfile decodeActivationProfile(JSONObject profileObject) {
        if (profileObject == null) {
            return null;
        }
        return new ActivationProfile(
                profileObject.getFloat("activation_weight"),
                profileObject.getFloat("diffusion_weight"),
                profileObject.getFloat("context_independence_weight")
        );
    }

    private List<StateValue> encodeSliceRefs(List<SliceRef> refs) {
        return refs.stream()
                .map(ref -> (StateValue) StateValue.obj(Map.of(
                        "unit_id", StateValue.str(ref.getUnitId()),
                        "slice_id", StateValue.str(ref.getSliceId())
                )))
                .toList();
    }

    private CopyOnWriteArrayList<SliceRef> decodeSliceRefs(JSONArray refsArray) {
        CopyOnWriteArrayList<SliceRef> refs = new CopyOnWriteArrayList<>();
        if (refsArray == null) {
            return refs;
        }
        for (int i = 0; i < refsArray.size(); i++) {
            JSONObject refObject = refsArray.getJSONObject(i);
            if (refObject == null) {
                continue;
            }
            String unitId = refObject.getString("unit_id");
            String sliceId = refObject.getString("slice_id");
            if (unitId == null || sliceId == null) {
                continue;
            }
            refs.addIfAbsent(new SliceRef(unitId, sliceId));
        }
        return refs;
    }
}
