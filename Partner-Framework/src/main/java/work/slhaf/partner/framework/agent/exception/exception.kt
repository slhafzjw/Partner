package work.slhaf.partner.framework.agent.exception

import com.alibaba.fastjson2.JSONObject
import org.slf4j.LoggerFactory

abstract class AgentException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    open fun toReport(): ExceptionReport {
        return ExceptionReport(
            this::class.java.simpleName,
            message ?: "",
            this
        )
    }
}

data class ExceptionReport @JvmOverloads constructor(
    val type: String,
    val message: String,
    val exception: Throwable? = null,
    val extra: MutableMap<String, Any?> = linkedMapOf()
) {

    override fun toString(): String {
        val cause = exception?.cause
        val causeType = cause?.javaClass?.simpleName ?: ""
        val causeMessage = cause?.message ?: ""
        return """type: $type,
            |message: $message,
            |causeType: $causeType,
            |cause: $causeMessage,
            |extra: ${JSONObject.toJSONString(extra)}
        """.trimMargin()
    }

    fun toDetailedString(): String {
        return buildString {
            appendLine(this@ExceptionReport.toString())
            val stackTrace = exception?.stackTraceToString()
            if (!stackTrace.isNullOrBlank()) {
                appendLine("stackTrace:")
                appendLine(stackTrace)
            }
        }
    }
}

object ExceptionReporterHandler {

    private val registry = mutableMapOf<String, ExceptionReporter>()

    private val log = LoggerFactory.getLogger(this::class.java)

    fun register(reporter: ExceptionReporter) {
        val previous = registry.putIfAbsent(reporter.reporterName(), reporter)
        checkAgentStartup(previous == null || previous === reporter) {
            AgentStartupException(
                "Exception reporter already registered: ${reporter.reporterName()}",
                "exception-reporter-handler"
            )
        }
    }

    fun report(exception: AgentException, vararg reporters: String) {
        LoggerExceptionReporter.report(exception)

        for (reporterName in reporters) {
            val reporter = registry[reporterName]
            if (reporter != null) {
                reporter.report(exception)
            } else {
                log.warn("Exception reporter $reporterName not registered")
            }
        }
    }

}

interface ExceptionReporter {

    fun reporterName(): String

    fun report(exception: AgentException)

    fun register() {
        ExceptionReporterHandler.register(this)
    }

}

object LoggerExceptionReporter : ExceptionReporter {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun reporterName(): String = "logger-reporter"

    override fun report(exception: AgentException) {
        val exceptionReport = exception.toReport().toDetailedString()
        log.error("exception occurred: $exceptionReport")
    }

}

inline fun checkAgentStartup(
    condition: Boolean,
    exception: () -> AgentStartupException
) {
    if (!condition) throw exception()
}
