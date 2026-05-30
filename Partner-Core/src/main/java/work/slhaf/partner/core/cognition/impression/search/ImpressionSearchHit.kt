package work.slhaf.partner.core.cognition.impression.search

data class ImpressionSearchHit(
    val document: ImpressionSearchDocument,
    val score: Double,
    val matchedTerms: Set<String> = emptySet(),
)
