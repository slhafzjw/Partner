package work.slhaf.partner.ctl.commands

import picocli.CommandLine

@CommandLine.Command(
    name = "module",
    resourceBundle = "i18n.messages",
    description = [$$"${bundle:cli.module.description}"],
)
class ModuleCommand : Runnable{
    override fun run() {
        TODO("Not yet implemented")
    }
}