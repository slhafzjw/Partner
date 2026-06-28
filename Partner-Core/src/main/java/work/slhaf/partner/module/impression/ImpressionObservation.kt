package work.slhaf.partner.module.impression

data class ImpressionEntityObservation @JvmOverloads constructor(
    val proposedSubject: String,
    val aliases: List<String> = emptyList(),
    val impressions: Map<String, Int> = emptyMap(),
    val features: Map<String, Int> = emptyMap(),
    val relations: Map<String, Map<String, Int>> = emptyMap(),
    val sourceActiveRuntimeIds: List<String> = emptyList(),
    val evidenceSnippets: List<String> = emptyList(),
    val reason: String? = null,
)

data class KnownEntityIdentity @JvmOverloads constructor(
    val entityUuid: String,
    val subject: String,
    val aliases: List<String> = emptyList(),
)
