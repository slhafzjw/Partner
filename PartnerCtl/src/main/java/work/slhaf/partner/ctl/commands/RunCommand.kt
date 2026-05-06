package work.slhaf.partner.ctl.commands

import picocli.CommandLine
import work.slhaf.partner.ctl.commands.control.*
import work.slhaf.partner.ctl.i18n.I18n.text
import work.slhaf.partner.ctl.support.CommandInterrupted
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime

@CommandLine.Command(name = "run", description = ["Start Partner agent."])
class RunCommand : Runnable {

    @CommandLine.Option(names = ["-d", "--background"], description = ["Run Partner in background."])
    var background: Boolean = false

    override fun run() {
        val home = resolvePartnerHome()
        val partnerJar = resolvePartnerJar(home)

        if (!Files.isRegularFile(partnerJar)) {
            throw CommandInterrupted(text("control.run.error.jarNotFound", partnerJar))
        }

        if (background) {
            runInBackground(home, partnerJar)
        } else {
            runInForeground(home, partnerJar)
        }
    }
}

private fun runInForeground(home: Path, partnerJar: Path) {
    val logFile = resolveLogFile(home)
    Files.createDirectories(logFile.parent)

    val process = createPartnerProcessBuilder(home, partnerJar)
        .inheritIO()
        .start()

    appendControlLog(logFile, text("control.run.log.foregroundStarting", process.pid(), partnerJar))

    val shutdownHook = Thread {
        if (process.isAlive) {
            process.destroy()
        }
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    val exitCode = process.waitFor()
    runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    appendControlLog(logFile, text("control.run.log.exited", exitCode))

    if (exitCode != 0) {
        throw CommandInterrupted(text("control.run.error.exited", exitCode), exitCode)
    }
}

private fun runInBackground(home: Path, partnerJar: Path) {
    val pidFile = resolvePidFile(home)
    val logFile = resolveLogFile(home)
    val existingProcess = findPartnerProcesses(home, partnerJar).firstOrNull()

    if (existingProcess != null) {
        throw CommandInterrupted(text("control.run.error.alreadyRunning", existingProcess.pid()))
    }

    Files.createDirectories(pidFile.parent)
    Files.createDirectories(logFile.parent)

    val process = createPartnerProcessBuilder(home, partnerJar)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()

    appendControlLog(logFile, text("control.run.log.backgroundStarting", process.pid(), partnerJar))

    Thread.sleep(BACKGROUND_START_CHECK_MILLIS)
    if (!process.isAlive) {
        val exitCode = process.exitValue()
        appendControlLog(logFile, text("control.run.log.exited", exitCode))
        Files.deleteIfExists(pidFile)
        throw CommandInterrupted(text("control.run.error.exited", exitCode), exitCode)
    }

    Files.writeString(
        pidFile,
        process.pid().toString(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )

    println(text("control.run.success.backgroundStarted", process.pid()))
    println(text("control.run.info.logFile", logFile))
}

private fun createPartnerProcessBuilder(home: Path, partnerJar: Path): ProcessBuilder {
    return ProcessBuilder("java", "-DPARTNER_HOME=$home", "-jar", partnerJar.toString())
        .apply {
            environment()["PARTNER_HOME"] = home.toString()
        }
}

private fun appendControlLog(logFile: Path, message: String) {
    Files.createDirectories(logFile.parent)
    Files.newOutputStream(
        logFile,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
    ).use { output ->
        writeControlLog(output, message)
    }
}

private fun writeControlLog(output: OutputStream, message: String) {
    output.write("[partnerctl ${LocalDateTime.now()}] $message\n".toByteArray())
    output.flush()
}

private const val BACKGROUND_START_CHECK_MILLIS = 500L
