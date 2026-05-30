package work.slhaf.partner.core.cognition.impression.search

data class EntityAssociationMatch(
    val target: ImpressionSearchTarget,
    val score: Double,
    val hits: List<ImpressionSearchHit> = emptyList(),
)
