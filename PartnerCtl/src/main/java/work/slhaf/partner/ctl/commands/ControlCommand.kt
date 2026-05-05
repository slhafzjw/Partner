package work.slhaf.partner.ctl.commands

import picocli.CommandLine
import work.slhaf.partner.ctl.i18n.I18n.text
import work.slhaf.partner.ctl.support.CommandInterrupted
import work.slhaf.partner.ctl.support.inheritCommand
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@CommandLine.Command(name = "run", description = ["Start Partner agent."])
class RunCommand : Runnable {

    override fun run() {
        val home = resolvePartnerHome()
        val partnerJar = resolvePartnerJar(home)

        if (!Files.isRegularFile(partnerJar)) {
            throw CommandInterrupted(text("control.run.error.jarNotFound", partnerJar))
        }

        val exitCode = inheritCommand(
            command = listOf("java", "-jar", partnerJar.toString()),
            environment = mapOf("PARTNER_HOME" to home.toString()),
        )

        if (exitCode != 0) {
            throw CommandInterrupted(text("control.run.error.exited", exitCode), exitCode)
        }
    }
}

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
        val processes = findPartnerProcesses(partnerJar)

        if (processes.isEmpty()) {
            println(text("control.shutdown.info.notRunning", partnerJar))
            return
        }

        var failed = false
        processes.forEach { process ->
            val pid = process.pid()
            println(text("control.shutdown.info.stopping", pid))
            process.destroy()

            val stopped = waitForExit(process, timeoutSeconds)
            if (stopped) {
                println(text("control.shutdown.success.stopped", pid))
                return@forEach
            }

            if (force) {
                println(text("control.shutdown.warn.force", pid))
                process.destroyForcibly()
                if (waitForExit(process, timeoutSeconds)) {
                    println(text("control.shutdown.success.stopped", pid))
                } else {
                    failed = true
                    println(text("control.shutdown.error.notStopped", pid))
                }
            } else {
                failed = true
                println(text("control.shutdown.error.notStoppedUseForce", pid))
            }
        }

        if (failed) {
            throw CommandInterrupted(text("control.shutdown.error.failed"))
        }
    }
}

private fun resolvePartnerHome(): Path {
    val home = System.getenv("PARTNER_HOME")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { Paths.get(it) }
        ?: Paths.get(System.getProperty("user.home"), ".partner")

    return home.toAbsolutePath().normalize()
}

private fun resolvePartnerJar(home: Path): Path {
    return home.resolve("resource").resolve("partner-core.jar").toAbsolutePath().normalize()
}

private fun findPartnerProcesses(partnerJar: Path): List<ProcessHandle> {
    val currentPid = ProcessHandle.current().pid()
    val jarPath = partnerJar.toString()

    return ProcessHandle.allProcesses()
        .filter { it.pid() != currentPid }
        .filter { it.isAlive }
        .filter { process ->
            val info = process.info()
            val arguments = info.arguments().orElse(emptyArray()) ?: emptyArray()
            val commandLine = info.commandLine().orElse("") ?: ""

            arguments.any { it == jarPath } || commandLine.contains(jarPath)
        }
        .toList()
}

private fun waitForExit(process: ProcessHandle, timeoutSeconds: Long): Boolean {
    return try {
        process.onExit().get(timeoutSeconds, TimeUnit.SECONDS)
        true
    } catch (_: TimeoutException) {
        false
    }
}