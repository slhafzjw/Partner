package work.slhaf.partner.core.action.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionCacheData {
    private float[] inputVector;
    private float[] tendencyVector;
    private String tendency;
    private int inputMatchCount;
    private boolean activated;
    private List<float[]> validSamples = new ArrayList<>();
    private double threshold;
}
