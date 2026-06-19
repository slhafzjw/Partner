package work.slhaf.partner.module.impression;

import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.support.Result;

import java.util.ArrayList;
import java.util.List;

public class ImpressionUpdatePlanApplier extends AbstractAgentModule.Sub<ImpressionUpdatePlan, Result<ImpressionUpdateApplyResult>> {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    protected Result<ImpressionUpdateApplyResult> doExecute(ImpressionUpdatePlan plan) {
        return apply(plan);
    }

    public Result<ImpressionUpdateApplyResult> apply(ImpressionUpdatePlan plan) {
        return Result.runCatching(() -> {
            if (plan == null || plan.getStatus() != PlanStatus.CONFIRMED) {
                throw new IllegalArgumentException("only confirmed impression update plans can be applied");
            }
            List<String> createdEntityUuids = new ArrayList<>();
            for (ImpressionUpdateStep step : plan.getSteps()) {
                String createdEntityUuid = applyStep(step);
                if (createdEntityUuid != null && !createdEntityUuid.isBlank()) {
                    createdEntityUuids.add(createdEntityUuid);
                }
            }
            return new ImpressionUpdateApplyResult(createdEntityUuids);
        });
    }

    private String applyStep(ImpressionUpdateStep step) {
        if (step instanceof UpdateExistingStep updateStep) {
            applyPatch(updateStep.getEntityUuid(), updateStep.getUpdatePatch());
            return null;
        }
        if (step instanceof CreateEntityStep createStep) {
            String entityUuid = cognitionCapability.createEntity(createStep.getSubject());
            if (entityUuid == null || entityUuid.isBlank()) {
                throw new IllegalStateException("created entity uuid is blank");
            }
            applyPatches(entityUuid, createStep.getImpressions());
            applyPatches(entityUuid, createStep.getFeatures());
            applyPatches(entityUuid, createStep.getAliases());
            applyPatches(entityUuid, createStep.getRelations());
            return entityUuid;
        }
        throw new IllegalArgumentException("unsupported impression update step: " + step);
    }

    private void applyPatches(String entityUuid, List<? extends UpdatePatch> patches) {
        for (UpdatePatch patch : patches) {
            applyPatch(entityUuid, patch);
        }
    }

    private void applyPatch(String entityUuid, UpdatePatch patch) {
        boolean applied = switch (patch) {
            case SubjectPatch subjectPatch -> cognitionCapability.renameEntitySubject(
                    entityUuid,
                    subjectPatch.getSubject(),
                    subjectPatch.getKeepOldSubjectAsAlias()
            );
            case AliasPatch aliasPatch -> cognitionCapability.addEntityAlias(entityUuid, aliasPatch.getAlias(), aliasPatch.getDeprecated());
            case ImpressionPatch impressionPatch -> cognitionCapability.updateEntityImpression(
                    entityUuid,
                    impressionPatch.getImpression(),
                    impressionPatch.getNewImpression(),
                    impressionPatch.getConfidence()
            );
            case FeaturePatch featurePatch -> cognitionCapability.updateEntityFeature(
                    entityUuid,
                    featurePatch.getFeature(),
                    featurePatch.getNewFeature(),
                    featurePatch.getConfidence()
            );
            case RelationPatch relationPatch -> cognitionCapability.updateEntityRelation(
                    entityUuid,
                    relationPatch.getTarget(),
                    relationPatch.getRelation(),
                    relationPatch.getStrength()
            );
            case null, default -> throw new IllegalArgumentException("unsupported impression update patch: " + patch);
        };

        if (!applied) {
            throw new IllegalStateException("failed to apply impression update patch: " + patch);
        }
    }

}
