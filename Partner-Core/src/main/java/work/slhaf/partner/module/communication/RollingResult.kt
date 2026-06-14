package work.slhaf.partner.module.communication

import work.slhaf.partner.core.memory.pojo.MemorySliceSnapshot
import work.slhaf.partner.core.memory.pojo.MemoryUnitSnapshot
import work.slhaf.partner.framework.agent.model.pojo.Message

data class RollingResult(
    val memoryUnit: MemoryUnitSnapshot,
    val memorySlice: MemorySliceSnapshot,
    val rollingSize: Int,
    val retainDivisor: Int,
) {
    val summary: String
        get() = memorySlice.summary ?: ""

    fun incrementMessages(): List<Message> = memoryUnit.messagesOf(memorySlice)
}
