package work.slhaf.partner.module.impression;

import lombok.val;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.context.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class ImpressionRecaller extends AbstractAgentModule.Running<PartnerRunningFlowContext> {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    /**
     * 从交互中积累谈论的内容的特征（证据），基于证据创建 ActiveEntity，然后交给 CognitionCapability 进行投影并更新上下文
     */
    @Override
    protected void doExecute(@NotNull PartnerRunningFlowContext context) {
        val contextWorkspace = cognitionCapability.contextWorkspace();
        context.getInputs()
                .stream()
                .map(inputEntry -> {
                    val content = inputEntry.getContent();
                    return cognitionCapability.projectEntity(content);
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toSet())
                .forEach(activeEntity -> {
                    contextWorkspace.register(new ContextBlock(
                            activeEntity,
                            activeEntity,
                            activeEntity,
                            Set.of(
                                    ContextBlock.FocusedDomain.COGNITION,
                                    ContextBlock.FocusedDomain.MEMORY
                            ),
                            100,
                            0.5,
                            20
                    ));
                });
    }

    @Override
    public int order() {
        return 2;
    }
}
