package work.slhaf.partner.module.impression

/**
 * A conservative, auditable plan produced after message rolling.
 *
 * The updater should treat this model as intent only: validation decides whether
 * a step is safe to execute, and the applier performs mutations through
 * CognitionCapability / ImpressionCore so indexes stay consistent.
 */
data class ImpressionUpdatePlan @JvmOverloads constructor(
    val steps: List<ImpressionUpdateStep>,
    val status: PlanStatus = PlanStatus.PREPARED,
    val reason: String? = null,
)

enum class PlanStatus {
    PREPARED,
    CONFIRMED,
    REJECTED,
}

sealed class ImpressionUpdateStep

data class UpdateExistingStep(
    val entityUuid: String,
    val updatePatch: UpdatePatch,
) : ImpressionUpdateStep()


data class CreateEntityStep(
    val subject: String,
    val impressions: List<ImpressionPatch> = emptyList(),
    val features: List<FeaturePatch> = emptyList(),
    val aliases: List<AliasPatch> = emptyList(),
    val relations: List<RelationPatch> = emptyList(),
) : ImpressionUpdateStep()

sealed class UpdatePatch

data class ImpressionPatch @JvmOverloads constructor(
    val impression: String,
    val newImpression: String? = null,
    val confidence: Double = 1.0,
) : UpdatePatch()

data class FeaturePatch @JvmOverloads constructor(
    val feature: String,
    val newFeature: String? = null,
    val confidence: Double = 1.0,
) : UpdatePatch()

data class AliasPatch @JvmOverloads constructor(
    val alias: String,
    val deprecated: Boolean = false,
) : UpdatePatch()

data class SubjectPatch @JvmOverloads constructor(
    val subject: String,
    val keepOldSubjectAsAlias: Boolean = true,
) : UpdatePatch()

data class RelationPatch @JvmOverloads constructor(
    val target: String,
    val relation: String,
    val strength: Double = 1.0,
) : UpdatePatch()
