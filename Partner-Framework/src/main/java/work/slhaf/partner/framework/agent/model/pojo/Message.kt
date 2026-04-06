package work.slhaf.partner.framework.agent.model.pojo

import com.alibaba.fastjson2.annotation.JSONCreator
import com.alibaba.fastjson2.annotation.JSONField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import work.slhaf.partner.framework.agent.common.entity.PersistableObject
import java.io.Serial

data class Message(
    val role: Character,
    val content: String
) : PersistableObject() {

    fun roleValue(): String = role.value

    enum class Character(
        @get:JsonValue
        @get:JSONField(value = true)
        val value: String
    ) {
        USER("user"),
        SYSTEM("system"),
        ASSISTANT("assistant");

        companion object {
            @JvmStatic
            @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
            @JSONCreator
            fun fromValue(value: String): Character {
                return entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("Unsupported message role: $value")
            }
        }
    }

    companion object {
        @Serial
        private const val serialVersionUID = 1L
    }
}
