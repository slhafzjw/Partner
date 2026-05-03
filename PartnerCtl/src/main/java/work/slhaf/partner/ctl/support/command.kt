package work.slhaf.partner.ctl.support

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Result of a completed external command.
 */
data class CommandResult(
    val command: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val success: Boolean get() = exitCode == 0
}

/**
 * Runs a command and captures stdout/stderr separately.
 */
fun runCommand(
    command: List<String>,
    workingDirectory: Path? = null,
    environment: Map<String, String> = emptyMap(),
    timeoutSeconds: Long? = null,
    charset: Charset = StandardCharsets.UTF_8,
): CommandResult {
    require(command.isNotEmpty()) { "command must not be empty" }

    val process = ProcessBuilder(command)
        .apply {
            if (workingDirectory != null) directory(workingDirectory.toFile())
            environment().putAll(environment)
        }
        .start()

    val executor = Executors.newFixedThreadPool(2)
    try {
        val stdout = executor.submit<String> { process.inputStream.readText(charset) }
        val stderr = executor.submit<String> { process.errorStream.readText(charset) }

        val finished = if (timeoutSeconds == null) {
            process.waitFor()
            true
        } else {
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        }

        if (!finished) {
            process.destroyForcibly()
            process.waitFor()
        }

        return CommandResult(
            command = command,
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout.get(),
            stderr = stderr.get(),
        )
    } finally {
        executor.shutdownNow()
    }
}

/**
 * Runs a command with stdout/stderr/stdin inherited from current process.
 */
fun inheritCommand(
    command: List<String>,
    workingDirectory: Path? = null,
    environment: Map<String, String> = emptyMap(),
): Int {
    require(command.isNotEmpty()) { "command must not be empty" }

    val process = ProcessBuilder(command)
        .apply {
            if (workingDirectory != null) directory(workingDirectory.toFile())
            environment().putAll(environment)
            inheritIO()
        }
        .start()

    return process.waitFor()
}

private fun InputStream.readText(charset: Charset): String {
    return bufferedReader(charset).use { it.readText() }
}
