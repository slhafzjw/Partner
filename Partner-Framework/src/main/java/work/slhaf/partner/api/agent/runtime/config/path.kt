package work.slhaf.partner.api.agent.runtime.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class ConfigPaths(
    val workDir: Path,
    val configDir: Path,
    val workspaceDir: Path,
    val stateDir: Path,
    val resourcesDir: Path
) {
    fun ensureDirectories() {
        Files.createDirectories(workDir)
        Files.createDirectories(configDir)
        Files.createDirectories(workspaceDir)
        Files.createDirectories(stateDir)
        Files.createDirectories(resourcesDir)
    }
}

internal fun resolvePaths(): ConfigPaths {

    fun resolveWorkdir(): Path {
        val envHome = System.getenv("PARTNER_HOME")?.trim()
        if (!envHome.isNullOrEmpty()) {
            return Paths.get(envHome).toAbsolutePath().normalize()
        }

        val userHome = System.getProperty("user.home")
            ?: throw IllegalStateException(" System property 'user.home' is missing.")
        return Paths.get(userHome, ".partner").toAbsolutePath().normalize()
    }

    val workDir = resolveWorkdir()
    return ConfigPaths(
        workDir,
        workDir.resolve("config"),
        workDir.resolve("workspace"),
        workDir.resolve("state"),
        workDir.resolve("resources")
    ).apply { ensureDirectories() }
}
