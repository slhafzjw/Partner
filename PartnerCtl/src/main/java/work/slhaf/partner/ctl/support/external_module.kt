package work.slhaf.partner.ctl.support

import kotlinx.serialization.Serializable

private fun loadModules(): Set<ModuleManifest> {
    // TODO: 待实现具体加载逻辑
    return emptySet()
}

fun loadAvailableGateway(): Set<ModuleManifest> {
    return loadModules().filter { it.withGateway }.toSet()
}

fun loadExternalModule(): Set<ModuleManifest> {
    return loadModules().filter { !it.withGateway }.toSet()
}

/**
 * External module manifest loaded from the module registry.
 *
 * This type models the JSON contract of a registry entry. Paths are stored as strings here because
 * manifests are external JSON documents. Runtime code should convert path-like fields to [java.nio.file.Path]
 * only when building execution specs.
 */
@Serializable
data class ModuleManifest(
    /** Stable module id. Also used as gateway channel name for gateway modules. */
    val id: String,

    /** Human-readable module name. */
    val name: String,

    /** Whether this module can provide a gateway channel. */
    val withGateway: Boolean,

    /** Human-readable module description shown before installation. */
    val description: String = "",

    val source: Source,
    val install: Install,
    val config: Config? = null,
)

@Serializable
data class Source(
    /** Git repository URL used as the module source. */
    val url: String,

    /** Directory name for the cloned repository under the temporary build directory. */
    val sourceDirName: String,

    /** Build command executed with the cloned source root as working directory. */
    val buildCommand: List<String>,

    /** Directory containing build artifacts, relative to the cloned source root. */
    val artifactDirectory: String,

    /** Glob pattern used inside [artifactDirectory] to select the artifact to install. */
    val artifactPattern: String,
)

@Serializable
data class Install(
    /** Install target path, relative to Partner home. */
    val target: String,
)

@Serializable
data class Config(
    /** Config file target path, relative to Partner home. */
    val target: String,

    /** Interactive fields used to generate the module config object. */
    val fields: List<Field> = emptyList(),
)

@Serializable
data class Field(
    val name: String,
    val label: String,
    val type: FieldType,
    val default: String? = null,
    val required: Boolean = true,
)

@Serializable
enum class FieldType {
    STRING,
    INT,
    NUMBER,
    BOOLEAN,
    RAW_JSON,
}