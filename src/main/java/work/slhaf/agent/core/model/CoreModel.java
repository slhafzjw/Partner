package work.slhaf.agent.core.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class CoreModel extends Model {

    public static final String MODEL_KEY = "core_model";
    private static CoreModel coreModel;

    public static CoreModel initialize(Config config) {
        if (coreModel == null) {
            coreModel = new CoreModel();
            coreModel.setPrompt(ModelConstant.CORE_MODEL_PROMPT);
            setModel(config, coreModel, MODEL_KEY, coreModel.getPrompt());
            log.info("CoreModel注册完毕...");
        }
        return coreModel;
    }

}
