package work.slhaf.partner.api.chat.pojo

import work.slhaf.partner.api.common.entity.PersistableObject
import java.io.Serial

data class Message(
    val role: String,
    val content: String
) : PersistableObject() {
    companion object {
        @Serial
        private const val serialVersionUID = 1L
    }
}
