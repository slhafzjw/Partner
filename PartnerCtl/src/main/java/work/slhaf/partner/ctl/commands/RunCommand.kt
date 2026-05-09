package work.slhaf.partner.ctl.commands

import picocli.CommandLine
import work.slhaf.partner.ctl.commands.control.*
import work.slhaf.partner.ctl.i18n.I18n.text
import work.slhaf.partner.ctl.support.CommandInterrupted
import java.nio.file.Files

@CommandLine.Command(
    name = "run",
    resourceBundle = "i18n.messages",
    description = [$$"${bundle:cli.run.description}"],
)
class RunCommand : Runnable {

    @CommandLine.Mixin
    lateinit var helpOptions: HelpOptions

    @CommandLine.Option(
        names = ["-d", "--background"],
        descriptionKey = "cli.run.option.background.description",
    )
    var background: Boolean = false

    @CommandLine.Option(
        names = ["-l", "--log-level"],
        descriptionKey = "cli.log.option.level.description",
        defaultValue = "INFO",
        converter = [LogLevelConverter::class],
    )
    lateinit var logLevel: LogLevel

    override fun run() {
        val home = resolvePartnerHome()
        val partnerJar = resolvePartnerJar(home)

        if (!Files.isRegularFile(partnerJar)) {
            throw CommandInterrupted(text("control.run.error.jarNotFound", partnerJar))
        }

        if (background) {
            runInBackground(home, partnerJar, logLevel)
        } else {
            runInForeground(home, partnerJar, logLevel)
        }
    }
}

