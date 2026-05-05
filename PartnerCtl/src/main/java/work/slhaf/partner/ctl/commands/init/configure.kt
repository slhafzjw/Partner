package work.slhaf.partner.ctl.commands.init

import kotlinx.serialization.json.*
import work.slhaf.partner.ctl.commands.data.GatewayConfig
import work.slhaf.partner.ctl.commands.data.OpenAiCompatible
import work.slhaf.partner.ctl.commands.data.ProviderConfig
import work.slhaf.partner.ctl.i18n.I18n.text
import work.slhaf.partner.ctl.support.*
import work.slhaf.partner.ctl.ui.Prompt
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory

fun configureWebSocketGateway(prompt: Prompt): GatewayConfig.ChannelConfig {
    prompt.section(text("configure.gateway.websocket.section"))

    val port = prompt.ask(text("configure.gateway.websocket.port.label"), "29600") {
        val intValue = it.toIntOrNull() ?: return@ask text("configure.gateway.websocket.port.error.int")
        if (intValue !in 1..65565) text("configure.gateway.websocket.port.error.range") else null
    }.toInt()

    val heartbeatInterval = prompt.ask(text("configure.gateway.websocket.heartbeatInterval.label"), "10000") {
        it.toLongOrNull() ?: return@ask text("configure.gateway.websocket.heartbeatInterval.error.long")
        return@ask null
    }.toLong()

    val hostname = prompt.ask(text("configure.gateway.websocket.hostname.label"), "127.0.0.1") {
        val host = it.trim()
        return@ask when {
            host.isEmpty() -> text("configure.gateway.websocket.hostname.error.required")
            host.contains(Regex("\\s")) -> text("configure.gateway.websocket.hostname.error.whitespace")
            host.contains("://") -> text("configure.gateway.websocket.hostname.error.protocol")
            host.contains("/") -> text("configure.gateway.websocket.hostname.error.path")
            looksLikeHostWithPort(host) -> text("configure.gateway.websocket.hostname.error.port")
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
    prompt.section(text("configure.gateway.external.section", manifest.name))

    prompt.details(
        title = text("configure.gateway.external.details.title"),
        items = listOf(
            text("configure.gateway.external.details.description") to manifest.description,
            text("configure.gateway.external.details.source") to manifest.source.url,
            text("configure.gateway.external.details.buildCommand") to manifest.source.buildCommand.joinToString(" "),
            text("configure.gateway.external.details.artifact") to "${manifest.source.artifactDirectory}/${manifest.source.artifactPattern}",
            text("configure.gateway.external.details.installTarget") to manifest.install.target,
            text("configure.gateway.external.details.configTarget") to (manifest.config?.target ?: text("configure.gateway.external.details.noConfig")),
        ),
    )

    if (!prompt.confirm(text("configure.gateway.external.confirmContinue"), true)) {
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
        FieldType.INT -> value.toIntOrNull()?.let { null } ?: text("configure.field.error.int", field.label)
        FieldType.NUMBER -> value.toDoubleOrNull()?.let { null } ?: text("configure.field.error.number", field.label)
        FieldType.BOOLEAN -> value.toBooleanStrictOrNull()?.let { null } ?: text("configure.field.error.boolean", field.label)
        FieldType.RAW_JSON -> runCatching { Json.parseToJsonElement(value) }
            .exceptionOrNull()
            ?.let { text("configure.field.error.rawJson", field.label) }
    }
}

fun configureOpenAiCompatible(prompt: Prompt, defaultAlreadySet: Boolean): ProviderConfig {
    val name = if (defaultAlreadySet) {
        prompt.ask(text("configure.model.openAiCompatible.providerName.label")) {
            if (it == "default") {
                text("configure.model.openAiCompatible.providerName.error.duplicateDefault")
            } else {
                null
            }
        }
    } else {
        "default"
    }

    val baseUrl = prompt.ask(text("configure.model.openAiCompatible.baseUrl.label")) { value ->
        validateNetworkUrl(value)
    }

    val apikey = prompt.ask(text("configure.model.openAiCompatible.apiKey.label"))
    val defaultModel = prompt.ask(text("configure.model.openAiCompatible.defaultModel.label"))
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
        return text("configure.model.openAiCompatible.baseUrl.error.required")
    }

    val uri = runCatching { URI(trimmed) }.getOrElse {
        return text("configure.model.openAiCompatible.baseUrl.error.validUrl")
    }

    return when {
        uri.scheme !in setOf("http", "https") -> text("configure.model.openAiCompatible.baseUrl.error.scheme")
        uri.host.isNullOrBlank() -> text("configure.model.openAiCompatible.baseUrl.error.host")
        uri.rawUserInfo != null -> text("configure.model.openAiCompatible.baseUrl.error.userInfo")
        uri.rawFragment != null -> text("configure.model.openAiCompatible.baseUrl.error.fragment")
        else -> null
    }
}