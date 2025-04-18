package work.slhaf.agent.modules.memory;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;

import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class SliceEvaluator extends Model {
    public static final String MODEL_KEY = "slice_evaluator";

    private static SliceEvaluator sliceEvaluator;

    private SliceEvaluator(){}

    public static SliceEvaluator getInstance() throws IOException, ClassNotFoundException {
        if (sliceEvaluator == null) {
            Config config = Config.getConfig();
            sliceEvaluator = new SliceEvaluator();
            setModel(config,sliceEvaluator, MODEL_KEY, ModelConstant.SLICE_EVALUATOR_PROMPT);
            log.info("SliceEvaluator注册完毕...");
        }

        return sliceEvaluator;
    }


}
