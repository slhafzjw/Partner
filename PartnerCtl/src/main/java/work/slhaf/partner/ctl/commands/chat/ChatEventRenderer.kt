package work.slhaf.partner.ctl.commands.chat

import work.slhaf.partner.api.InteractionEvent
import work.slhaf.partner.api.ModuleEvent
import work.slhaf.partner.api.ReplyEvent
import work.slhaf.partner.api.SystemEvent

internal class ChatEventRenderer {
    fun renderCommittedUserInput(content: String): String = "you: $content"

    fun renderActiveReply(content: String): String {
        return if (content.isBlank()) {
            "assistant:"
        } else {
            "assistant: $content"
        }
    }

    fun renderEventMessage(event: InteractionEvent): String? {
        return when (event) {
            is ReplyEvent -> null
            is SystemEvent -> "system: ${event.title}: ${event.content}"
            is ModuleEvent -> "module:${event.data.module}: ${event.data.content}"
        }
    }

    fun renderSendFailure(message: String): String = "send failed: $message"
}
