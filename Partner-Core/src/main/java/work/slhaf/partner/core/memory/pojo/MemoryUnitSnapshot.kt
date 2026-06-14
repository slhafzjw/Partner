package work.slhaf.partner.core.memory.pojo

import work.slhaf.partner.framework.agent.model.pojo.Message

data class MemoryUnitSnapshot(
    val id: String,
    val conversationMessages: List<Message>,
    val timestamp: Long,
    val slices: List<MemorySliceSnapshot>,
) {

    fun messagesOf(slice: MemorySliceSnapshot): List<Message> {
        if (conversationMessages.isEmpty()) {
            return emptyList()
        }
        val start = slice.startIndex.coerceIn(0, conversationMessages.size)
        val end = slice.endIndex.coerceIn(start, conversationMessages.size)
        if (start >= end) {
            return emptyList()
        }
        return conversationMessages.subList(start, end).toList()
    }
}
