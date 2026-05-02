package work.slhaf.partner.module.communication

import kotlinx.coroutines.*
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException
import work.slhaf.partner.framework.agent.exception.ExceptionReporterHandler
import work.slhaf.partner.framework.agent.interaction.AgentRuntime
import work.slhaf.partner.framework.agent.interaction.data.InteractionEvent.EventStatus
import work.slhaf.partner.framework.agent.interaction.data.ReplyEvent
import work.slhaf.partner.framework.agent.model.StreamChatMessageConsumer
import kotlin.time.Duration.Companion.milliseconds

object ReplyDispatcher {

    fun createConsumer(target: String, responseChannel: String?): StreamChatMessageConsumer = ReplyConsumer(
        target = target,
        responseChannel = responseChannel
    )

}

private class ReplyConsumer(
    private val target: String,
    private val responseChannel: String?
) : StreamChatMessageConsumer {

    private enum class VisibilityState {
        UNDECIDED,
        NO_REPLY,
        VISIBLE
    }

    private val rawResponse = StringBuilder()
    private val undecidedBuffer = StringBuilder()
    private val visibleBuffer = StringBuilder()
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var flushJob: Job? = null
    private var visibilityState = VisibilityState.UNDECIDED
    private var terminalEmitted = false

    override fun onDelta(delta: String) {
        synchronized(lock) {
            rawResponse.append(delta)
            routeDelta(delta)
        }
    }

    override fun onComplete() {
        synchronized(lock) {
            finalizeUndecidedBuffer()
            flushVisibleBufferLocked()
            dispatchTerminalLocked(EventStatus.DONE)
            shutdownScopeLocked()
        }
    }

    override fun onError(exception: AgentRuntimeException) {
        synchronized(lock) {
            ExceptionReporterHandler.report(exception)
            rawResponse.append(INTERRUPTED_MARKER)
            routeDelta(INTERRUPTED_MARKER)
            finalizeUndecidedBuffer()
            flushVisibleBufferLocked()
            dispatchTerminalLocked(EventStatus.ERROR)
            shutdownScopeLocked()
        }
    }

    override fun collectResponse(): String {
        synchronized(lock) {
            finalizeUndecidedBuffer()
            flushVisibleBufferLocked()
            return rawResponse.toString()
        }
    }

    private fun routeDelta(delta: String) {
        when (visibilityState) {
            VisibilityState.NO_REPLY -> return
            VisibilityState.VISIBLE -> appendVisibleLocked(delta)
            VisibilityState.UNDECIDED -> {
                undecidedBuffer.append(delta)
                resolveVisibility()
            }
        }
    }

    private fun resolveVisibility() {
        val content = undecidedBuffer.toString()
        if (content.length <= NO_REPLY_MARKER.length) {
            if (NO_REPLY_MARKER.startsWith(content)) {
                return
            }
            revealBufferedContent()
            return
        }

        if (!content.startsWith(NO_REPLY_MARKER)) {
            revealBufferedContent()
            return
        }

        val suffixFirstChar = content[NO_REPLY_MARKER.length]
        if (suffixFirstChar == '\n' || suffixFirstChar == '\r') {
            visibilityState = VisibilityState.NO_REPLY
            undecidedBuffer.setLength(0)
            return
        }
        revealBufferedContent()
    }

    private fun finalizeUndecidedBuffer() {
        if (visibilityState != VisibilityState.UNDECIDED) {
            return
        }
        if (undecidedBuffer.toString() == NO_REPLY_MARKER) {
            visibilityState = VisibilityState.NO_REPLY
            undecidedBuffer.setLength(0)
            return
        }
        revealBufferedContent()
    }

    private fun revealBufferedContent() {
        visibilityState = VisibilityState.VISIBLE
        if (undecidedBuffer.isNotEmpty()) {
            appendVisibleLocked(undecidedBuffer.toString())
            undecidedBuffer.setLength(0)
        }
    }

    private fun appendVisibleLocked(delta: String) {
        if (delta.isEmpty()) {
            return
        }
        visibleBuffer.append(delta)
        scheduleFlushLocked()
    }

    private fun scheduleFlushLocked() {
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(AGGREGATE_WINDOW_MILLIS.milliseconds)
            synchronized(lock) {
                flushVisibleBufferLocked()
            }
        }
    }

    private fun flushVisibleBufferLocked() {
        flushJob?.cancel()
        flushJob = null
        if (visibleBuffer.isEmpty()) {
            return
        }
        val content = visibleBuffer.toString()
        visibleBuffer.setLength(0)
        dispatchLocked(
            ReplyEvent(
                status = EventStatus.RUNNING,
                target = target,
                content = content,
                mode = ReplyEvent.ContentMode.APPEND
            )
        )
    }

    private fun dispatchTerminalLocked(status: EventStatus) {
        if (terminalEmitted) {
            return
        }
        terminalEmitted = true
        dispatchLocked(
            ReplyEvent(
                status = status,
                target = target,
                content = "",
                mode = ReplyEvent.ContentMode.APPEND
            )
        )
    }

    private fun dispatchLocked(event: ReplyEvent) {
        if (responseChannel == null) {
            AgentRuntime.response(event)
        } else {
            AgentRuntime.response(event, responseChannel)
        }
    }

    private fun shutdownScopeLocked() {
        flushJob?.cancel()
        flushJob = null
        scope.cancel()
    }

    private companion object {
        private const val AGGREGATE_WINDOW_MILLIS = 100L
        private const val INTERRUPTED_MARKER = "\n[response interrupted due to internal exception]"
        private const val NO_REPLY_MARKER = "NO_REPLY"
    }
}
