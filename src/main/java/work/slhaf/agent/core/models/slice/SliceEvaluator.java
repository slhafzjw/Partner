package work.slhaf.agent.core.models.slice;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.core.config.Config;
import work.slhaf.agent.core.models.common.Model;
import work.slhaf.agent.core.models.common.ModelConstant;

@EqualsAndHashCode(callSuper = true)
@Data
public class SliceEvaluator extends Model {
    public static final String MODEL_KEY = "slice_evaluator";

    private static SliceEvaluator sliceEvaluator;

    public static SliceEvaluator initialize(Config config) {

        if (sliceEvaluator == null) {
            sliceEvaluator = new SliceEvaluator();
            sliceEvaluator.setPrompt(ModelConstant.SLICE_EVALUATOR_PROMPT);
            setModel(config,sliceEvaluator, MODEL_KEY, sliceEvaluator.getPrompt());
        }

        return sliceEvaluator;
    }


}
