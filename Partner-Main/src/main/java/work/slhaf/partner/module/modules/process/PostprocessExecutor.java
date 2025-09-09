package work.slhaf.partner.module.modules.process;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;
import work.slhaf.partner.module.common.module.PostRunningModule;

import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class PostprocessExecutor extends PostRunningModule {

    private static volatile PostprocessExecutor postprocessExecutor;
    private static final int POST_PROCESS_TRIGGER_ROLL_LIMIT = 36;

    @InjectCapability
    private CognationCapability cognationCapability;

    public static PostprocessExecutor getInstance() throws IOException, ClassNotFoundException {
        if (postprocessExecutor == null) {
            synchronized (PostprocessExecutor.class) {
                if (postprocessExecutor == null) {
                    postprocessExecutor = new PostprocessExecutor();
                }
            }
        }
        return postprocessExecutor;
    }

    @Override
    public void execute(PartnerRunningFlowContext context) throws IOException, ClassNotFoundException {
        boolean trigger = cognationCapability.getChatMessages().size() >= POST_PROCESS_TRIGGER_ROLL_LIMIT;
        context.getModuleContext().getExtraContext().put("post_process_trigger", trigger);
        log.debug("[PostprocessExecutor] 是否执行后处理: {}", trigger);
    }
}
