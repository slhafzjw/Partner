package work.slhaf.partner.ctl.commands.init

import work.slhaf.partner.ctl.support.CommandInterrupted
import work.slhaf.partner.ctl.support.inheritCommand
import work.slhaf.partner.ctl.support.runCommand
import work.slhaf.partner.ctl.ui.Prompt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isDirectory
import kotlin.io.path.name

fun buildFromSource(home: Path, prompt: Prompt) {
    checkTool()

    val tempDir = Files.createTempDirectory("partnerctl-build-")
    val sourceDir = tempDir.resolve("Partner")
    val repoUrl = "https://gitea.slhaf.work/slhaf/Partner.git"
    val targetJar = home.resolve("resource").resolve("partner-core.jar")

    try {
        prompt.info("Cloning Partner source from $repoUrl")
        val cloneExitCode = inheritCommand(
            command = listOf("git", "clone", "--depth", "1", repoUrl, sourceDir.toString()),
        )
        if (cloneExitCode != 0) {
            throw CommandInterrupted("Failed to clone Partner source from $repoUrl")
        }

        prompt.info("Building Partner-Core and required modules")
        val buildExitCode = inheritCommand(
            command = listOf("mvn", "-pl", "Partner-Core", "-am", "package", "-DskipTests=true"),
            workingDirectory = sourceDir,
        )
        if (buildExitCode != 0) {
            throw CommandInterrupted("Failed to build Partner runtime. Please make sure Maven is using JDK 21.")
        }

        val builtJar = findPartnerCoreJar(sourceDir)
        Files.createDirectories(targetJar.parent)
        Files.copy(builtJar, targetJar, StandardCopyOption.REPLACE_EXISTING)
        prompt.success("Partner runtime installed at $targetJar")
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}

private fun checkTool() {

    fun buildToolLackedMessage(lack: Map<String, String>): String {
        return buildString {
            appendLine("Missing required build tools:")
            appendLine()
            lack.forEach { (tool, reason) ->
                appendLine("$tool:")
                appendLine("  $reason")
            }
            appendLine()
            appendLine("Install the missing tools, then rerun:")
            appendLine("  partnerctl init")
        }.trimEnd()
    }


    val lack = mutableMapOf<String, String>()
    if (runCommand(listOf("java", "--version")).exitCode != 0) {
        lack["java"] = "Required to run Maven and Partner runtime. Command failed: java --version"
    }
    if (runCommand(listOf("javac", "--version")).exitCode != 0) {
        lack["javac"] =
            "Required to compile Partner from source. Install a JDK, not just a JRE. Command failed: javac --version"
    }
    if (runCommand(listOf("git", "--version")).exitCode != 0) {
        lack["git"] = "Required to clone Partner source. Command failed: git --version"
    }
    if (runCommand(listOf("mvn", "--version")).exitCode != 0) {
        lack["mvn"] = "Required to build Partner from source. Command failed: mvn --version"
    }
    if (lack.isNotEmpty()) {
        throw CommandInterrupted(buildToolLackedMessage(lack))
    }
}

private fun findPartnerCoreJar(sourceDir: Path): Path {
    val targetDir = sourceDir.resolve("Partner-Core").resolve("target")
    if (!targetDir.isDirectory()) {
        throw CommandInterrupted("Partner-Core target directory does not exist: $targetDir")
    }

    return Files.list(targetDir).use { stream ->
        stream
            .filter { it.name.endsWith(".jar") }
            .filter { !it.name.startsWith("original-") }
            .filter { !it.name.endsWith("-sources.jar") }
            .filter { !it.name.endsWith("-javadoc.jar") }
            .max(Comparator.comparingLong { Files.size(it) })
            .orElseThrow { CommandInterrupted("Could not find built Partner Core jar in $targetDir") }
    }
}

