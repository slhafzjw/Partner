package work.slhaf.partner.core.memory.pojo

data class MemorySliceSnapshot(
    val id: String,
    val startIndex: Int,
    val endIndex: Int,
    val summary: String?,
    val timestamp: Long,
)
