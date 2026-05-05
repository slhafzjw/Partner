package work.slhaf.partner.ctl.commands.init

import kotlinx.serialization.json.*
import work.slhaf.partner.ctl.commands.data.GatewayConfig
import work.slhaf.partner.ctl.commands.data.OpenAiCompatible
import work.slhaf.partner.ctl.commands.data.ProviderConfig
import work.slhaf.partner.ctl.support.*
import work.slhaf.partner.ctl.ui.Prompt
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory

fun configureWebSocketGateway(prompt: Prompt): GatewayConfig.ChannelConfig {
    prompt.section("Configure Gateway: WebSocket Gateway")

    val port = prompt.ask("port", "29600") {
        val intValue = it.toIntOrNull() ?: return@ask "WebSocket port only accepts int value"
        if (intValue !in 1..65565) "WebSocket port should be between 1 and 65565" else null
    }.toInt()

    val heartbeatInterval = prompt.ask("heartbeat interval", "10000") {
        it.toLongOrNull() ?: return@ask "Heartbeat interval only accepts long value"
        return@ask null
    }.toLong()

    val hostname = prompt.ask("Listening hostname", "127.0.0.1") {
        val host = it.trim()
        return@ask when {
            host.isEmpty() -> "Hostname is required"
            host.contains(Regex("\\s")) -> "Hostname must not contain whitespace."
            host.contains("://") -> "Do not include protocol. Use hostname only, for example: 127.0.0.1"
            host.contains("/") -> "Do not include path. Use hostname only."
            looksLikeHostWithPort(host) -> "Do not include port here. Port is configured separately."
            else -> null
        }
    }
    return GatewayConfig.ChannelConfig(
        "websocket_channel",
        buildJsonObject {
            put("port", port)
            put("heartbeat_interval", heartbeatInterval)
            put("hostname", hostname)
        }
    )
}

private fun looksLikeHostWithPort(value: String): Boolean {
    // IPv6 contains ':', so only treat single-colon host:port as invalid.
    val colonCount = value.count { it == ':' }
    return colonCount == 1 && value.substringAfter(':').all { it.isDigit() }
}

fun configureExternalGateway(home: Path, prompt: Prompt, manifest: ModuleManifest): GatewayConfig.ChannelConfig? {
    prompt.section("Configure Gateway: ${manifest.name}")

    prompt.details(
        title = "Gateway module details",
        items = listOf(
            "Description" to manifest.description,
            "Source" to manifest.source.url,
            "Build command" to manifest.source.buildCommand.joinToString(" "),
            "Artifact" to "${manifest.source.artifactDirectory}/${manifest.source.artifactPattern}",
            "Install target" to manifest.install.target,
            "Config target" to (manifest.config?.target ?: "No config"),
        ),
    )

    if (!prompt.confirm("Continue installation?", true)) {
        return null
    }

    buildAndInstallFromSource(
        home,
        prompt,
        SourceBuildInstallSpec(
            displayName = manifest.name,
            repoUrl = manifest.source.url,
            sourceDirName = manifest.source.sourceDirName,
            buildCommand = manifest.source.buildCommand,
            artifactDirectory = Paths.get(manifest.source.artifactDirectory),
            artifactSelector = { artifactSelector(it, manifest.source.artifactPattern) },
            installRelativePath = Paths.get(manifest.install.target)
        )
    )

    val params = configureFields(prompt, manifest.config?.fields.orEmpty())
    return GatewayConfig.ChannelConfig(
        channelName = manifest.id,
        params = params,
    )
}

private fun artifactSelector(path: Path, pattern: String): Path? {
    if (!path.isDirectory()) return null

    return Files.newDirectoryStream(path, pattern).use { stream ->
        stream
            .asSequence()
            .filter { Files.isRegularFile(it) }
            .maxByOrNull { Files.size(it) }
    }
}

private fun configureFields(prompt: Prompt, fields: List<Field>) = buildJsonObject {
    fields.forEach { field ->
        val value = askField(prompt, field) ?: return@forEach
        put(field.name, value)
    }
}

private fun askField(prompt: Prompt, field: Field): JsonElement? {
    val rawValue = when (field.type) {
        FieldType.BOOLEAN -> prompt.confirm(
            label = field.label,
            defaultValue = field.default?.toBooleanStrictOrNull() ?: true,
        ).toString()

        else -> prompt.ask(
            label = field.label,
            defaultValue = field.default,
            required = field.required,
        ) { value ->
            validateFieldValue(field, value)
        }
    }

    if (rawValue.isBlank() && !field.required) return null

    return when (field.type) {
        FieldType.STRING -> JsonPrimitive(rawValue)
        FieldType.INT -> JsonPrimitive(rawValue.toInt())
        FieldType.NUMBER -> JsonPrimitive(rawValue.toDouble())
        FieldType.BOOLEAN -> JsonPrimitive(rawValue.toBooleanStrict())
        FieldType.RAW_JSON -> Json.parseToJsonElement(rawValue)
    }
}

@Suppress("KotlinConstantConditions")
private fun validateFieldValue(field: Field, value: String): String? {
    if (value.isBlank() && !field.required) return null

    return when (field.type) {
        FieldType.STRING -> null
        FieldType.INT -> value.toIntOrNull()?.let { null } ?: "${field.label} only accepts int value"
        FieldType.NUMBER -> value.toDoubleOrNull()?.let { null } ?: "${field.label} only accepts number value"
        FieldType.BOOLEAN -> value.toBooleanStrictOrNull()?.let { null } ?: "${field.label} only accepts true or false"
        FieldType.RAW_JSON -> runCatching { Json.parseToJsonElement(value) }
            .exceptionOrNull()
            ?.let { "${field.label} only accepts valid JSON" }
    }
}

fun configureOpenAiCompatible(prompt: Prompt, defaultAlreadySet: Boolean): ProviderConfig {
    val name = if (defaultAlreadySet) {
        prompt.ask("Provider name") {
            if (it == "default") {
                "Default provider cannot be duplicate"
            } else {
                null
            }
        }
    } else {
        "default"
    }

    val baseUrl = prompt.ask("Base url") { value ->
        validateNetworkUrl(value)
    }

    val apikey = prompt.ask("Apikey")
    val defaultModel = prompt.ask("Default model")
    return OpenAiCompatible(
        name = name,
        baseUrl = baseUrl,
        apiKey = apikey,
        defaultModel = defaultModel
    )
}

private fun validateNetworkUrl(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return "Base url is required"
    }

    val uri = runCatching { URI(trimmed) }.getOrElse {
        return "Base url must be a valid URL"
    }

    return when {
        uri.scheme !in setOf("http", "https") -> "Base url must start with http:// or https://"
        uri.host.isNullOrBlank() -> "Base url must include a valid host"
        uri.rawUserInfo != null -> "Base url must not include user info"
        uri.rawFragment != null -> "Base url must not include fragment"
        else -> null
    }
}