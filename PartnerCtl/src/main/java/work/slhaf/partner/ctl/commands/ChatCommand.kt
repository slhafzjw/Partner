package work.slhaf.partner.ctl.commands

import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import picocli.CommandLine

@CommandLine.Command(
    name = "chat",
    resourceBundle = "i18n.messages",
    description = [$$"${bundle:cli.chat.description}"],
)
class ChatCommand : Runnable {

    override fun run() {
        val terminal = createTerminal()
        val reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .build()

        terminal.writer().println("Partner chat demo. Type /exit to quit.")
        terminal.writer().flush()

        while (true) {
            val line = try {
                reader.readLine("partner> ")
            } catch (_: UserInterruptException) {
                terminal.writer().println()
                terminal.writer().flush()
                continue
            } catch (_: EndOfFileException) {
                terminal.writer().println()
                terminal.writer().flush()
                break
            }

            when {
                line == "/exit" -> break
                line.isBlank() -> continue
                else -> {
                    terminal.writer().println("echo: $line")
                    terminal.writer().flush()
                }
            }
        }
    }

    private fun createTerminal(): Terminal {
        return TerminalBuilder.builder()
            .system(true)
            .dumb(true)
            .build()
    }
}