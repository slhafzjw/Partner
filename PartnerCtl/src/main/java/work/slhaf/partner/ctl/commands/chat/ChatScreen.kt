package work.slhaf.partner.ctl.commands.chat

import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import work.slhaf.partner.api.InteractionEvent
import work.slhaf.partner.api.InteractionEvent.EventStatus
import work.slhaf.partner.api.ReplyEvent
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.ceil

internal class ChatScreen(
    private val terminal: Terminal = TerminalBuilder.builder()
        .system(true)
        .dumb(true)
        .build(),
    private val renderer: ChatEventRenderer = ChatEventRenderer(),
) : AutoCloseable {

    private val input = ChatInputBuffer()
    private val events: BlockingQueue<ChatScreenEvent> = LinkedBlockingQueue()
    private val activeReply = StringBuilder()
    private var dynamicRows = 0
    private var closed = false

    fun postInteractionEvent(event: InteractionEvent) {
        events.offer(ChatScreenEvent.Interaction(event))
    }

    fun postSystemMessage(message: String) {
        events.offer(ChatScreenEvent.SystemMessage(message))
    }

    fun run(onInput: (String) -> Unit) {
        terminal.writer().println("Partner chat. Type /exit to quit.")
        terminal.writer().println()
        terminal.writer().flush()

        val oldAttributes = terminal.enterRawMode()
        try {
            repaintDynamicArea()
            while (!closed) {
                drainEvents()
                val key = terminal.reader().read(50L)
                if (key != -1) {
                    handleKey(key, onInput)
                }
            }
        } finally {
            terminal.attributes = oldAttributes
            clearDynamicArea()
            terminal.writer().println()
            terminal.writer().flush()
        }
    }

    override fun close() {
        closed = true
    }

    private fun handleKey(key: Int, onInput: (String) -> Unit) {
        when (key) {
            CTRL_C, CTRL_D -> close()
            ENTER, CARRIAGE_RETURN -> submitInput(onInput)
            BACKSPACE, DELETE -> {
                input.backspace()
                repaintDynamicArea()
            }

            ESCAPE -> consumeEscapeSequence()
            else -> {
                if (key >= PRINTABLE_START) {
                    input.append(key.toChar())
                    repaintDynamicArea()
                }
            }
        }
    }

    private fun submitInput(onInput: (String) -> Unit) {
        val line = input.consume()
        if (line.isBlank()) {
            repaintDynamicArea()
            return
        }
        if (line == "/exit") {
            close()
            return
        }

        commitDynamicArea()
        printCommitted(renderer.renderCommittedUserInput(line))
        activeReply.setLength(0)
        repaintDynamicArea()

        runCatching { onInput(line) }
            .onFailure { error -> showSendFailure(error.message ?: error::class.java.simpleName) }
    }

    private fun drainEvents() {
        var changed = false
        while (true) {
            val event = events.poll() ?: break
            when (event) {
                is ChatScreenEvent.Interaction -> handleInteractionEvent(event.event)
                is ChatScreenEvent.SystemMessage -> {
                    commitDynamicArea()
                    printCommitted(event.message)
                }
            }
            changed = true
        }
        if (changed) {
            repaintDynamicArea()
        }
    }

    private fun handleInteractionEvent(event: InteractionEvent) {
        if (event is ReplyEvent) {
            when (event.mode) {
                ReplyEvent.ContentMode.APPEND -> activeReply.append(event.content)
                ReplyEvent.ContentMode.REPLACE -> {
                    activeReply.setLength(0)
                    activeReply.append(event.content)
                }
            }

            if (event.status == EventStatus.DONE || event.status == EventStatus.ERROR) {
                commitDynamicArea()
                if (activeReply.isNotBlank()) {
                    printCommitted(renderer.renderActiveReply(activeReply.toString()))
                    activeReply.setLength(0)
                }
                if (event.status == EventStatus.ERROR) {
                    printCommitted("assistant: [error]")
                }
            }
            return
        }

        renderer.renderEventMessage(event)?.let { message ->
            commitDynamicArea()
            printCommitted(message)
        }
    }

    private fun showSendFailure(message: String) {
        commitDynamicArea()
        printCommitted(renderer.renderSendFailure(message))
        repaintDynamicArea()
    }

    private fun commitDynamicArea() {
        clearDynamicArea()
    }

    private fun printCommitted(message: String) {
        message.split('\n').forEach(terminal.writer()::println)
        terminal.writer().flush()
    }

    private fun repaintDynamicArea() {
        clearDynamicArea()

        val output = dynamicOutput()
        terminal.writer().print(output)
        terminal.writer().flush()

        dynamicRows = measureDisplayRows(output)
    }

    private fun clearDynamicArea() {
        if (dynamicRows <= 0) {
            return
        }

        terminal.writer().print("\r")
        if (dynamicRows > 1) {
            terminal.writer().print("\u001B[${dynamicRows - 1}A")
        }
        terminal.writer().print("\u001B[J")
        dynamicRows = 0
    }

    private fun dynamicOutput(): String {
        return buildString {
            if (activeReply.isNotBlank()) {
                append(renderer.renderActiveReply(activeReply.toString()))
                append('\n')
            }
            append(inputPrompt())
        }
    }

    private fun inputPrompt(): String = "partner> ${input}"

    private fun measureDisplayRows(text: String): Int {
        val width = terminal.width.takeIf { it > 0 } ?: DEFAULT_TERMINAL_WIDTH
        return text.split('\n').sumOf { line ->
            ceil(displayWidth(line).coerceAtLeast(1).toDouble() / width.toDouble())
                .toInt()
                .coerceAtLeast(1)
        }
    }

    private fun displayWidth(text: String): Int {
        var width = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            width += codePointWidth(codePoint)
            index += Character.charCount(codePoint)
        }
        return width
    }

    private fun codePointWidth(codePoint: Int): Int {
        val type = Character.getType(codePoint)
        if (type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.FORMAT.toInt()
        ) {
            return 0
        }

        return when {
            codePoint == 0 -> 0
            codePoint < 32 || codePoint in 0x7F..0x9F -> 0
            isWideCodePoint(codePoint) -> 2
            else -> 1
        }
    }

    private fun isWideCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x1100..0x115F ||
                codePoint in 0x2329..0x232A ||
                codePoint in 0x2E80..0xA4CF ||
                codePoint in 0xAC00..0xD7A3 ||
                codePoint in 0xF900..0xFAFF ||
                codePoint in 0xFE10..0xFE19 ||
                codePoint in 0xFE30..0xFE6F ||
                codePoint in 0xFF00..0xFF60 ||
                codePoint in 0xFFE0..0xFFE6 ||
                codePoint in 0x1F300..0x1FAFF ||
                codePoint in 0x20000..0x3FFFD
    }

    private fun consumeEscapeSequence() {
        while (terminal.reader().read(1L) != -1) {
            // Drop the rest of the currently available escape sequence.
        }
    }

    private sealed interface ChatScreenEvent {
        data class Interaction(val event: InteractionEvent) : ChatScreenEvent
        data class SystemMessage(val message: String) : ChatScreenEvent
    }

    private companion object {
        private const val CTRL_C = 3
        private const val CTRL_D = 4
        private const val BACKSPACE = 8
        private const val DELETE = 127
        private const val ENTER = 10
        private const val CARRIAGE_RETURN = 13
        private const val ESCAPE = 27
        private const val PRINTABLE_START = 32
        private const val DEFAULT_TERMINAL_WIDTH = 80
    }
}
