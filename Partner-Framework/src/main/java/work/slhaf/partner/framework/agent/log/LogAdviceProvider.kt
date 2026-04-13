package work.slhaf.partner.framework.agent.log

import com.alibaba.fastjson2.JSONException
import com.alibaba.fastjson2.JSONObject
import org.slf4j.LoggerFactory
import work.slhaf.partner.framework.agent.config.Config
import work.slhaf.partner.framework.agent.config.ConfigCenter
import work.slhaf.partner.framework.agent.config.ConfigRegistration
import work.slhaf.partner.framework.agent.config.Configurable
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime

object LogAdviceProvider : Configurable, ConfigRegistration<AdviceLoggingConfig> {

    private val logPath = ConfigCenter.paths.stateDir.resolve("trace").normalize().toAbsolutePath()
    private val _adviceRegistry = mutableSetOf<LogAdvice<*, *>>()
    val adviceRegistry: Set<LogAdvice<*, *>>
        get() = _adviceRegistry

    var logLevel = AdviceLoggingConfig.LogLevel.NONE

    init {
        Files.createDirectories(logPath)
    }

    @JvmOverloads
    fun <I, O> createAdvice(
        adviceTarget: String,
        inputType: Class<I>,
        outputType: Class<O>,
        meta: Map<String, Any> = emptyMap(),
        invoker: (I) -> O
    ): LogAdvice<I, O> {
        return LogAdvice(
            adviceTarget = adviceTarget,
            invoker = invoker,
            AdviceMeta(adviceTarget, inputType, outputType, meta)
        ).apply { _adviceRegistry.add(this) }
    }

    internal fun record(result: AdviceResult) {
        val path = logPath.resolve(result.adviceTarget).normalize().toAbsolutePath()
        val traceEvent = TraceEvent(path, result.toJSON(), result.finishTime.toInstant().toEpochMilli())
        TraceRecorder.record(traceEvent)
    }

    override fun declare(): Map<Path, ConfigRegistration<out Config>> = mapOf(Path.of("advice_logging.json") to this)

    override fun type(): Class<AdviceLoggingConfig> = AdviceLoggingConfig::class.java

    override fun init(
        config: AdviceLoggingConfig,
        json: JSONObject?
    ) {
        logLevel = config.logLevel
    }

    override fun defaultConfig(): AdviceLoggingConfig = AdviceLoggingConfig(AdviceLoggingConfig.LogLevel.NONE)
}

class LogAdvice<I, O> internal constructor(
    val adviceTarget: String,
    private val invoker: (I) -> O,
    private val adviceMeta: AdviceMeta
) {

    companion object {
        private val log = LoggerFactory.getLogger(LogAdvice::class.java)
    }

    fun invoke(input: I): Result<O> {
        val startAt = ZonedDateTime.now()
        return try {
            logEnter(input)
            val output = invoker(input)
            logOutput(output)
            createResult(input, output, startAt)
            Result.success(output)
        } catch (e: Exception) {
            logException(e)
            createUnexpectedResult(input, e, startAt)
            throw e
        }
    }

    private fun logException(e: Exception) {
        when (LogAdviceProvider.logLevel) {
            AdviceLoggingConfig.LogLevel.NONE -> return
            AdviceLoggingConfig.LogLevel.ABSTRACT -> log.error("${adviceMeta.adviceTarget} occurred exception: $e..")
            AdviceLoggingConfig.LogLevel.DETAIL -> log.error("${adviceMeta.adviceTarget} occurred exception: ", e)
        }
    }

    private fun logOutput(output: O) {
        when (LogAdviceProvider.logLevel) {
            AdviceLoggingConfig.LogLevel.NONE -> return
            AdviceLoggingConfig.LogLevel.ABSTRACT -> log.info("${adviceMeta.adviceTarget} ended.")
            AdviceLoggingConfig.LogLevel.DETAIL -> {
                try {
                    log.info("${adviceMeta.adviceTarget} ended with output: ${JSONObject.toJSONString(output)}")
                } catch (_: Exception) {
                    log.info("${adviceMeta.adviceTarget} ended with output: ${output.toString()}, which cannot be printed as json string.")
                }
            }
        }
    }

    private fun logEnter(input: I) {
        when (LogAdviceProvider.logLevel) {
            AdviceLoggingConfig.LogLevel.NONE -> return
            AdviceLoggingConfig.LogLevel.ABSTRACT -> log.info("${adviceMeta.adviceTarget} entered.")
            AdviceLoggingConfig.LogLevel.DETAIL -> {
                try {
                    log.info("${adviceMeta.adviceTarget} entered with input : ${JSONObject.toJSONString(input)}")
                } catch (_: Exception) {
                    log.info("${adviceMeta.adviceTarget} entered with input : ${input.toString()}, which cannot be printed as json string.")
                }
            }
        }
    }

    private fun createResult(input: I, output: O, startAt: ZonedDateTime) {
        val inputSerialized = try {
            JSONObject.toJSONString(input)
        } catch (_: JSONException) {
            input.toString()
        }
        val outputSerialized = try {
            JSONObject.toJSONString(output)
        } catch (_: JSONException) {
            output.toString()
        }
        LogAdviceProvider.record(
            AdviceResult.Normal(
                adviceTarget,
                inputSerialized,
                startAt,
                adviceMeta,
                outputSerialized
            )
        )
    }

    private fun createUnexpectedResult(input: I, throwable: Throwable, startAt: ZonedDateTime) { /* 落盘 */
        val inputSerialized = try {
            JSONObject.toJSONString(input)
        } catch (_: JSONException) {
            input.toString()
        }
        LogAdviceProvider.record(
            AdviceResult.Unexpected(
                adviceTarget,
                inputSerialized,
                startAt,
                adviceMeta,
                throwable.localizedMessage ?: "",
                throwable.stackTraceToString()
            )
        )
    }
}

data class AdviceMeta(
    val adviceTarget: String,
    val inputType: Class<*>,
    val outputType: Class<*>,
    val meta: Map<String, Any>
)

sealed class AdviceResult {

    abstract val adviceTarget: String
    abstract val input: String
    abstract val type: Type
    abstract val startAt: ZonedDateTime
    abstract val adviceMeta: AdviceMeta

    val finishTime: ZonedDateTime = ZonedDateTime.now()
    val elapsed: Long
        get() = finishTime.toInstant().toEpochMilli() - startAt.toInstant().toEpochMilli()

    enum class Type {
        NORMAL,
        UNEXPECTED
    }


    data class Normal(
        override val adviceTarget: String,
        override val input: String,
        override val startAt: ZonedDateTime,
        override val adviceMeta: AdviceMeta,
        val output: String,
    ) : AdviceResult() {
        override val type: Type = Type.NORMAL
    }

    data class Unexpected(
        override val adviceTarget: String,
        override val input: String,
        override val startAt: ZonedDateTime,
        override val adviceMeta: AdviceMeta,
        val message: String,
        val stackTrace: String
    ) : AdviceResult() {
        override val type: Type = Type.UNEXPECTED
    }

    fun toJSON(): JSONObject = JSONObject.from(this)

    override fun toString(): String = toJSON().toJSONString()

}

data class AdviceLoggingConfig(
    val logLevel: LogLevel
) : Config() {
    enum class LogLevel {
        NONE,
        ABSTRACT,
        DETAIL
    }
}
