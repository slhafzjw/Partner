package work.slhaf.partner.framework.agent.interaction.data

open class InputData(
    val source: String,
    val content: String
) {
    private val _meta = mutableMapOf<String, String>()
    val meta: Map<String, String>
        get() = _meta

    fun addMeta(key: String, value: String) {
        _meta[key] = value
    }
}