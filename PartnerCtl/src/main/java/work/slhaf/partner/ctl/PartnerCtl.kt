package work.slhaf.partner.ctl

import picocli.AutoComplete
import picocli.CommandLine
import work.slhaf.partner.ctl.commands.*
import work.slhaf.partner.ctl.support.CommandInterrupted
import work.slhaf.partner.ctl.ui.PromptCancelledException
import kotlin.system.exitProcess

@CommandLine.Command(
    name = "partnerctl",
    resourceBundle = "i18n.messages",
    description = [$$"${bundle:cli.partnerctl.description}"],
    mixinStandardHelpOptions = true,
    version = ["partnerctl 0.1.0"],
    subcommands = [
        InitCommand::class,
        RunCommand::class,
        ShutdownCommand::class,
        LogCommand::class,
        ChatCommand::class,
        ConfigCommand::class,
        ModuleCommand::class,
        AutoComplete.GenerateCompletion::class
    ]
)
class PartnerCtl : Runnable {

    override fun run() {
        CommandLine.usage(this, System.out)
    }

}

fun main(args: Array<String>) {
    val commandLine = CommandLine(PartnerCtl())
    commandLine.executionExceptionHandler = CommandLine.IExecutionExceptionHandler { ex, _, _ ->
        return@IExecutionExceptionHandler when (ex) {
            is PromptCancelledException -> {
                println()
                println("[warn] Cancelled")
                130
            }

            is CommandInterrupted -> {
                println()
                println("[error] ${ex.message}")
                ex.exitCode
            }

            else -> {
                System.err.println("[error] ${ex.message ?: "Unexpected error"}")
                if (System.getenv("PARTNERCTL_DEBUG") == "1") {
                    ex.printStackTrace(System.err)
                }
                1
            }
        }
    }

    exitProcess(commandLine.execute(*args))
}