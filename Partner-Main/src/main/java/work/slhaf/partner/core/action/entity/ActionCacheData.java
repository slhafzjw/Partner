package work.slhaf.partner.core.action.entity;

import java.util.ArrayList;
import java.util.List;

import org.nd4j.linalg.api.ndarray.INDArray;

import lombok.Data;

@Data
public class ActionCacheData {
    private INDArray inputArray;
    private INDArray tendencyArray;
    private String tendency;
    private int count;
    private List<String> activateInputs = new ArrayList<>();
    private boolean activated;
}
