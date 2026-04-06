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

    // TODO 通过配置中心动态指定响应通道
    private var channelName: String? = null

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
                        flush(builder.toString(), firstChunk.target, firstChunk.channelName)
                        return@launch
                    }

                    val chunk = nextChunk.getOrNull() ?: break
                    if (chunk.target == firstChunk.target && chunk.channelName == firstChunk.channelName) {
                        builder.append(chunk.delta)
                    } else {
                        pendingChunk = chunk
                        break
                    }
                }

                flush(builder.toString(), firstChunk.target, firstChunk.channelName)
            }
        }
    }

    private fun flush(content: String, target: String, channelName: String?) {
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
        if (channelName.isNullOrBlank()) {
            AgentRuntime.response(event)
        } else {
            AgentRuntime.response(event, channelName)
        }
    }

    fun createConsumer(target: String): StreamChatMessageConsumer = ReplyConsumer(
        collectorChannel = collectorChannel,
        target = target,
        channelName = channelName
    )

    private data class ReplyChunk(
        val delta: String,
        val target: String,
        val channelName: String?
    )

    private class ReplyConsumer(
        private val collectorChannel: Channel<ReplyChunk>,
        private val target: String,
        private val channelName: String?
    ) : StreamChatMessageConsumer() {

        override fun consumeDelta(delta: String?) {
            if (delta != null) {
                collectorChannel.trySend(ReplyChunk(delta, target, channelName)).isSuccess
            }
        }

    }
}
