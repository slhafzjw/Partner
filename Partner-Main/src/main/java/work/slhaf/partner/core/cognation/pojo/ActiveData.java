package work.slhaf.partner.core.cognation.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.common.entity.PersistableObject;
import work.slhaf.partner.core.submodule.memory.pojo.EvaluatedSlice;

import java.io.Serial;
import java.util.HashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ActiveData extends PersistableObject {
    private HashMap<String, List<EvaluatedSlice>> activatedSlices = new HashMap<>();

    @Serial
    private static final long serialVersionUID = 1L;

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
