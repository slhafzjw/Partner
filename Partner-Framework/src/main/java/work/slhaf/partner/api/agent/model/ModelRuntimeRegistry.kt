package work.slhaf.partner.api.agent.model

import work.slhaf.partner.api.agent.model.provider.ModelProvider
import work.slhaf.partner.api.agent.model.provider.ProviderOverride
import work.slhaf.partner.api.agent.model.provider.openai.OpenAiCompatibleProvider

object ModelRuntimeRegistry {

    private const val DEFAULT_PROVIDER = "default"

    /**
     * 基础的 provider 提供商，可 fork 出新的 runtime provider，必须提供一个 default provider
     */
    private val baseProvider = mutableMapOf<String, ModelProvider>()

    /**
     * 根据模块进行对应的 provider
     */
    private val runtimeProvider = mutableMapOf<String, ModelProvider>()

    fun resolveProvider(modelKey: String): ModelProvider {
        val provider = runtimeProvider[modelKey]
        if (provider != null) {
            return provider
        }
        return baseProvider[DEFAULT_PROVIDER]!!
    }

    private fun registerProvider(config: ProviderConfig) {
        when (config) {
            is OpenAiCompatibleProviderConfig -> baseProvider[config.name] =
                OpenAiCompatibleProvider(config.baseUrl, config.apiKey, config.defaultModel)
        }
    }

    private fun forkProvider(config: RuntimeProviderConfig) {
        val provider = baseProvider[config.providerName]
            ?: throw IllegalArgumentException("Provider ${config.providerName} not found")
        val override = config.override

        runtimeProvider[config.modelKey] = if (override != null) {
            provider.fork(override)
        } else {
            provider
        }
    }

}

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
    override val type: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    override val defaultModel: String,

    val baseUrl: String,
    val apiKey: String
) : ProviderConfig()

