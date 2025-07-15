package work.slhaf.partner.module.modules.process;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.core.cognation.capability.ability.CognationCapability;
import work.slhaf.partner.core.cognation.CognationManager;
import work.slhaf.partner.core.interaction.data.context.InteractionContext;
import work.slhaf.partner.core.interaction.module.InteractionModule;

import java.io.IOException;

@Slf4j
@Data
public class PostprocessExecutor implements InteractionModule {

    private static volatile PostprocessExecutor postprocessExecutor;
    private static final int POST_PROCESS_TRIGGER_ROLL_LIMIT = 36;

    private CognationCapability cognationCapability;

    public static PostprocessExecutor getInstance() throws IOException, ClassNotFoundException {
        if (postprocessExecutor == null) {
            synchronized (PostprocessExecutor.class) {
                if (postprocessExecutor == null) {
                    postprocessExecutor = new PostprocessExecutor();
                    postprocessExecutor.setCognationCapability(CognationManager.getInstance());
                }
            }
        }
        return postprocessExecutor;
    }

    @Override
    public void execute(InteractionContext context) throws IOException, ClassNotFoundException {
        boolean trigger = cognationCapability.getChatMessages().size() >= POST_PROCESS_TRIGGER_ROLL_LIMIT;
        context.getModuleContext().getExtraContext().put("post_process_trigger", trigger);
        log.debug("[PostprocessExecutor] 是否执行后处理: {}", trigger);
    }
}
