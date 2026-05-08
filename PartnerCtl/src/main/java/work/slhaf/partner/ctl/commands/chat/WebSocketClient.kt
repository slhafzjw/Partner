package work.slhaf.partner.ctl.commands.chat

import kotlinx.serialization.json.*
import work.slhaf.partner.api.*
import work.slhaf.partner.api.InteractionEvent.EventStatus
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletionStage

class WebSocketClient(
    val url: String,
    val onResponse: (event: InteractionEvent) -> Unit
) : AutoCloseable {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val listener = Listener(::handleMessage)

    private val webSocket = httpClient.newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .buildAsync(URI.create(url), listener)

    fun send(inputData: InputData) {
        val socket = webSocket.join()
        socket.sendText(inputData.toJson(), true).join()
    }

    override fun close() {
        if (!webSocket.isDone) {
            webSocket.cancel(true)
            return
        }

        runCatching {
            webSocket.join().sendClose(WebSocket.NORMAL_CLOSURE, "bye").join()
        }
    }

    private fun handleMessage(text: String) {
        val event = parseInteractionEvent(text) ?: return
        onResponse(event)
    }

    private fun parseInteractionEvent(text: String): InteractionEvent? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val status = obj.string("status")?.let { runCatching { EventStatus.valueOf(it) }.getOrNull() } ?: return null
        val target = obj.string("target") ?: return null

        return when (obj.string("event")) {
            "REPLY" -> ReplyEvent(
                status = status,
                target = target,
                content = obj.string("content") ?: "",
                mode = obj.string("mode")
                    ?.let { runCatching { ReplyEvent.ContentMode.valueOf(it) }.getOrNull() }
                    ?: ReplyEvent.ContentMode.REPLACE,
                seq = obj.long("seq")
            ).withMetaFrom(obj)

            "SYSTEM" -> SystemEvent(
                status = status,
                target = target,
                title = obj.string("title") ?: "",
                content = obj.string("content") ?: "",
                urgency = obj.string("urgency")
                    ?.let { runCatching { SystemEvent.Urgency.valueOf(it) }.getOrNull() }
                    ?: SystemEvent.Urgency.NORMAL
            ).withMetaFrom(obj)

            "MODULE" -> ModuleEvent(
                status = status,
                target = target,
                data = obj["data"]?.jsonObject?.let { data ->
                    ModuleEvent.Data(
                        module = data.string("module") ?: "",
                        content = data.string("content") ?: ""
                    )
                } ?: ModuleEvent.Data(module = "", content = "")
            ).withMetaFrom(obj)

            else -> null
        }
    }

    private fun InputData.toJson(): String = buildJsonObject(
        "source" to JsonPrimitive(source),
        "content" to JsonPrimitive(content),
        "meta" to JsonObject(meta.mapValues { JsonPrimitive(it.value) })
    ).toString()

    private fun <T : InteractionEvent> T.withMetaFrom(obj: JsonObject): T {
        obj["meta"]?.jsonObject?.forEach { (key, value) ->
            value.jsonPrimitive.contentOrNull?.let { addMeta(key, it) }
        }
        return this
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private fun buildJsonObject(vararg values: Pair<String, JsonElement>): JsonObject = JsonObject(values.toMap())

    private class Listener(
        private val onMessage: (String) -> Unit
    ) : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val text = buffer.toString()
                buffer.setLength(0)
                onMessage(text)
            }
            webSocket.request(1)
            return null
        }

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }
    }
}
