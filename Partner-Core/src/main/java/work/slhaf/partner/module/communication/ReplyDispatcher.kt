package work.slhaf.partner.module.communication

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import work.slhaf.partner.framework.agent.interaction.AgentRuntime
import work.slhaf.partner.framework.agent.interaction.data.InteractionEvent.EventStatus
import work.slhaf.partner.framework.agent.interaction.data.Reply
import work.slhaf.partner.framework.agent.model.StreamChatMessageConsumer
import kotlin.time.Duration.Companion.milliseconds

object ReplyDispatcher {

    private const val AGGREGATE_WINDOW_MILLIS = 100L
    private const val NO_REPLY_MARKER = "NO_REPLY"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val collectorChannel = Channel<ReplyChunk>(Channel.UNLIMITED)

    init {
        scope.launch {
            var pendingChunk: ReplyChunk? = null
            while (true) {
                val firstChunk = pendingChunk ?: collectorChannel.receiveCatching().getOrNull() ?: break
                pendingChunk = null
                val builder = StringBuilder(firstChunk.delta)

                while (true) {
                    val nextChunk = withTimeoutOrNull(AGGREGATE_WINDOW_MILLIS.milliseconds) {
                        collectorChannel.receiveCatching()
                    } ?: break

                    if (nextChunk.isClosed) {
                        flush(builder.toString(), firstChunk.target)
                        return@launch
                    }

                    val chunk = nextChunk.getOrNull() ?: break
                    if (chunk.target == firstChunk.target) {
                        builder.append(chunk.delta)
                    } else {
                        pendingChunk = chunk
                        break
                    }
                }

                flush(builder.toString(), firstChunk.target)
            }
        }
    }

    /**
     * flush 将推送至 AgentRuntime 的默认通道。
     */
    private fun flush(content: String, target: String) {
        if (content.isEmpty()) {
            return
        }
        val event = Reply(
            status = EventStatus.RUNNING,
            target = target,
            content = content,
            mode = Reply.ContentMode.APPEND,
            done = false
        )
        AgentRuntime.response(event)
    }

    fun createConsumer(target: String): StreamChatMessageConsumer = ReplyConsumer(
        collectorChannel = collectorChannel,
        target = target,
    )

    private data class ReplyChunk(
        val delta: String,
        val target: String,
    )

    private class ReplyConsumer(
        private val collectorChannel: Channel<ReplyChunk>,
        private val target: String,
    ) : StreamChatMessageConsumer() {

        private enum class VisibilityState {
            UNDECIDED,
            NO_REPLY,
            VISIBLE
        }

        private val rawResponse = StringBuilder()
        private val undecidedBuffer = StringBuilder()
        private var visibilityState = VisibilityState.UNDECIDED

        override fun onDelta(delta: String) {
            rawResponse.append(delta)
            routeDelta(delta)
        }

        override fun collectResponse(): String {
            finalizeUndecidedBuffer()
            return rawResponse.toString()
        }

        private fun routeDelta(delta: String) {
            when (visibilityState) {
                VisibilityState.NO_REPLY -> return
                VisibilityState.VISIBLE -> flushVisible(delta)
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
                flushVisible(undecidedBuffer.toString())
                undecidedBuffer.setLength(0)
            }
        }

        private fun flushVisible(delta: String) {
            collectorChannel.trySend(ReplyChunk(delta, target)).isSuccess
        }

        override fun consumeDelta(delta: String?) {
        }

    }
}
