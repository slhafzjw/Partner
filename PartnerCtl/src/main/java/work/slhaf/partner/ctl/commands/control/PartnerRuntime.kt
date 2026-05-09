package work.slhaf.partner.ctl.commands.control

import picocli.CommandLine
import work.slhaf.partner.ctl.i18n.I18n.text
import work.slhaf.partner.ctl.support.CommandInterrupted
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun resolvePartnerHome(): Path {
    val home = System.getenv("PARTNER_HOME")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { Paths.get(it) }
        ?: Paths.get(System.getProperty("user.home"), ".partner")

    return home.toAbsolutePath().normalize()
}

fun resolvePartnerJar(home: Path): Path {
    return home.resolve("resources").resolve("partner-core.jar").toAbsolutePath().normalize()
}

fun resolvePidFile(home: Path): Path {
    return home.resolve("state").resolve("run").resolve("partner.pid").toAbsolutePath().normalize()
}

fun resolveLogFile(home: Path): Path {
    return home.resolve("state").resolve("trace").resolve("log").resolve("partner-core.log").toAbsolutePath()
        .normalize()
}

fun findPartnerProcesses(home: Path, partnerJar: Path): List<ProcessHandle> {
    val pidFile = resolvePidFile(home)
    val pidProcess = readPidProcess(pidFile, partnerJar)
    if (pidProcess != null) {
        return listOf(pidProcess)
    }

    cleanupStalePidFile(pidFile)
    return findPartnerProcessesByCommandLine(partnerJar)
}

fun cleanupStalePidFile(pidFile: Path) {
    if (!Files.isRegularFile(pidFile)) return
    val pid = Files.readString(pidFile).trim().toLongOrNull()
    val process = pid?.let { ProcessHandle.of(it).orElse(null) }
    if (process == null || !process.isAlive) {
        Files.deleteIfExists(pidFile)
    }
}

fun deletePidFileIfMatches(pidFile: Path, pid: Long) {
    if (!Files.isRegularFile(pidFile)) return
    val recordedPid = Files.readString(pidFile).trim().toLongOrNull()
    if (recordedPid == pid) {
        Files.deleteIfExists(pidFile)
    }
}

fun waitForExit(process: ProcessHandle, timeoutSeconds: Long): Boolean {
    return try {
        process.onExit().get(timeoutSeconds, TimeUnit.SECONDS)
        true
    } catch (_: TimeoutException) {
        false
    }
}

private fun readPidProcess(pidFile: Path, partnerJar: Path): ProcessHandle? {
    if (!Files.isRegularFile(pidFile)) return null

    val pid = Files.readString(pidFile).trim().toLongOrNull() ?: return null
    val process = ProcessHandle.of(pid).orElse(null) ?: return null

    return if (process.isAlive && isPartnerProcess(process, partnerJar)) {
        process
    } else {
        null
    }
}

private fun findPartnerProcessesByCommandLine(partnerJar: Path): List<ProcessHandle> {
    val currentPid = ProcessHandle.current().pid()
    return ProcessHandle.allProcesses()
        .filter { it.pid() != currentPid }
        .filter { it.isAlive }
        .filter { isPartnerProcess(it, partnerJar) }
        .toList()
}

private fun isPartnerProcess(process: ProcessHandle, partnerJar: Path): Boolean {
    val jarPath = partnerJar.toString()
    val info = process.info()
    val arguments = info.arguments().orElse(emptyArray()) ?: emptyArray()
    val commandLine = info.commandLine().orElse("") ?: ""

    return arguments.any { it == jarPath } || commandLine.contains(jarPath)
}


fun runInForeground(home: Path, partnerJar: Path, logLevel: LogLevel) {
    val logFile = resolveLogFile(home)
    Files.createDirectories(logFile.parent)

    val process = createPartnerProcessBuilder(home, partnerJar, logLevel)
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

fun runInBackground(home: Path, partnerJar: Path, logLevel: LogLevel) {
    val pidFile = resolvePidFile(home)
    val logFile = resolveLogFile(home)
    val existingProcess = findPartnerProcesses(home, partnerJar).firstOrNull()

    if (existingProcess != null) {
        throw CommandInterrupted(text("control.run.error.alreadyRunning", existingProcess.pid()))
    }

    Files.createDirectories(pidFile.parent)
    Files.createDirectories(logFile.parent)

    val process = createPartnerProcessBuilder(home, partnerJar, logLevel)
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

private fun createPartnerProcessBuilder(home: Path, partnerJar: Path, logLevel: LogLevel): ProcessBuilder {
    return ProcessBuilder(
        "java",
        "-Dpartner.log.level=${logLevel.name.uppercase()}",
        "-DPARTNER_HOME=$home",
        "-jar",
        partnerJar.toString()
    )
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

enum class LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR
}

class LogLevelConverter : CommandLine.ITypeConverter<LogLevel> {
    override fun convert(value: String): LogLevel {
        return LogLevel.entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: throw CommandLine.TypeConversionException(
            "invalid log level '$value'. Valid values: ${
                LogLevel.entries.joinToString(", ") { it.name.lowercase() }
            }"
        )
    }
}
