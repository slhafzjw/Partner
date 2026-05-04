package work.slhaf.partner.ctl.support

import work.slhaf.partner.ctl.ui.Prompt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Runtime specification for building a JVM/Maven project from source and installing one built artifact.
 *
 * Path semantics:
 * - [sourceDirName] is the child directory name created under a temporary build directory.
 * - [artifactDirectory] is resolved relative to the cloned source root: tempDir/sourceDirName/artifactDirectory.
 * - [installRelativePath] is resolved relative to Partner home: home/installRelativePath.
 */
data class SourceBuildInstallSpec(
    /** Display name used in prompt messages. */
    val displayName: String,

    /** Git repository URL used by `git clone --depth 1`. */
    val repoUrl: String,

    /** Directory name for the cloned repository under the temporary build directory. */
    val sourceDirName: String,

    /** Build command executed with the cloned source root as working directory. */
    val buildCommand: List<String>,

    /** Directory containing build artifacts, relative to the cloned source root. */
    val artifactDirectory: Path,

    /** Selects the artifact to install from the resolved artifact directory. */
    val artifactSelector: (Path) -> Path?,

    /** Install target path, relative to Partner home. */
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

