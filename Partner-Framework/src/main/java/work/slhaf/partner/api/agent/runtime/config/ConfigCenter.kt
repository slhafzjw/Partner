package work.slhaf.partner.api.agent.runtime.config

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import org.slf4j.LoggerFactory
import work.slhaf.partner.api.agent.runtime.exception.AgentLaunchFailedException
import work.slhaf.partner.api.common.support.DirectoryWatchSupport
import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

object ConfigCenter : AutoCloseable {

    private val log = LoggerFactory.getLogger(ConfigCenter::class.java)
    val paths = resolvePaths()
    private val registrations = ConcurrentHashMap<Path, ConfigRegistration<out Config>>()
    private var watchExecutor: ExecutorService? = null
    private var watchSupport: DirectoryWatchSupport? = null

    @Volatile
    private var started = false

    @Synchronized
    fun register(configurable: Configurable) {

        check(!started) {
            "ConfigCenter does not allow registration after watching started"
        }

        val declared = configurable.declare()
        val normalized = mutableMapOf<Path, ConfigRegistration<out Config>>()

        declared.forEach { (path, registration) ->
            val normalizedPath = normalizeRelativePath(path)

            check(normalized.putIfAbsent(normalizedPath, registration) == null) {
                "Duplicated config path declared in the same configurable: $normalizedPath"
            }

        }

        normalized.forEach { (path, registration) ->
            check(registrations.putIfAbsent(path, registration) == null) {
                "Config path already registered: $path"
            }
        }

    }

    @Synchronized
    fun start() {
        if (watchSupport != null) {
            return
        }

        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val support = DirectoryWatchSupport(
            DirectoryWatchSupport.Context(paths.configDir),
            executor,
            -1
        ) {
            initAll()
        }.onCreate(this::handleUpsert)
            .onModify(this::handleUpsert)
            .onDelete(this::handleDelete)
            .onOverflow { _, _ -> reconcileAll() }

        watchExecutor = executor
        watchSupport = support
        support.start()
        log.info("ConfigCenter 文件监听注册完毕: {}", paths.configDir)
    }

    @Suppress("UNCHECKED_CAST")
    fun initAll() {
        registrations.forEach { (path, registration) ->
            val pair = loadConfig(path, registration)
            if (pair != null) {
                (registration as ConfigRegistration<Config>).init(pair.first, pair.second)
                return
            }
            val defaultConfig = registration.defaultConfig()
            if (defaultConfig != null) {
                (registration as ConfigRegistration<Config>).init(defaultConfig, null)
            }
            val configDoc = resolveConfigDoc(registration.type())
            throw AgentLaunchFailedException("Failed to init config, related path: $path, config definition: $configDoc")
        }

    }

    private fun resolveConfigDoc(type: Class<out Config>): String {
        val kotlinProperties = if (type.isKotlinClass()) {
            type.kotlin.memberProperties.associateBy { property ->
                property.javaField?.name ?: property.name
            }
        } else {
            emptyMap()
        }
        val fieldDocs = type.declaredFields.asSequence()
            .filterNot(::shouldSkipField)
            .map { field -> resolveFieldDoc(type, field, kotlinProperties[field.name]) }
            .toList()
        return buildString {
            append("Expected fields:")
            if (fieldDocs.isNotEmpty()) {
                append('\n')
                append(fieldDocs.joinToString("\n\n"))
            }
        }
    }

    private fun resolveFieldDoc(
        ownerType: Class<out Config>,
        field: Field,
        kotlinProperty: KProperty1<out Any, *>?
    ): String {
        val configDoc = field.getAnnotation(ConfigDoc::class.java)
            ?: kotlinProperty?.annotations?.filterIsInstance<ConfigDoc>()?.firstOrNull()
        val nullableInfo = resolveNullableInfo(ownerType, field, kotlinProperty)
        val lines = mutableListOf(
            "- ${field.name}: ${resolveDisplayType(field.type)}",
            "  Description: ${configDoc?.description ?: "No description provided"}"
        )
        configDoc?.unit?.takeIf { it.isNotBlank() }?.let { lines += "  Unit: $it" }
        configDoc?.constraint?.takeIf { it.isNotBlank() }?.let { lines += "  Constraint: $it" }
        configDoc?.example?.takeIf { it.isNotBlank() }?.let { lines += "  Example: $it" }
        lines += buildString {
            append("  Nullable: ")
            append(nullableInfo.nullable)
            nullableInfo.note?.let {
                append(" (")
                append(it)
                append(')')
            }
        }
        return lines.joinToString("\n")
    }

    private fun shouldSkipField(field: Field): Boolean {
        return field.isSynthetic || Modifier.isStatic(field.modifiers)
    }

    private fun handleUpsert(thisDir: Path, context: Path?) {
        if (context == null || !Files.isRegularFile(context) || !isJsonFile(context)) {
            return
        }
        reloadIfRegistered(context)
    }

    private fun handleDelete(thisDir: Path, context: Path?) {
        if (context == null || !isJsonFile(context)) {
            return
        }
        val relativePath = toRelativeConfigPath(context) ?: return
        if (!registrations.containsKey(relativePath)) {
            return
        }
        log.info("Config deleted, skipped reload: {}", relativePath)
    }

    private fun reconcileAll() {
        val configDir = paths.configDir
        if (!Files.isDirectory(configDir)) {
            return
        }
        Files.walk(configDir).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter(::isJsonFile)
                .forEach(this::reloadIfRegistered)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun reloadIfRegistered(file: Path) {
        val relativePath = toRelativeConfigPath(file) ?: return
        val registration = registrations[relativePath] ?: return
        try {
            val pair = loadConfig(file, registration)
            if (pair != null) {
                (registration as ConfigRegistration<Config>).onReload(pair.first, pair.second)
            }
        } catch (e: Exception) {
            log.error("Config reload failed: {}", relativePath, e)
        }
    }

    private fun loadConfig(file: Path, registration: ConfigRegistration<out Config>): Pair<Config, JSONObject>? {
        return try {
            val json = JSON.parseObject(Files.readString(file, StandardCharsets.UTF_8))
            val config = json.toJavaObject(registration.type())
            config to json
        } catch (e: Exception) {
            log.error("Config reload failed: {}", file, e)
            null
        }
    }

    private fun toRelativeConfigPath(file: Path): Path? {
        val normalizedFile = file.toAbsolutePath().normalize()
        val normalizedConfigDir = paths.configDir.toAbsolutePath().normalize()
        if (!normalizedFile.startsWith(normalizedConfigDir)) {
            return null
        }
        return normalizedConfigDir.relativize(normalizedFile).normalize()
    }

    private fun isJsonFile(path: Path): Boolean {
        return path.fileName.toString().endsWith(".json")
    }

    @Synchronized
    override fun close() {
        try {
            watchSupport?.close()
        } catch (e: IOException) {
            log.warn("Failed to close ConfigCenter watch service", e)
        } finally {
            watchSupport = null
        }
        watchExecutor?.shutdownNow()
        watchExecutor = null
    }

    private fun normalizeRelativePath(path: Path): Path {
        require(!path.isAbsolute) {
            "Config path must be relative: $path"
        }
        return path.normalize()
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
    fun init(config: T, json: JSONObject?)
    fun onReload(config: T, json: JSONObject?) {}
    fun defaultConfig(): T?
}

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.PROPERTY
)
annotation class ConfigDoc(
    val description: String,
    val unit: String = "",
    val constraint: String = "",
    val example: String = "",
)
