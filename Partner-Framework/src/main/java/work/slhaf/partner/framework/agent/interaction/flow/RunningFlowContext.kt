package work.slhaf.partner.framework.agent.interaction.flow

import com.alibaba.fastjson2.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
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
    val firstInputEpochMillis: Long,
    additionalUserInfo: Map<String, String> = emptyMap(),
    skippedModules: Set<String> = emptySet(),
    target: String = ""
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
    var target: String = target

    private val _additionalUserInfo = additionalUserInfo.toMutableMap()
    val additionalUserInfo: Map<String, String>
        get() = _additionalUserInfo

    private val _skippedModules = skippedModules.toMutableSet()
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

    @JvmOverloads
    fun appendInputsXml(
        document: Document,
        parent: Element,
        containerTagName: String = "inputs",
        inputTagName: String = "input",
        intervalAttributeName: String = "interval-to-first"
    ) {
        val inputsElement = document.createElement(containerTagName)
        parent.appendChild(inputsElement)
        inputs.forEach { entry ->
            val inputElement = document.createElement(inputTagName)
            inputElement.setAttribute(intervalAttributeName, entry.offsetMillis.toString())
            inputElement.textContent = entry.content
            inputsElement.appendChild(inputElement)
        }
    }

    fun encodeInputsXml(): String {
        val builder = StringBuilder()
        builder.append("<inputs>")
        inputs.forEach { entry ->
            builder.append("<input interval-to-first=\"")
                .append(escapeXml(entry.offsetMillis.toString()))
                .append("\">")
                .append(escapeXml(entry.content))
                .append("</input>")
        }
        builder.append("</inputs>")
        return builder.toString()
    }

    fun mergedWith(other: RunningFlowContext): RunningFlowContext {
        require(source == other.source) {
            "Unable to merge RunningFlowContext from different source: $source != ${other.source}"
        }
        val mergedFirstEpochMillis = min(firstInputEpochMillis, other.firstInputEpochMillis)
        val mergedInputs = buildList(inputs.size + other.inputs.size) {
            addAll(normalizeInputs(this@RunningFlowContext, mergedFirstEpochMillis))
            addAll(normalizeInputs(other, mergedFirstEpochMillis))
        }.sortedBy { it.offsetMillis }

        val mergedAdditionalUserInfo = LinkedHashMap<String, String>(_additionalUserInfo)
        mergedAdditionalUserInfo.putAll(other.additionalUserInfo)

        val mergedSkippedModules = LinkedHashSet<String>(_skippedModules)
        mergedSkippedModules.addAll(other.skippedModules)

        return copyWith(
            inputs = mergedInputs,
            firstInputEpochMillis = mergedFirstEpochMillis,
            additionalUserInfo = mergedAdditionalUserInfo,
            skippedModules = mergedSkippedModules,
            target = other.target.ifBlank { target }
        )
    }

    protected abstract fun copyWith(
        inputs: List<InputEntry>,
        firstInputEpochMillis: Long,
        additionalUserInfo: Map<String, String>,
        skippedModules: Set<String>,
        target: String
    ): RunningFlowContext

    private fun normalizeInputs(context: RunningFlowContext, firstEpochMillis: Long): List<InputEntry> {
        return context.inputs.map { entry ->
            InputEntry(
                offsetMillis = context.firstInputEpochMillis + entry.offsetMillis - firstEpochMillis,
                content = entry.content
            )
        }
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    data class InputEntry(
        val offsetMillis: Long,
        val content: String
    )

    class Info {
        val uuid = UUID.randomUUID().toString()
        val dateTime: LocalDateTime = LocalDateTime.now()
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
