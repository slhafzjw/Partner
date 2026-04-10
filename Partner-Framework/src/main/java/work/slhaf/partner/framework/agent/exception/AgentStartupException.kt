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
