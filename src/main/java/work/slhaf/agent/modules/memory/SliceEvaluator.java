package work.slhaf.agent.modules.memory;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class SliceEvaluator extends Model {
    public static final String MODEL_KEY = "slice_evaluator";

    private static SliceEvaluator sliceEvaluator;

    public static SliceEvaluator initialize(Config config) {

        if (sliceEvaluator == null) {
            sliceEvaluator = new SliceEvaluator();
            sliceEvaluator.setPrompt(ModelConstant.SLICE_EVALUATOR_PROMPT);
            setModel(config,sliceEvaluator, MODEL_KEY, sliceEvaluator.getPrompt());
            log.info("SliceEvaluator注册完毕...");
        }

        return sliceEvaluator;
    }


}
