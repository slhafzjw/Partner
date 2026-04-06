package work.slhaf.partner.framework.agent.interaction.data

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

sealed class InteractionEvent {

    /**
     * event type
     */
    abstract val event: Event

    /**
     * event sending status
     */
    abstract val status: EventStatus

    /**
     * the target send to
     */
    abstract val target: String

    private val _meta = mutableMapOf<String, String>(
        "datetime" to ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    )
    val meta: Map<String, String>
        get() = _meta

    fun addMeta(key: String, value: String) {
        _meta[key] = value
    }

    enum class Event {
        REPLY,
        MODULE,
        SYSTEM
    }

    enum class EventStatus {
        RUNNING,
        ERROR,
        DONE
    }

}

data class Reply @JvmOverloads constructor(
    override val status: EventStatus,
    override val target: String,
    val content: String,
    val mode: ContentMode = ContentMode.REPLACE,
    val seq: Long? = null,
    val done: Boolean = false
) : InteractionEvent() {
    override val event = Event.REPLY

    enum class ContentMode {
        APPEND,
        REPLACE
    }
}

data class Module(
    override val status: EventStatus,
    override val target: String,
    val data: Data
) : InteractionEvent() {
    override val event = Event.MODULE

    data class Data(
        val module: String,
        val content: String
    )
}

data class System @JvmOverloads constructor(
    override val status: EventStatus,
    override val target: String,
    val title: String,
    val content: String,
    val urgency: Urgency = Urgency.NORMAL
) : InteractionEvent() {
    override val event = Event.SYSTEM

    enum class Urgency {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
}
