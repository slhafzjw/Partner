package work.slhaf.partner.framework.agent.interaction.flow

import com.alibaba.fastjson2.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.min

/**
 * 流程上下文
 */
abstract class RunningFlowContext protected constructor(
    inputs: List<InputEntry>,
    private var firstInputEpochMillis: Long
) {
    /**
     * 消息来源: 由谁发出
     */
    abstract val source: String

    /**
     * 输入序列
     */
    val inputs: List<InputEntry> = inputs.sortedBy { it.offsetMillis }

    /**
     * 兼容旧路径的纯文本输入表示，按时间顺序换行拼接
     */
    val input: String
        get() = formatInputsForHistory()

    /**
     * 消息回应对象，默认与 source 一致
     */
    private var _target: String? = null
    var target: String
        get() = _target ?: source
        set(value) {
            _target = value
        }

    private val _additionalUserInfo = mutableMapOf<String, String>()
    val additionalUserInfo: Map<String, String>
        get() = _additionalUserInfo

    private val _skippedModules = mutableSetOf<String>()
    val skippedModules: Set<String>
        get() = _skippedModules

    val status = Status()

    val firstInputDateTime: LocalDateTime
        get() = Instant.ofEpochMilli(firstInputEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

    fun addSkippedModule(moduleName: String) {
        _skippedModules.add(moduleName)
    }

    fun putUserInfo(key: String, value: String) {
        _additionalUserInfo[key] = value
    }

    fun putUserInfo(key: String, value: Any) {
        _additionalUserInfo[key] = try {
            JSONObject.toJSONString(value)
        } catch (_: Exception) {
            value.toString()
        }
    }

    fun formatInputsForHistory(): String = inputs.joinToString("\n") { it.content }


    fun mergedWith(other: RunningFlowContext): RunningFlowContext {
        require(source == other.source) {
            "Unable to merge RunningFlowContext from different source: $source != ${other.source}"
        }
        val mergedFirstEpochMillis = min(firstInputEpochMillis, other.firstInputEpochMillis)
        val mergedInputs = buildList(inputs.size + other.inputs.size) {
            addAll(normalizeInputs(this@RunningFlowContext, mergedFirstEpochMillis))
            addAll(normalizeInputs(other, mergedFirstEpochMillis))
        }.sortedBy { it.offsetMillis }

        val mergedContext = recreate(mergedInputs)
        mergedContext.firstInputEpochMillis = mergedFirstEpochMillis
        mergedContext.target = other.target.ifBlank { target }
        mergedContext._additionalUserInfo.putAll(_additionalUserInfo)
        mergedContext._additionalUserInfo.putAll(other.additionalUserInfo)
        mergedContext._skippedModules.addAll(_skippedModules)
        mergedContext._skippedModules.addAll(other.skippedModules)
        return mergedContext
    }

    protected abstract fun recreate(inputs: List<InputEntry>): RunningFlowContext

    private fun normalizeInputs(context: RunningFlowContext, firstEpochMillis: Long): List<InputEntry> {
        return context.inputs.map { entry ->
            InputEntry(
                offsetMillis = context.firstInputEpochMillis + entry.offsetMillis - firstEpochMillis,
                content = entry.content
            )
        }
    }

    data class InputEntry(
        val offsetMillis: Long,
        val content: String
    )

    class Info {
        val uuid = UUID.randomUUID().toString()
    }

    class Status {
        /**
         * 本次 runningFlow 是否正常执行
         */
        val ok: Boolean
            get() = errors.isEmpty()

        /**
         * 模块边界上的协作式打断标记
         */
        @Volatile
        var interrupted: Boolean = false

        /**
         * 本次执行时收集到的异常信息
         */
        var errors = mutableListOf<String>()
    }
}
