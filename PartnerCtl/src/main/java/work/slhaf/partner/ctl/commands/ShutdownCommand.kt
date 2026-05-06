package work.slhaf.partner.ctl.commands

import picocli.CommandLine
import work.slhaf.partner.ctl.commands.control.*
import work.slhaf.partner.ctl.i18n.I18n
import work.slhaf.partner.ctl.support.CommandInterrupted

@CommandLine.Command(name = "shutdown", description = ["Shutdown Partner agent."])
class ShutdownCommand : Runnable {

    @CommandLine.Option(
        names = ["--timeout"],
        description = ["Seconds to wait after graceful termination before failing or forcing shutdown."]
    )
    var timeoutSeconds: Long = 10

    @CommandLine.Option(
        names = ["-f", "--force"],
        description = ["Forcefully kill matching Partner process if it does not exit before timeout."]
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