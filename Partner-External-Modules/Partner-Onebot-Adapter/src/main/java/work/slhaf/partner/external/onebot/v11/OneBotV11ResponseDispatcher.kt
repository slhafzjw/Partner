package work.slhaf.partner.external.onebot.v11

import work.slhaf.partner.framework.agent.interaction.data.InteractionEvent
import work.slhaf.partner.framework.agent.interaction.data.ModuleEvent
import work.slhaf.partner.framework.agent.interaction.data.ReplyEvent
import work.slhaf.partner.framework.agent.interaction.data.SystemEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Converts Partner streaming interaction events into readable OneBot chat bubbles.
 *
 * OneBot/QQ messages are discrete chat messages, not frontend delta patches. This dispatcher buffers
 * ReplyEvent.APPEND chunks and sends complete natural blocks whenever possible.
 */
class OneBotV11ResponseDispatcher(
    private val actionExecutor: OneBotV11ActionExecutor = OneBotV11ActionExecutor,
    private val maxMessageChars: Int = 1200,
    private val softFlushChars: Int = 1000,
) : AutoCloseable {

    private val buffers = ConcurrentHashMap<String, StringBuilder>()

    fun accept(event: InteractionEvent) {
        when (event) {
            is ReplyEvent -> acceptReply(event)
            is ModuleEvent -> {
                flush(event.target)
                sendStandalone(event.target, event.data.content)
            }

            is SystemEvent -> {
                flush(event.target)
                sendStandalone(event.target, formatSystemEvent(event))
            }
        }
    }

    fun flush(target: String) {
        val buffer = buffers[target] ?: return
        val content = buffer.toString().trim()
        buffer.clear()
        if (content.isNotBlank()) {
            sendSegments(target, content)
        }
    }

    override fun close() {
        buffers.keys.toList().forEach(::flush)
    }

    private fun acceptReply(event: ReplyEvent) {
        val content = event.content
        if (content.isNotBlank()) {
            val buffer = buffers.computeIfAbsent(event.target) { StringBuilder() }
            when (event.mode) {
                ReplyEvent.ContentMode.APPEND -> buffer.append(content)
                ReplyEvent.ContentMode.REPLACE -> {
                    buffer.clear()
                    buffer.append(content)
                }
            }
            flushCompletedBlocks(event.target, buffer)
        }

        if (event.status == InteractionEvent.EventStatus.DONE || event.status == InteractionEvent.EventStatus.ERROR) {
            flush(event.target)
        }
    }

    private fun flushCompletedBlocks(target: String, buffer: StringBuilder) {
        while (buffer.isNotEmpty()) {
            if (hasUnclosedCodeFence(buffer)) {
                return
            }

            val completedBlockEnd = findCompletedBlockEnd(buffer)
            if (completedBlockEnd > 0) {
                val block = buffer.substring(0, completedBlockEnd).trim()
                buffer.delete(0, completedBlockEnd)
                trimLeadingBlankLines(buffer)
                if (block.isNotBlank()) {
                    sendSegments(target, block)
                }
                continue
            }

            if (buffer.length >= softFlushChars) {
                val splitAt = findNaturalSplit(buffer, maxMessageChars.coerceAtMost(buffer.length))
                if (splitAt != null && splitAt > 0) {
                    val block = buffer.substring(0, splitAt).trim()
                    buffer.delete(0, splitAt)
                    trimLeadingBlankLines(buffer)
                    if (block.isNotBlank()) {
                        sendSegments(target, block)
                    }
                    continue
                }
            }

            return
        }
    }

    private fun findCompletedBlockEnd(buffer: CharSequence): Int {
        val text = buffer.toString()
        val blankLine = findFirstBlankLine(text)
        if (blankLine <= 0) {
            return -1
        }

        val candidate = text.substring(0, blankLine).trim()
        if (candidate.isBlank()) {
            return blankLine
        }

        val nextBlockStart = skipBlankLines(text, blankLine)
        if (isHeadingOnly(candidate) && nextBlockStart < text.length) {
            val nextBlankLine = findFirstBlankLine(text, nextBlockStart)
            if (nextBlankLine > 0) {
                return nextBlankLine
            }
            return -1
        }

        return blankLine
    }

    private fun sendSegments(target: String, content: String) {
        splitForOneBot(content).forEach { segment ->
            actionExecutor.sendMessage(target, segment)
        }
    }

    private fun sendStandalone(target: String, content: String) {
        if (content.isBlank()) {
            return
        }
        sendSegments(target, content.trim())
    }

    private fun splitForOneBot(content: String): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder(content.trim())

        while (buffer.length > maxMessageChars) {
            val splitAt = findNaturalSplit(buffer, maxMessageChars)
                ?.takeIf { it > 0 }
                ?: maxMessageChars
            val segment = buffer.substring(0, splitAt).trim()
            if (segment.isNotBlank()) {
                result.add(segment)
            }
            buffer.delete(0, splitAt)
            trimLeadingBlankLines(buffer)
        }

        val rest = buffer.toString().trim()
        if (rest.isNotBlank()) {
            result.add(rest)
        }
        return result
    }

    private fun findNaturalSplit(text: CharSequence, preferredMax: Int): Int? {
        val max = preferredMax.coerceAtMost(text.length)
        if (max <= 0) {
            return null
        }

        val searchStart = (max * 0.45).toInt().coerceAtLeast(1)
        val priorities = listOf(
            "\n\n", "\r\n\r\n",
            "。", "！", "？", "!", "?",
            "；", ";",
            "：", ":",
            "，", ",",
            "\n",
            " "
        )

        for (delimiter in priorities) {
            val idx = lastIndexOf(text, delimiter, max, searchStart)
            if (idx >= 0) {
                return idx + delimiter.length
            }
        }
        return null
    }

    private fun lastIndexOf(text: CharSequence, delimiter: String, endExclusive: Int, startInclusive: Int): Int {
        var i = endExclusive - delimiter.length
        while (i >= startInclusive) {
            var matched = true
            for (j in delimiter.indices) {
                if (text[i + j] != delimiter[j]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return i
            }
            i--
        }
        return -1
    }

    private fun hasUnclosedCodeFence(text: CharSequence): Boolean {
        var count = 0
        var index = 0
        val value = text.toString()
        while (true) {
            val found = value.indexOf("```", index)
            if (found < 0) {
                break
            }
            count++
            index = found + 3
        }
        return count % 2 != 0
    }

    private fun findFirstBlankLine(text: String, startIndex: Int = 0): Int {
        val lf = text.indexOf("\n\n", startIndex)
        val crlf = text.indexOf("\r\n\r\n", startIndex)
        return when {
            lf < 0 -> crlf.takeIf { it >= 0 }?.plus(4) ?: -1
            crlf < 0 -> lf + 2
            lf < crlf -> lf + 2
            else -> crlf + 4
        }
    }

    private fun skipBlankLines(text: String, index: Int): Int {
        var i = index
        while (i < text.length && (text[i] == '\n' || text[i] == '\r')) {
            i++
        }
        return i
    }

    private fun trimLeadingBlankLines(buffer: StringBuilder) {
        while (buffer.isNotEmpty() && (buffer.first() == '\n' || buffer.first() == '\r')) {
            buffer.deleteCharAt(0)
        }
    }

    private fun isHeadingOnly(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }
        return lines.size == 1 && lines.first().trimStart().startsWith("#")
    }

    private fun formatSystemEvent(event: SystemEvent): String {
        return buildString {
            append(event.title)
            if (event.content.isNotBlank()) {
                append('\n')
                append(event.content)
            }
        }
    }
}
