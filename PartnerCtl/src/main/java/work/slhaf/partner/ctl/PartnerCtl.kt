package work.slhaf.partner.ctl

import picocli.AutoComplete
import picocli.CommandLine
import work.slhaf.partner.ctl.commands.*
import kotlin.system.exitProcess

@CommandLine.Command(
    name = "partnerctl",
    mixinStandardHelpOptions = true,
    version = ["partnerctl 0.1.0"],
    subcommands = [
        InitCommand::class,
        RunCommand::class,
        ChatCommand::class,
        ConfigCommand::class,
        ModuleCommand::class,
        AutoComplete.GenerateCompletion::class
    ],
    description = ["Partner command line tool."]
)
class PartnerCtl : Runnable {

    override fun run() {
        CommandLine.usage(this, System.out)
    }

}

fun main(args: Array<String>) {
    val exitCode = CommandLine(PartnerCtl()).execute(*args)
    exitProcess(exitCode)
}