package work.slhaf.partner.ctl.support

import work.slhaf.partner.ctl.ui.Prompt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class SourceBuildInstallSpec(
    val displayName: String,
    val repoUrl: String,
    val sourceDirName: String,
    val buildCommand: List<String>,
    val artifactDirectory: Path,
    val artifactSelector: (Path) -> Path?,
    val installRelativePath: Path,
)

fun buildAndInstallFromSource(
    home: Path,
    prompt: Prompt,
    spec: SourceBuildInstallSpec,
) {
    require(spec.sourceDirName.isNotBlank()) { "sourceDirName must not be blank" }
    require(spec.buildCommand.isNotEmpty()) { "buildCommand must not be empty" }

    checkTool()

    val tempDir = Files.createTempDirectory("partnerctl-build-")
    val sourceDir = tempDir.resolve(spec.sourceDirName)
    val targetPath = home.resolve(spec.installRelativePath)

    try {
        prompt.info("Cloning ${spec.displayName} source from ${spec.repoUrl}")
        val cloneExitCode = inheritCommand(
            command = listOf("git", "clone", "--depth", "1", spec.repoUrl, sourceDir.toString()),
        )
        if (cloneExitCode != 0) {
            throw CommandInterrupted("Failed to clone ${spec.displayName} source from ${spec.repoUrl}")
        }

        prompt.info("Building ${spec.displayName}")
        val buildExitCode = inheritCommand(
            command = spec.buildCommand,
            workingDirectory = sourceDir,
        )
        if (buildExitCode != 0) {
            throw CommandInterrupted("Failed to build ${spec.displayName}.")
        }

        val artifactDir = sourceDir.resolve(spec.artifactDirectory)
        val artifact = spec.artifactSelector(artifactDir)
            ?: throw CommandInterrupted("Could not find built ${spec.displayName} artifact in $artifactDir")

        Files.createDirectories(targetPath.parent)
        Files.copy(artifact, targetPath, StandardCopyOption.REPLACE_EXISTING)
        prompt.success("${spec.displayName} installed at $targetPath")
    } finally {
        runCatching { tempDir.toFile().deleteRecursively() }
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
        lack["java"] = "Required to run Maven. Command failed: java --version"
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

