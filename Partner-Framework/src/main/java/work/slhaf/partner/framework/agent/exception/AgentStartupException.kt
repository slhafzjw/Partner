package work.slhaf.partner.framework.agent.exception

open class AgentStartupException @JvmOverloads constructor(
    message: String,
    val relatedComponent: String,
    cause: Throwable? = null
) : AgentException(message, cause) {
    override fun toReport(): ExceptionReport {
        val report = super.toReport()
        report.extra["relatedComponent"] = relatedComponent
        return report
    }
}

open class FactoryExecutionException @JvmOverloads constructor(
    message: String,
    val factoryName: String,
    cause: Throwable? = null
) : AgentStartupException(message, "agent-register-factory", cause) {
    override fun toReport(): ExceptionReport {
        val report = super.toReport()
        report.extra["factoryName"] = factoryName
        return report
    }
}

open class GatewayStartupException @JvmOverloads constructor(
    message: String,
    val gatewayName: String,
    cause: Throwable? = null
) : AgentStartupException(message, "agent-gateway-registry", cause) {
    override fun toReport(): ExceptionReport {
        val report = super.toReport()
        report.extra["gatewayName"] = gatewayName
        return report
    }
}

open class ModelRegistryStartupException @JvmOverloads constructor(
    message: String,
    val providerName: String,
    val modelKey: String,
    val override: Map<String, String> = emptyMap(),
    cause: Throwable? = null
) : AgentStartupException(message, "model-runtime-registry", cause) {
    override fun toReport(): ExceptionReport {
        val report = super.toReport()
        report.extra["providerName"] = providerName
        report.extra["modelKey"] = modelKey
        report.extra["override"] = override
        return report
    }
}
