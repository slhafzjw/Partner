package work.slhaf.partner.core.action.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionCacheData {
    private boolean activated;
    private int inputMatchCount;

    private float[] inputVector;
    private float[] tendencyVector;
    private String tendency;
    private double threshold;

    private List<String> validSamples = new ArrayList<>();
    private int failedCount;
    private Type type;

    enum Type {
        PRIMARY, REBUILD_V1, REBUILD_V2, REBUILD_V3
    }
}
