package work.slhaf.partner.framework.agent.exception

import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule

open class AgentRuntimeException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : AgentException(message, cause)

open class ModuleExecutionException @JvmOverloads constructor(
    message: String,
    val moduleType: Class<out AbstractAgentModule>,
    val moduleName: String,
    cause: Throwable? = null
) : AgentRuntimeException(message, cause) {
    override fun toReport(): ExceptionReport = super.toReport().also {
        it.extra["moduleType"] = moduleType
        it.extra["moduleName"] = moduleName
    }
}

open class InteractionException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : AgentRuntimeException(message, cause)

open class GatewayException @JvmOverloads constructor(
    message: String,
    val gatewayName: String,
    cause: Throwable? = null
) : InteractionException(message, cause) {
    override fun toReport(): ExceptionReport = super.toReport().also {
        it.extra["gatewayName"] = gatewayName
    }
}

open class GatewayRegistryException @JvmOverloads constructor(
    message: String,
    val gatewayName: String,
    cause: Throwable? = null
) : InteractionException(message, cause) {
    override fun toReport(): ExceptionReport = super.toReport().also {
        it.extra["gatewayName"] = gatewayName
    }
}

open class ModelInvokeException(
    message: String,
    val providerName: String,
    val modelKey: String,
    val baseUrl: String,
    val model: String,
    val override: Map<String, String> = emptyMap(),
    cause: Throwable? = null
) : AgentRuntimeException(message, cause) {
    override fun toReport(): ExceptionReport = super.toReport().also {
        it.extra["providerName"] = providerName
        it.extra["modelKey"] = modelKey
        it.extra["baseUrl"] = baseUrl
        it.extra["model"] = model
        it.extra["override"] = override
    }
}

open class ModelRegistryException(
    message: String,
    val providerName: String,
    val modelKey: String,
    val override: Map<String, String> = emptyMap(),
    cause: Throwable? = null
) : AgentRuntimeException(message, cause) {
    override fun toReport(): ExceptionReport = super.toReport().also {
        it.extra["providerName"] = providerName
        it.extra["modelKey"] = modelKey
        it.extra["override"] = override
    }
}
