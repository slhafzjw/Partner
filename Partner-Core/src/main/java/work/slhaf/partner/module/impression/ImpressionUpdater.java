package work.slhaf.partner.module.impression;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.context.BlockContent;
import work.slhaf.partner.core.cognition.context.ContextBlock;
import work.slhaf.partner.core.cognition.impression.ActiveEntity;
import work.slhaf.partner.core.memory.pojo.MemorySliceSnapshot;
import work.slhaf.partner.core.memory.pojo.MemoryUnitSnapshot;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.communication.AfterRolling;
import work.slhaf.partner.module.communication.AfterRollingRegistry;
import work.slhaf.partner.module.communication.RollingResult;

import java.util.List;
import java.util.Set;

public class ImpressionUpdater extends AbstractAgentModule.Standalone implements AfterRolling {

    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectModule
    private AfterRollingRegistry afterRollingRegistry;
    @InjectModule
    private ImpressionUpdatePlanner planner;
    @InjectModule
    private ImpressionUpdatePlanValidator validator;
    @InjectModule
    private ImpressionUpdatePlanApplier applier;

    @Init
    public void init() {
        afterRollingRegistry.register(this);
    }

    @Override
    public void consume(RollingResult result) {
        ImpressionUpdateContext context = buildContext(result);
        Result<ImpressionUpdatePlan> planResult = planner.execute(context);
        ImpressionUpdatePlan plan = planResult.getOrDefault(null);
        if (!validator.execute(plan)) {
            return;
        }
        ImpressionUpdatePlan confirmedPlan = new ImpressionUpdatePlan(
                plan.getSteps(),
                PlanStatus.CONFIRMED,
                plan.getReason()
        );
        Result<ImpressionUpdateApplyResult> applyResult = applier.execute(confirmedPlan);
        applyResult.onFailure(exp -> applierFailure(context, exp.getMessage()))
                .onSuccess(applySummary -> applySummary.createdEntityUuids().forEach(entityUuid -> {
                    ActiveEntity activeEntity = cognitionCapability.activateKnownEntity(entityUuid);
                    if (activeEntity != null) {
                        registerActiveEntity(activeEntity);
                    }
                }));
    }

    private ImpressionUpdateContext buildContext(RollingResult result) {
        MemoryUnitSnapshot unit = result.getMemoryUnit();
        MemorySliceSnapshot slice = result.getMemorySlice();
        return new ImpressionUpdateContext(
                unit.getId(),
                slice.getId(),
                result.getSummary(),
                result.getRollingSize(),
                result.getRetainDivisor(),
                slice.getStartIndex(),
                slice.getEndIndex(),
                slice.getTimestamp(),
                unit.getTimestamp(),
                List.copyOf(result.incrementMessages())
        );
    }

    private void applierFailure(ImpressionUpdateContext context, String message) {
        cognitionCapability.contextWorkspace().register(new ContextBlock(
                new BlockContent("impression_update_apply_failure", "impression_updater", BlockContent.Urgency.LOW) {
                    @Override
                    protected void fillXml(@NotNull Document document, @NotNull Element root) {
                        appendTextElement(document, root, "memory_unit_id", context.memoryUnitId());
                        appendTextElement(document, root, "memory_slice_id", context.memorySliceId());
                        appendTextElement(document, root, "message", message == null ? "" : message);
                    }
                },
                Set.of(ContextBlock.FocusedDomain.COGNITION),
                20,
                20,
                0
        ));
    }

    private void registerActiveEntity(ActiveEntity activeEntity) {
        cognitionCapability.contextWorkspace().register(new ContextBlock(
                activeEntity,
                activeEntity,
                activeEntity,
                Set.of(ContextBlock.FocusedDomain.COGNITION, ContextBlock.FocusedDomain.MEMORY),
                100,
                0.5,
                20
        ));
    }

}
