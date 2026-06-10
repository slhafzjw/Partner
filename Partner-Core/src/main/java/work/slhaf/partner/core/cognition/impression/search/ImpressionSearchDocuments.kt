package work.slhaf.partner.core.cognition.impression.search

import work.slhaf.partner.core.cognition.impression.ActiveEntity
import work.slhaf.partner.core.cognition.impression.Entity

object ImpressionSearchDocuments {

    fun fromActiveEntity(activeEntity: ActiveEntity): List<ImpressionSearchDocument> {
        val target = ImpressionSearchTarget(
            ImpressionSearchTarget.Type.ACTIVE_ENTITY,
            activeEntity.runtimeId
        )
        val metadata = activeEntity.boundEntityUuid
            ?.let { mapOf("boundEntityUuid" to it) }
            .orEmpty()

        return buildList {
            add(
                ImpressionSearchDocument(
                    id = "active:${activeEntity.runtimeId}:subject",
                    target = target,
                    field = ImpressionSearchField.SUBJECT,
                    text = activeEntity.subject,
                    weight = SUBJECT_WEIGHT,
                    metadata = metadata,
                )
            )

            activeEntity.evidences.forEachIndexed { index, evidence ->
                add(
                    ImpressionSearchDocument(
                        id = "active:${activeEntity.runtimeId}:evidence:$index",
                        target = target,
                        field = ImpressionSearchField.EVIDENCE,
                        text = evidence.contentForContext(),
                        weight = EVIDENCE_WEIGHT * evidence.associationConfidence,
                        metadata = metadata,
                    )
                )
            }

            activeEntity.projectedFeatures.entries.forEachIndexed { index, entry ->
                add(
                    ImpressionSearchDocument(
                        id = "active:${activeEntity.runtimeId}:feature:$index",
                        target = target,
                        field = ImpressionSearchField.FEATURE,
                        text = entry.key,
                        weight = FEATURE_WEIGHT * entry.value,
                        metadata = metadata,
                    )
                )
            }

            activeEntity.projectedImpressions.entries.forEachIndexed { index, entry ->
                add(
                    ImpressionSearchDocument(
                        id = "active:${activeEntity.runtimeId}:impression:$index",
                        target = target,
                        field = ImpressionSearchField.IMPRESSION,
                        text = entry.key,
                        weight = IMPRESSION_WEIGHT * entry.value,
                        metadata = metadata,
                    )
                )
            }
        }
    }

    fun fromEntity(entity: Entity): List<ImpressionSearchDocument> {
        val target = ImpressionSearchTarget(
            ImpressionSearchTarget.Type.ENTITY,
            entity.uuid
        )

        return buildList {
            add(
                ImpressionSearchDocument(
                    id = "entity:${entity.uuid}:subject",
                    target = target,
                    field = ImpressionSearchField.SUBJECT,
                    text = entity.subject,
                    weight = SUBJECT_WEIGHT,
                )
            )

            entity.showAliases(includeDeprecated = true).forEachIndexed { index, alias ->
                add(
                    ImpressionSearchDocument(
                        id = "entity:${entity.uuid}:alias:$index",
                        target = target,
                        field = ImpressionSearchField.SUBJECT,
                        text = alias.alias,
                        weight = SUBJECT_WEIGHT * ALIAS_WEIGHT_FACTOR,
                    )
                )
            }

            entity.snapshotFeatures().keys.forEachIndexed { index, feature ->
                add(
                    ImpressionSearchDocument(
                        id = "entity:${entity.uuid}:feature:$index",
                        target = target,
                        field = ImpressionSearchField.FEATURE,
                        text = feature,
                        weight = FEATURE_WEIGHT,
                    )
                )
            }

            entity.snapshotImpressions().keys.forEachIndexed { index, impression ->
                add(
                    ImpressionSearchDocument(
                        id = "entity:${entity.uuid}:impression:$index",
                        target = target,
                        field = ImpressionSearchField.IMPRESSION,
                        text = impression,
                        weight = IMPRESSION_WEIGHT,
                    )
                )
            }

            entity.showRelations().forEachIndexed { index, relation ->
                val relationText = buildString {
                    append(relation.target)
                    relation.relations.keys.forEach { name ->
                        append(' ')
                        append(name)
                    }
                }

                add(
                    ImpressionSearchDocument(
                        id = "entity:${entity.uuid}:relation:$index",
                        target = target,
                        field = ImpressionSearchField.RELATION,
                        text = relationText,
                        weight = RELATION_WEIGHT,
                    )
                )
            }
        }
    }

    private const val SUBJECT_WEIGHT = 1.0
    private const val ALIAS_WEIGHT_FACTOR = 0.9
    private const val FEATURE_WEIGHT = 0.85
    private const val IMPRESSION_WEIGHT = 0.75
    private const val RELATION_WEIGHT = 0.65
    private const val EVIDENCE_WEIGHT = 0.8
}
