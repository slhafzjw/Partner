package work.slhaf.partner.ctl.commands

import picocli.CommandLine
import work.slhaf.partner.ctl.i18n.I18n.text
import work.slhaf.partner.ctl.support.CommandInterrupted
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

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
                deletePidFileIfMatches(pidFile, pid)
                return@forEach
            }

            if (force) {
                println(text("control.shutdown.warn.force", pid))
                process.destroyForcibly()
                if (waitForExit(process, timeoutSeconds)) {
                    println(text("control.shutdown.success.stopped", pid))
                    deletePidFileIfMatches(pidFile, pid)
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

@CommandLine.Command(name = "log", description = ["Show Partner logs."])
class LogCommand : Runnable {

    @CommandLine.Option(names = ["--tail"], description = ["Number of log lines to show before exiting or following."])
    var tailLines: Int = 200

    @CommandLine.Option(names = ["-f", "--follow"], description = ["Follow appended log output."])
    var follow: Boolean = false

    override fun run() {
        val home = resolvePartnerHome()
        val logFile = resolveLogFile(home)

        if (!Files.exists(logFile)) {
            if (!follow) {
                println(text("control.log.info.notFound", logFile))
                return
            }
            println(text("control.log.info.waiting", logFile))
            waitForLogFile(logFile)
        }

        printLastLines(logFile, tailLines)

        if (follow) {
            followLog(logFile)
        }
    }
}

private fun runInForeground(home: Path, partnerJar: Path) {
    val logFile = resolveLogFile(home)
    Files.createDirectories(logFile.parent)

    val process = createPartnerProcessBuilder(home, partnerJar)
        .redirectInput(ProcessBuilder.Redirect.INHERIT)
        .redirectErrorStream(true)
        .start()

    val shutdownHook = Thread {
        if (process.isAlive) {
            process.destroy()
        }
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    val logOutput = Files.newOutputStream(
        logFile,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
    )

    val copier = thread(name = "partnerctl-run-log-tee") {
        logOutput.use { output ->
            tee(process.inputStream, output)
        }
    }

    val exitCode = process.waitFor()
    copier.join()
    runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }

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
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
        .redirectErrorStream(true)
        .start()

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
    return ProcessBuilder("java", "-jar", partnerJar.toString())
        .apply {
            environment()["PARTNER_HOME"] = home.toString()
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

private fun resolvePidFile(home: Path): Path {
    return home.resolve("state").resolve("run").resolve("partner.pid").toAbsolutePath().normalize()
}

private fun resolveLogFile(home: Path): Path {
    return home.resolve("state").resolve("trace").resolve("log").resolve("partner-core.log").toAbsolutePath().normalize()
}

private fun findPartnerProcesses(home: Path, partnerJar: Path): List<ProcessHandle> {
    val pidFile = resolvePidFile(home)
    val pidProcess = readPidProcess(pidFile, partnerJar)
    if (pidProcess != null) {
        return listOf(pidProcess)
    }

    cleanupStalePidFile(pidFile)
    return findPartnerProcessesByCommandLine(partnerJar)
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

private fun cleanupStalePidFile(pidFile: Path) {
    if (!Files.isRegularFile(pidFile)) return
    val pid = Files.readString(pidFile).trim().toLongOrNull()
    val process = pid?.let { ProcessHandle.of(it).orElse(null) }
    if (process == null || !process.isAlive) {
        Files.deleteIfExists(pidFile)
    }
}

private fun deletePidFileIfMatches(pidFile: Path, pid: Long) {
    if (!Files.isRegularFile(pidFile)) return
    val recordedPid = Files.readString(pidFile).trim().toLongOrNull()
    if (recordedPid == pid) {
        Files.deleteIfExists(pidFile)
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

private fun waitForExit(process: ProcessHandle, timeoutSeconds: Long): Boolean {
    return try {
        process.onExit().get(timeoutSeconds, TimeUnit.SECONDS)
        true
    } catch (_: TimeoutException) {
        false
    }
}

private fun tee(input: InputStream, logOutput: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        System.out.write(buffer, 0, read)
        System.out.flush()
        logOutput.write(buffer, 0, read)
        logOutput.flush()
    }
}

private fun printLastLines(logFile: Path, lineCount: Int) {
    if (lineCount <= 0) return

    val lines = ArrayDeque<String>()
    Files.newBufferedReader(logFile).use { reader ->
        while (true) {
            val line = reader.readLine() ?: break
            if (lines.size == lineCount) {
                lines.removeFirst()
            }
            lines.addLast(line)
        }
    }

    lines.forEach(::println)
}

private fun waitForLogFile(logFile: Path) {
    while (!Files.exists(logFile)) {
        Thread.sleep(LOG_FOLLOW_INTERVAL_MILLIS)
    }
}

private fun followLog(logFile: Path) {
    var position = Files.size(logFile)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    while (true) {
        if (!Files.exists(logFile)) {
            Thread.sleep(LOG_FOLLOW_INTERVAL_MILLIS)
            continue
        }

        val size = Files.size(logFile)
        if (size < position) {
            position = 0
        }

        if (size > position) {
            Files.newInputStream(logFile).use { input ->
                var skipped = input.skip(position)
                while (skipped < position) {
                    val current = input.skip(position - skipped)
                    if (current <= 0) break
                    skipped += current
                }

                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    System.out.write(buffer, 0, read)
                    System.out.flush()
                    position += read
                }
            }
        }

        Thread.sleep(LOG_FOLLOW_INTERVAL_MILLIS)
    }
}

private const val LOG_FOLLOW_INTERVAL_MILLIS = 500L
