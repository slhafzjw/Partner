package work.slhaf.partner.framework.agent.interaction.flow

import com.alibaba.fastjson2.JSONObject
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

/**
 * 流程上下文
 */
abstract class RunningFlowContext {
    /**
     * 消息来源: 由谁发出
     */
    abstract val source: String

    /**
     * 消息内容
     */
    abstract val input: String

    /**
     * 消息回应对象，默认与 source 一致
     */
    var target = source

    private val _additionalUserInfo = mutableMapOf<String, String>()
    val additionalUserInfo: Map<String, String>
        get() = _additionalUserInfo

    private val _skippedModules = mutableSetOf<String>()
    val skippedModules: Set<String>
        get() = _skippedModules

    val status = Status()

    fun addSkippedModule(moduleName: String) {
        _skippedModules.add(moduleName)
    }

    fun putUserInfo(key: String, value: String) {
        _additionalUserInfo[key] = value
    }

    fun putUserInfo(key: String, value: Any) {
        _additionalUserInfo[key] = try {
            JSONObject.toJSONString(value)
        } catch (e: Exception) {
            value.toString()
        }
    }

    class Info {
        val uuid = UUID.randomUUID().toString()
        val dateTime: LocalDateTime = ZonedDateTime.now().toLocalDateTime()
    }

    class Status {
        /**
         * 本次 runningFlow 是否正常执行
         */
        val ok: Boolean
            get() = errors.isEmpty()

        /**
         * 本次执行时收集到的异常信息
         */
        var errors = mutableListOf<String>()
    }
}
