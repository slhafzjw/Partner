package work.slhaf.agent.core.models.core;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.core.config.Config;
import work.slhaf.agent.core.models.common.Model;
import work.slhaf.agent.core.models.common.ModelConstant;

@EqualsAndHashCode(callSuper = true)
@Data
public class CoreModel extends Model {

    public static final String MODEL_KEY = "core_model";
    private static CoreModel coreModel;

    public static CoreModel initialize(Config config) {
        if (coreModel == null) {
            coreModel = new CoreModel();
            coreModel.setPrompt(ModelConstant.CORE_MODEL_PROMPT);
            setModel(config, coreModel, MODEL_KEY, coreModel.getPrompt());
        }
        return coreModel;
    }

}
