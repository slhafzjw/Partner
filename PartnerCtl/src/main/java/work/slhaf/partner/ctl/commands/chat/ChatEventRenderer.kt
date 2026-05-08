package work.slhaf.partner.ctl.commands.chat

import work.slhaf.partner.api.InteractionEvent
import work.slhaf.partner.api.ModuleEvent
import work.slhaf.partner.api.ReplyEvent
import work.slhaf.partner.api.SystemEvent
import work.slhaf.partner.ctl.support.PromptPart
import work.slhaf.partner.ctl.support.PromptStyle
import work.slhaf.partner.ctl.support.TerminalText

internal class ChatEventRenderer {
    fun renderCommittedUserInput(content: String): String {
        return TerminalText.render(
            listOf(
                PromptPart("you", PromptStyle.CYAN),
                PromptPart(": "),
                PromptPart(content),
            )
        )
    }

    fun renderActiveReply(content: String): String {
        return if (content.isBlank()) {
            TerminalText.render(
                listOf(
                    PromptPart("assistant", PromptStyle.GREEN),
                    PromptPart(":"),
                )
            )
        } else {
            TerminalText.render(
                listOf(
                    PromptPart("assistant", PromptStyle.GREEN),
                    PromptPart(": "),
                    PromptPart(content),
                )
            )
        }
    }

    fun renderEventMessage(event: InteractionEvent): String? {
        return when (event) {
            is ReplyEvent -> null
            is SystemEvent -> TerminalText.render(
                listOf(
                    PromptPart("system", PromptStyle.YELLOW),
                    PromptPart(": "),
                    PromptPart(event.title),
                    PromptPart(": "),
                    PromptPart(event.content),
                )
            )

            is ModuleEvent -> TerminalText.render(
                listOf(
                    PromptPart("module", PromptStyle.BLUE),
                    PromptPart(":"),
                    PromptPart(event.data.module),
                    PromptPart(": "),
                    PromptPart(event.data.content),
                )
            )
        }
    }

    fun renderSendFailure(message: String): String {
        return TerminalText.render(
            listOf(
                PromptPart("send failed", PromptStyle.RED),
                PromptPart(": "),
                PromptPart(message),
            )
        )
    }
}
