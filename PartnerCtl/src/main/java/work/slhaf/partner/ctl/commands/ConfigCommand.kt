package work.slhaf.partner.ctl.commands

import picocli.CommandLine

@CommandLine.Command(
    name = "config",
    resourceBundle = "i18n.messages",
    description = [$$"${bundle:cli.config.description}"],
)
class ConfigCommand : Runnable{
    override fun run() {
        TODO("Not yet implemented")
    }
}