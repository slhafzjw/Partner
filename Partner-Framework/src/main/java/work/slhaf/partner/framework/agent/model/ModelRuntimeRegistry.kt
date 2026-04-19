package work.slhaf.partner.framework.agent.model

import com.alibaba.fastjson2.JSONObject
import org.slf4j.LoggerFactory
import work.slhaf.partner.framework.agent.config.Config
import work.slhaf.partner.framework.agent.config.ConfigDoc
import work.slhaf.partner.framework.agent.config.ConfigRegistration
import work.slhaf.partner.framework.agent.config.Configurable
import work.slhaf.partner.framework.agent.exception.ModelRegistryException
import work.slhaf.partner.framework.agent.exception.ModelRegistryStartupException
import work.slhaf.partner.framework.agent.model.ProviderConfig.ProviderType.OPENAI_COMPATIBLE
import work.slhaf.partner.framework.agent.model.provider.ModelProvider
import work.slhaf.partner.framework.agent.model.provider.ProviderOverride
import work.slhaf.partner.framework.agent.model.provider.openai.OpenAiCompatibleProvider
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object ModelRuntimeRegistry : Configurable, ConfigRegistration<ModelRuntimeRegistryConfig> {

    private const val DEFAULT_PROVIDER = "default"
    private const val COMPONENT_NAME = "model-runtime-registry"

    /**
     * 基础的 provider 提供商，可 fork 出新的 runtime provider，必须提供一个 default provider
     */
    private val baseProvider = mutableMapOf<String, ModelProvider>()

    /**
     * 根据模块进行对应的 provider
     */
    private val runtimeProvider = mutableMapOf<String, ModelProvider>()

    private val providerLock = ReentrantLock()
    private val log = LoggerFactory.getLogger(ModelRuntimeRegistry::class.java)

    fun resolveProvider(modelKey: String): ModelProvider = providerLock.withLock {
        val provider = runtimeProvider[modelKey]
        if (provider != null) {
            return provider
        }
        return baseProvider[DEFAULT_PROVIDER]!!
    }

    private fun registerProvider(config: ProviderConfig) {
        when (config) {
            is OpenAiCompatibleProviderConfig -> baseProvider[config.name] =
                OpenAiCompatibleProvider(
                    config.name,
                    DEFAULT_PROVIDER,
                    config.baseUrl,
                    config.apiKey,
                    config.defaultModel
                )
        }
    }

    private fun forkProvider(config: RuntimeProviderConfig) {
        val provider = baseProvider[config.providerName]
            ?: throw runtimeModelException(
                "Provider ${config.providerName} not found",
                config.providerName,
                config.modelKey,
                config.override
            )
        val override = config.override

        try {
            runtimeProvider[config.modelKey] = provider.fork(config.modelKey, override)
        } catch (e: Exception) {
            throw runtimeModelException(
                "Failed to build runtime provider for model key ${config.modelKey}",
                config.providerName,
                config.modelKey,
                override,
                e
            )
        }
    }

    override fun declare(): Map<Path, ConfigRegistration<out Config>> {
        return mapOf(Path.of("model.json") to this)
    }

    override fun type(): Class<ModelRuntimeRegistryConfig> = ModelRuntimeRegistryConfig::class.java

    override fun init(config: ModelRuntimeRegistryConfig, json: JSONObject?) = providerLock.withLock {
        val acceptableConfig = try {
            parseJsonConfig(json)
        } catch (e: Exception) {
            log.warn("Unable to load model config", e)
            config
        }

        try {
            applyConfig(acceptableConfig)
        } catch (e: ModelRegistryException) {
            throw ModelRegistryStartupException(
                e.message ?: "Failed to apply model runtime config",
                e.providerName,
                e.modelKey,
                e.override,
                e
            )
        }
    }

    override fun onReload(config: ModelRuntimeRegistryConfig, json: JSONObject?) = providerLock.withLock {
        val root = json ?: return@withLock
        val baseProviderSnapshot = baseProvider.toMap()
        val runtimeProviderSnapshot = runtimeProvider.toMap()
        try {
            val parsedConfig = parseJsonConfig(root)
            applyConfig(parsedConfig)
        } catch (e: Exception) {
            log.error("Error while loading runtime provider config", e)
            baseProvider.clear()
            baseProvider.putAll(baseProviderSnapshot)
            runtimeProvider.clear()
            runtimeProvider.putAll(runtimeProviderSnapshot)
        }
    }

    private fun parseJsonConfig(json: JSONObject?): ModelRuntimeRegistryConfig {
        if (json == null) {
            throw runtimeModelException("Unable to find model config")
        }
        val providerConfigSet = json.getJSONArray("providerConfigSet").filterIsInstance<JSONObject>()
            .map { config ->
                val type = config.getString("type")
                val config = if (type.equals(OPENAI_COMPATIBLE.name.uppercase())) {
                    config.toJavaObject(OpenAiCompatibleProviderConfig::class.java)
                } else {
                    null
                }
                if (config == null) {
                    throw runtimeModelException("Unknown config type: $type")
                }
                config
            }.toSet()

        val runtimeConfigSet = json.getJSONArray("runtimeConfigSet").filterIsInstance<JSONObject>()
            .map { config ->
                config.toJavaObject(RuntimeProviderConfig::class.java)
            }.toSet()

        return ModelRuntimeRegistryConfig(providerConfigSet, runtimeConfigSet)
    }

    override fun defaultConfig(): ModelRuntimeRegistryConfig? {
        val defaultBaseUrl = System.getenv("PARTNER_DEFAULT_BASE_URL") ?: return null
        val defaultApiKey = System.getenv("PARTNER_DEFAULT_API_KEY") ?: return null
        val defaultModel = System.getenv("PARTNER_DEFAULT_MODEL") ?: return null
        return ModelRuntimeRegistryConfig(
            setOf(
                OpenAiCompatibleProviderConfig(
                    "default",
                    OPENAI_COMPATIBLE,
                    defaultModel, defaultBaseUrl, defaultApiKey
                )
            ), setOf()
        )
    }

    private fun applyConfig(config: ModelRuntimeRegistryConfig) {
        baseProvider.clear()
        runtimeProvider.clear()
        config.providerConfigSet.forEach { registerProvider(it) }
        if (!baseProvider.containsKey(DEFAULT_PROVIDER)) {
            throw runtimeModelException("Provider default not found", DEFAULT_PROVIDER)
        }
        config.runtimeConfigSet.forEach { forkProvider(it) }
    }

    private fun runtimeModelException(
        message: String,
        providerName: String = COMPONENT_NAME,
        modelKey: String = COMPONENT_NAME,
        override: ProviderOverride? = null,
        cause: Throwable? = null
    ): ModelRegistryException {
        return ModelRegistryException(
            message,
            providerName,
            modelKey,
            override?.toReportOverride() ?: emptyMap(),
            cause
        )
    }

    private fun ProviderOverride.toReportOverride(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        result["model"] = model
        temperature?.let { result["temperature"] = it.toString() }
        topP?.let { result["topP"] = it.toString() }
        maxTokens?.let { result["maxTokens"] = it.toString() }
        extras?.forEach { (key, value) -> result["extra.$key"] = value?.toString() ?: "null" }
        return result
    }
}

