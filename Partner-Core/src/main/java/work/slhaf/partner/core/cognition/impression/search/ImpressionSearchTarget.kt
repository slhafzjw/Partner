package work.slhaf.partner.core.cognition.impression.search

data class ImpressionSearchTarget(
    val type: Type,
    val id: String,
) {
    enum class Type {
        ACTIVE_ENTITY,
        ENTITY
    }
}
