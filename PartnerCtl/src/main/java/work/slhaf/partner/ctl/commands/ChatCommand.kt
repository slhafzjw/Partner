package work.slhaf.partner.ctl.commands

import picocli.CommandLine
import work.slhaf.partner.api.InputData
import work.slhaf.partner.ctl.commands.chat.ChatScreen
import work.slhaf.partner.ctl.commands.chat.WebSocketClient

@CommandLine.Command(
    name = "chat",
    resourceBundle = "i18n.messages",
    description = [$$"${bundle:cli.chat.description}"],
)
class ChatCommand : Runnable {

    @CommandLine.Mixin
    lateinit var helpOptions: HelpOptions

    @CommandLine.Option(
        names = ["--url"],
        description = ["WebSocket gateway URL."],
        defaultValue = DEFAULT_URL,
    )
    lateinit var url: String

    @CommandLine.Option(
        names = ["--source"],
        description = ["Input source identity used by Partner runtime."],
        required = true
    )
    lateinit var source: String

    override fun run() {
        val screen = ChatScreen(source)
        WebSocketClient(url) { event ->
            screen.postInteractionEvent(event)
        }.use { client ->
            screen.run { line ->
                client.send(InputData(source, line))
            }
        }
    }

    private companion object {
        private const val DEFAULT_URL = "ws://127.0.0.1:29600"
    }
}
