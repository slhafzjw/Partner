package work.slhaf.partner.ctl.commands.control

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
    return home.resolve("resource").resolve("partner-core.jar").toAbsolutePath().normalize()
}

fun resolvePidFile(home: Path): Path {
    return home.resolve("state").resolve("run").resolve("partner.pid").toAbsolutePath().normalize()
}

fun resolveLogFile(home: Path): Path {
    return home.resolve("state").resolve("trace").resolve("log").resolve("partner-core.log").toAbsolutePath().normalize()
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
