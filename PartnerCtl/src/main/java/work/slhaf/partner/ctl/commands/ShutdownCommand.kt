package work.slhaf.partner.ctl.commands

import picocli.CommandLine
import work.slhaf.partner.ctl.commands.control.*
import work.slhaf.partner.ctl.i18n.I18n
import work.slhaf.partner.ctl.support.CommandInterrupted

@CommandLine.Command(
    name = "shutdown",
    resourceBundle = "i18n.messages",
    description = [$$"${bundle:cli.shutdown.description}"],
)
class ShutdownCommand : Runnable {

    @CommandLine.Option(
        names = ["--timeout"],
        descriptionKey = "cli.shutdown.option.timeout.description"
    )
    var timeoutSeconds: Long = 10

    @CommandLine.Option(
        names = ["-f", "--force"],
        descriptionKey = "cli.shutdown.option.force.description"
    )
    var force: Boolean = false

    override fun run() {
        val home = resolvePartnerHome()
        val partnerJar = resolvePartnerJar(home)
        val pidFile = resolvePidFile(home)
        val processes = findPartnerProcesses(home, partnerJar)

        if (processes.isEmpty()) {
            cleanupStalePidFile(pidFile)
            println(I18n.text("control.shutdown.info.notRunning", partnerJar))
            return
        }

        var failed = false
        processes.forEach { process ->
            val pid = process.pid()
            println(I18n.text("control.shutdown.info.stopping", pid))
            process.destroy()

            val stopped = waitForExit(process, timeoutSeconds)
            if (stopped) {
                println(I18n.text("control.shutdown.success.stopped", pid))
                deletePidFileIfMatches(pidFile, pid)
                return@forEach
            }

            if (force) {
                println(I18n.text("control.shutdown.warn.force", pid))
                process.destroyForcibly()
                if (waitForExit(process, timeoutSeconds)) {
                    println(I18n.text("control.shutdown.success.stopped", pid))
                    deletePidFileIfMatches(pidFile, pid)
                } else {
                    failed = true
                    println(I18n.text("control.shutdown.error.notStopped", pid))
                }
            } else {
                failed = true
                println(I18n.text("control.shutdown.error.notStoppedUseForce", pid))
            }
        }

        if (failed) {
            throw CommandInterrupted(I18n.text("control.shutdown.error.failed"))
        }
    }
}