data class ModelRuntimeRegistryConfig(
    @field:ConfigDoc(
        description = "提供商配置集合", example = """ [ {
                    "name": "example_provider_name",
                    "type": "OPENAI_COMPATIBLE",
                    "defaultModel": "example_default_model",
                    "baseUrl": "example_base_url",
                    "apiKey": "example_apikey"
                }
            ]
        """
    )
    val providerConfigSet: Set<ProviderConfig>,
    @field:ConfigDoc(
        description = "模块所用提供商配置，可覆写 model、temperature、top_p、max_tokens 等配置",
        example = """
            [
                {
                    "modelKey": "example_model_key", // 模块通过该 key 定位到对应的提供商配置
                    "providerName": "example_provider_name", // 该配置对应的提供商名称
                    "override": { // 该配置需要重写的内容, 如果无需重写，可忽略该字段，该字段的各个子字段均为可选覆写
                        "model": "example_override_model",
                        "temperature": "example_override_temperature",
                        "topP": "example_override_top_p",
                        "maxTokens": "example_override_max_tokens",
                        "extra": { // 要覆写的额外内容
                            "example1": "value1"
                        }
                    }
                }
            ]
        """
    )
    val runtimeConfigSet: Set<RuntimeProviderConfig>
) : Config()

data class RuntimeProviderConfig(
    val modelKey: String,
    val providerName: String,

    val override: ProviderOverride?
)


sealed class ProviderConfig {
    abstract val name: String
    abstract val type: ProviderType
    abstract val defaultModel: String

    enum class ProviderType {
        OPENAI_COMPATIBLE
    }
}

data class OpenAiCompatibleProviderConfig(
    override val name: String,
    override val type: ProviderType = OPENAI_COMPATIBLE,
    override val defaultModel: String,

    val baseUrl: String,
    val apiKey: String
) : ProviderConfig()
