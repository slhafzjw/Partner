package work.slhaf.partner.api.agent.runtime.config

import java.nio.file.Path

object ConfigCenter {

    val paths = resolvePaths()
    private val registrations = mutableMapOf<Path, ConfigRegistration<out Config>>()

    fun register(configurable: Configurable) {

        fun normalizeRelativePath(path: Path): Path {
            require(!path.isAbsolute) {
                "Config path must be relative: $path"
            }
            return path.normalize()
        }

        val declared = configurable.declare()
        val normalized = mutableMapOf<Path, ConfigRegistration<out Config>>()

        declared.forEach { (path, registration) ->
            val normalizedPath = normalizeRelativePath(path)

            check(!normalized.containsKey(normalizedPath)) {
                "Duplicated config path declared in the same configurable: $normalizedPath"
            }

            check(!registrations.containsKey(normalizedPath)) {
                "Config path already registered: $normalizedPath"
            }

            normalized[normalizedPath] = registration
        }

        registrations.putAll(normalized)
    }

}

abstract class Config

interface Configurable {
    fun declare(): Map<Path, ConfigRegistration<out Config>>
    fun register() {
        ConfigCenter.register(this)
    }
}

interface ConfigRegistration<T : Config> {
    fun type(): Class<T>
    fun init(config: T)
    fun onReload(config: T) {}
}