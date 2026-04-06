package work.slhaf.partner.framework.agent.state

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import work.slhaf.partner.framework.agent.config.ConfigCenter
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

object StateCenter {

    private val stateRegistry = ConcurrentHashMap<Path, StateSerializable>()

    fun register(stateSerializable: StateSerializable): JSONObject? {
        val relativePath = stateSerializable.statePath().normalize()
        check(!relativePath.isAbsolute) { "StatePath must be relative" }

        val stateDir = ConfigCenter.paths.stateDir.normalize()
        val finalStatePath = stateDir.resolve(relativePath).normalize()
        check(finalStatePath.startsWith(stateDir)) { "StatePath escapes stateDir" }

        val previous = stateRegistry.putIfAbsent(finalStatePath, stateSerializable)
        check(previous == null || previous === stateSerializable) {
            "StatePath already registered: $finalStatePath"
        }

        if (!finalStatePath.exists()) {
            return null
        }

        check(finalStatePath.isRegularFile()) { "StatePath must point to a regular file: $finalStatePath" }
        check(finalStatePath.toFile().canRead()) { "StateFile must be readable: $finalStatePath" }

        return JSONObject.parseObject(finalStatePath.readText())
    }

    fun save() {
        stateRegistry.forEach { (path, state) ->
            path.parent?.let(Files::createDirectories)
            path.writeText(state.convert().toString())
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

    fun load(state: JSONObject)

    fun convert(): State
}

class State {

    private val json = JSONObject()

    fun append(key: String, value: StateValue) = json.put(key, value.toJsonValue())

    fun toJson(): JSONObject = json

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