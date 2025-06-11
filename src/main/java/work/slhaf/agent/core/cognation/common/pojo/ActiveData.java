package work.slhaf.agent.core.cognation.common.pojo;

import lombok.Data;
import work.slhaf.agent.shared.memory.EvaluatedSlice;

import java.util.HashMap;
import java.util.List;

@Data
public class ActiveData {
    private HashMap<String, List<EvaluatedSlice>> activatedSlices;

    public void updateActivatedSlices(String userId, List<EvaluatedSlice> memorySlices) {
        activatedSlices.put(userId, memorySlices);
    }

    public String getActivatedSlicesStr(String userId) {
        if (activatedSlices.containsKey(userId)) {
            StringBuilder str = new StringBuilder();
            activatedSlices.get(userId).forEach(slice -> str.append("\n\n").append("[").append(slice.getDate()).append("]\n")
                    .append(slice.getSummary()));
            return str.toString();
        } else {
            return null;
        }
    }

    public void clearActivatedSlices(String userId) {
        activatedSlices.remove(userId);
    }

    public boolean hasActivatedSlices(String userId) {
        if (!activatedSlices.containsKey(userId)){
            return false;
        }
        return !activatedSlices.get(userId).isEmpty();
    }
}
