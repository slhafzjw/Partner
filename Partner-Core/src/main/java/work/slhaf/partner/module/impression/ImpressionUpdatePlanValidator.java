package work.slhaf.partner.module.impression;

import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;

import java.util.List;

public class ImpressionUpdatePlanValidator extends AbstractAgentModule.Sub<ImpressionUpdatePlan, Boolean> {

    @Override
    protected @NotNull Boolean doExecute(ImpressionUpdatePlan plan) {
        return isExecutable(plan);
    }

    public boolean isExecutable(ImpressionUpdatePlan plan) {
        if (plan == null || plan.getStatus() != PlanStatus.PREPARED) {
            return false;
        }
        List<ImpressionUpdateStep> steps = plan.getSteps();
        if (steps.isEmpty()) {
            return false;
        }
        for (ImpressionUpdateStep step : steps) {
            if (!isValidStep(step)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidStep(ImpressionUpdateStep step) {
        if (step instanceof UpdateExistingStep updateStep) {
            return hasText(updateStep.getEntityUuid()) && isValidPatch(updateStep.getUpdatePatch());
        }
        if (step instanceof CreateEntityStep createStep) {
            return hasText(createStep.getSubject())
                    && (!createStep.getImpressions().isEmpty()
                    || !createStep.getFeatures().isEmpty()
                    || !createStep.getAliases().isEmpty()
                    || !createStep.getRelations().isEmpty())
                    && createStep.getImpressions().stream().allMatch(this::isValidPatch)
                    && createStep.getFeatures().stream().allMatch(this::isValidPatch)
                    && createStep.getAliases().stream().allMatch(this::isValidPatch)
                    && createStep.getRelations().stream().allMatch(this::isValidPatch);
        }
        return false;
    }

    private boolean isValidPatch(UpdatePatch patch) {
        if (patch instanceof ImpressionPatch impressionPatch) {
            return hasText(impressionPatch.getImpression());
        }
        if (patch instanceof FeaturePatch featurePatch) {
            return hasText(featurePatch.getFeature());
        }
        if (patch instanceof AliasPatch aliasPatch) {
            return hasText(aliasPatch.getAlias());
        }
        if (patch instanceof SubjectPatch subjectPatch) {
            return hasText(subjectPatch.getSubject());
        }
        if (patch instanceof RelationPatch relationPatch) {
            return hasText(relationPatch.getTarget()) && hasText(relationPatch.getRelation());
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
