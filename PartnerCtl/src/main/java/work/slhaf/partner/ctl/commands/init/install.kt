package work.slhaf.partner.ctl.commands.init

import work.slhaf.partner.ctl.support.SourceBuildInstallSpec
import work.slhaf.partner.ctl.support.buildAndInstallFromSource
import work.slhaf.partner.ctl.ui.Prompt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name

private const val PARTNER_REPO_URL = "https://gitea.slhaf.work/slhaf/Partner.git"

fun buildFromSource(home: Path, prompt: Prompt) {
    buildAndInstallFromSource(
        home = home,
        prompt = prompt,
        spec = SourceBuildInstallSpec(
            displayName = "Partner runtime",
            repoUrl = PARTNER_REPO_URL,
            sourceDirName = "Partner",
            buildCommand = listOf("mvn", "-pl", "Partner-Core", "-am", "package", "-DskipTests=true"),
            artifactDirectory = Paths.get("Partner-Core", "target"),
            artifactSelector = ::findLargestJar,
            installRelativePath = Paths.get("resource", "partner-core.jar"),
        ),
    )
}

private fun findLargestJar(directory: Path): Path? {
    if (!directory.isDirectory()) return null

    return Files.list(directory).use { stream ->
        stream
            .filter { it.name.endsWith(".jar") }
            .filter { !it.name.startsWith("original-") }
            .filter { !it.name.endsWith("-sources.jar") }
            .filter { !it.name.endsWith("-javadoc.jar") }
            .max(Comparator.comparingLong { Files.size(it) })
            .orElse(null)
    }
}
