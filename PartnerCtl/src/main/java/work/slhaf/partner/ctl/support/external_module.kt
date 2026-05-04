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

@Serializable
data class ModuleManifest(
    val id: String,
    val name: String,
    val withGateway: Boolean,
    val description: String = "",
    val source: Source,
    val install: Install,
    val config: Config? = null,
)

@Serializable
data class Source(
    val url: String,
    val sourceDirName: String,
    val buildCommand: List<String>,
    val artifactDirectory: String,
    val artifactPattern: String,
)

@Serializable
data class Install(
    val target: String,
)

@Serializable
data class Config(
    val target: String,
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