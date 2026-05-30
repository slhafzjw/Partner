package work.slhaf.partner.core.cognition.impression.search

data class ImpressionSearchDocument(
    val id: String,
    val target: ImpressionSearchTarget,
    val field: ImpressionSearchField,
    val text: String,
    val weight: Double = 1.0,
    val metadata: Map<String, String> = emptyMap(),
)
