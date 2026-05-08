package work.slhaf.partner.ctl.support

data class PromptPart(
    val text: String,
    val style: PromptStyle = PromptStyle.PLAIN,
)

enum class PromptStyle {
    PLAIN,
    DIM,
    BOLD,
    CYAN,
    GREEN,
    YELLOW,
    RED,
    BLUE,
}

object TerminalText {
    val colorEnabled: Boolean
        get() = System.getenv("NO_COLOR") == null &&
                !(System.getenv("TERM") ?: "").equals("dumb", ignoreCase = true)

    fun render(parts: List<PromptPart>, colorEnabled: Boolean = this.colorEnabled): String {
        return parts.joinToString(separator = "") { render(it, colorEnabled) }
    }

    fun render(part: PromptPart, colorEnabled: Boolean = this.colorEnabled): String {
        return when (part.style) {
            PromptStyle.PLAIN -> part.text
            PromptStyle.DIM -> ansi("2", part.text, colorEnabled)
            PromptStyle.BOLD -> ansi("1", part.text, colorEnabled)
            PromptStyle.CYAN -> ansi("36", part.text, colorEnabled)
            PromptStyle.GREEN -> ansi("32", part.text, colorEnabled)
            PromptStyle.YELLOW -> ansi("33", part.text, colorEnabled)
            PromptStyle.RED -> ansi("31", part.text, colorEnabled)
            PromptStyle.BLUE -> ansi("34", part.text, colorEnabled)
        }
    }

    fun ansi(code: String, text: String, colorEnabled: Boolean = this.colorEnabled): String {
        if (!colorEnabled) return text
        val escape = 27.toChar()
        return "$escape[${code}m$text$escape[0m"
    }

    fun stripAnsi(text: String): String {
        val escape = 27.toChar()
        val result = StringBuilder(text.length)
        var index = 0

        while (index < text.length) {
            if (text[index] == escape && index + 1 < text.length && text[index + 1] == '[') {
                index += 2
                while (index < text.length && text[index] !in '@'..'~') {
                    index++
                }
                if (index < text.length) {
                    index++
                }
            } else {
                result.append(text[index])
                index++
            }
        }

        return result.toString()
    }
}
