package work.slhaf.partner.ctl.commands.chat

internal class ChatInputBuffer {
    private val buffer = StringBuilder()

    val isEmpty: Boolean
        get() = buffer.isEmpty()

    fun append(ch: Char) {
        buffer.append(ch)
    }

    fun backspace() {
        if (buffer.isNotEmpty()) {
            buffer.deleteCharAt(buffer.length - 1)
        }
    }

    fun clear() {
        buffer.setLength(0)
    }

    fun consume(): String {
        val value = buffer.toString()
        clear()
        return value
    }

    override fun toString(): String = buffer.toString()
}
