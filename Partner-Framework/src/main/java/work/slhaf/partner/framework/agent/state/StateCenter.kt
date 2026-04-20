package work.slhaf.partner.framework.agent.state

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import org.slf4j.LoggerFactory
import work.slhaf.partner.framework.agent.config.ConfigCenter
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

object StateCenter {

    private val log = LoggerFactory.getLogger(StateCenter::class.java)

    private val stateRegistry = ConcurrentHashMap<Path, StateRecord>()

    fun register(stateSerializable: StateSerializable): JSONObject? {
        val relativePath = stateSerializable.statePath().normalize()
        check(!relativePath.isAbsolute) { "StatePath must be relative" }

        val stateDir = ConfigCenter.paths.stateDir.normalize()
        val finalStatePath = stateDir.resolve(relativePath).normalize()
        check(finalStatePath.startsWith(stateDir)) { "StatePath escapes stateDir" }

        val stateRecord = StateRecord(stateSerializable)
        val previous = stateRegistry.putIfAbsent(finalStatePath, stateRecord)
        check(previous == null || previous.serializable === stateSerializable) {
            "StatePath already registered: $finalStatePath"
        }

        if (!finalStatePath.exists()) {
            stateRecord.saveEnabled = true
            return null
        }

        check(finalStatePath.isRegularFile()) { "StatePath must point to a regular file: $finalStatePath" }
        check(finalStatePath.toFile().canRead()) { "StateFile must be readable: $finalStatePath" }

        if (!stateSerializable.autoLoadOnRegister()) {
            return null
        }

        stateRecord.saveEnabled = true

        return JSONObject.parseObject(finalStatePath.readText())
    }

    fun load(path: Path) {
        val finalStatePath = ConfigCenter.paths.stateDir.normalize().resolve(path).normalize()
        if (!stateRegistry.containsKey(finalStatePath)) {
            return
        }
        val record = stateRegistry[finalStatePath] ?: return
        record.saveEnabled = true
        if (!finalStatePath.exists()) {
            return
        }
        try {
            val json = JSONObject.parseObject(finalStatePath.readText())
            record.serializable.load(json)
        } catch (_: Exception) {
            log.warn("StateCenter loading failed: $path")
        }
    }

    fun save() {
        stateRegistry.forEach { (path, record) ->
            if (!record.saveEnabled) {
                return@forEach
            }
            path.parent?.let(Files::createDirectories)
            path.writeText(record.serializable.convert().toString())
        }
    }

}

interface StateSerializable {

    fun register() {
        val existingState = StateCenter.register(this)
        if (existingState != null) {
            load(existingState)
        }
    }

    fun statePath(): Path

    /**
     * 手动加载状态数据
     */
    fun load() {
        StateCenter.load(statePath())
    }

    /**
     * 状态加载逻辑
     */
    fun load(state: JSONObject)

    /**
     * 数据转换为状态逻辑
     */
    fun convert(): State

    /**
     * 是否在注册时即触发一次加载
     */
    fun autoLoadOnRegister(): Boolean = true
}

class State {

    private val json = JSONObject()

    fun append(key: String, value: StateValue) = json.put(key, value.toJsonValue())

    override fun toString(): String = json.toString()

    private fun StateValue.toJsonValue(): Any = when (this) {
        is StateValue.Bool -> value
        is StateValue.Num -> value
        is StateValue.Str -> value
        is StateValue.Arr -> JSONArray().apply { value.forEach { add(it.toJsonValue()) } }
        is StateValue.Obj -> JSONObject().apply { value.forEach { (k, v) -> this[k] = v.toJsonValue() } }
    }
}

sealed interface StateValue {
    data class Num(val value: Number) : StateValue
    data class Bool(val value: Boolean) : StateValue
    data class Str(val value: String) : StateValue
    data class Arr(val value: List<StateValue>) : StateValue
    data class Obj(val value: Map<String, StateValue>) : StateValue

    companion object {
        @JvmStatic
        fun num(value: Number) = Num(value)

        @JvmStatic
        fun bool(value: Boolean) = Bool(value)

        @JvmStatic
        fun str(value: String) = Str(value)

        @JvmStatic
        fun arr(value: List<StateValue>) = Arr(value)

        @JvmStatic
        fun obj(value: Map<String, StateValue>) = Obj(value)
    }
}

data class StateRecord(
    val serializable: StateSerializable,
    var saveEnabled: Boolean = false
)
