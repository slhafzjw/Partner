package work.slhaf.partner.ctl.commands

import picocli.CommandLine
import work.slhaf.partner.ctl.commands.control.resolveLogFile
import work.slhaf.partner.ctl.commands.control.resolvePartnerHome
import work.slhaf.partner.ctl.i18n.I18n.text
import java.nio.file.Files
import java.nio.file.Path

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
