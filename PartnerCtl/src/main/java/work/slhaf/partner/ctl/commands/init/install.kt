package work.slhaf.partner.ctl.commands.init

import work.slhaf.partner.ctl.i18n.I18n.text
import work.slhaf.partner.ctl.support.SourceBuildInstallSpec
import work.slhaf.partner.ctl.support.buildAndInstallFromSource
import work.slhaf.partner.ctl.support.downloadTo
import work.slhaf.partner.ctl.support.registryIndex
import work.slhaf.partner.ctl.ui.Prompt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

private const val PARTNER_REPO_URL = "https://github.com/slhaf/Partner.git"

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
            installRelativePath = Paths.get("resources", "partner-core.jar"),
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

fun downloadFromRelease(home: Path, prompt: Prompt) {
    prompt.info(text("init.install.method.downloadFromRelease.startDownloading"))
    val path = home.resolve("resources/partner-core.jar").toAbsolutePath().normalize()
    downloadTo(registryIndex.partner.latestRelease.url, path) { downloaded, total ->
        if (total != null && total > 0) {
            val percent = downloaded * 100 / total
            updateLine(
                text(
                    "init.install.method.downloadFromRelease.progress.percent",
                    percent
                )
            )
        } else {
            updateLine(
                text(
                    "init.install.method.downloadFromRelease.progress.size",
                    downloaded / 1024
                )
            )
        }
    }
    finishLine(text("init.install.method.downloadFromRelease.done"))
    if (!path.exists()) {
        throw IllegalStateException("Unable to find downloaded partner release at $path")
    }
    prompt.success(text("init.install.method.downloadFromRelease.success"))
}

fun updateLine(text: String) {
    print("\r\u001B[2K$text")
    System.out.flush()
}

fun finishLine(text: String) {
    updateLine(text)
    println()
}