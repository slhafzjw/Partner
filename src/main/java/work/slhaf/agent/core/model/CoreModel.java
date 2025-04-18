package work.slhaf.agent.core.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;

import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class CoreModel extends Model implements InteractionModule {

    public static final String MODEL_KEY = "core_model";
    private static CoreModel coreModel;

    private CoreModel() {
    }

    public static CoreModel getInstance() throws IOException, ClassNotFoundException {
        if (coreModel == null) {
            Config config = Config.getConfig();
            coreModel = new CoreModel();
            setModel(config, coreModel, MODEL_KEY, ModelConstant.CORE_MODEL_PROMPT);
            log.info("CoreModel注册完毕...");
        }
        return coreModel;
    }

    @Override
    public void execute(InteractionContext interactionContext) {
        //TODO 需要拼接上下文之后再发送给主模型

        ChatResponse res = runChat(interactionContext.getInput());
        interactionContext.setCoreResponse(res);
    }
}
