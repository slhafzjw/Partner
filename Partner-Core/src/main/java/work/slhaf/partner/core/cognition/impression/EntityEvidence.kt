package work.slhaf.partner.core.cognition.impression

/**
 * Runtime evidence associated with an active entity.
 *
 * The confidence describes how strongly this evidence is associated with the
 * current active entity, not whether the evidence content itself is true.
 */
data class EntityEvidence @JvmOverloads constructor(
    val content: String,
    val associationConfidence: Double = 1.0,
    val source: Source = Source.USER_INPUT,
    val timestamp: Long = System.currentTimeMillis(),
) {
    enum class Source {
        USER_INPUT,
        ASSISTANT_REPLY
    }

    fun isContentTruncated(maxLength: Int = CONTEXT_CONTENT_MAX_LENGTH): Boolean =
        content.length > maxLength

    fun contentForContext(maxLength: Int = CONTEXT_CONTENT_MAX_LENGTH): String {
        if (content.length <= maxLength) {
            return content
        }

        val available = maxLength - OMITTED_MARKER.length
        if (available <= 0) {
            return content.take(maxLength)
        }

        val headBudget = available / 2
        val tailBudget = available - headBudget
        val headEnd = adjustHeadEnd(content, headBudget)
        val tailStart = adjustTailStart(content, content.length - tailBudget)

        if (tailStart <= headEnd) {
            return content.take(maxLength).trimEnd()
        }

        return content.substring(0, headEnd).trimEnd() +
                OMITTED_MARKER +
                content.substring(tailStart).trimStart()
    }

    private fun adjustHeadEnd(source: String, preferredEnd: Int): Int {
        val safePreferredEnd = preferredEnd.coerceIn(0, source.length)
        findForwardBoundary(source, safePreferredEnd, STRONG_BOUNDARY_SEARCH_WINDOW, ::isStrongBoundary)?.let {
            return it + 1
        }
        findForwardBoundary(source, safePreferredEnd, SOFT_BOUNDARY_SEARCH_WINDOW, ::isSoftBoundary)?.let {
            return it + 1
        }
        return safePreferredEnd
    }

    private fun adjustTailStart(source: String, preferredStart: Int): Int {
        val safePreferredStart = preferredStart.coerceIn(0, source.length)
        findBackwardBoundary(source, safePreferredStart, STRONG_BOUNDARY_SEARCH_WINDOW, ::isStrongBoundary)?.let {
            return it
        }
        findBackwardBoundary(source, safePreferredStart, SOFT_BOUNDARY_SEARCH_WINDOW, ::isSoftBoundary)?.let {
            return it
        }
        return safePreferredStart
    }

    private fun findForwardBoundary(
        source: String,
        start: Int,
        window: Int,
        predicate: (Char) -> Boolean,
    ): Int? {
        val end = (start + window).coerceAtMost(source.length)
        for (index in start until end) {
            if (predicate(source[index])) {
                return index
            }
        }
        return null
    }

    private fun findBackwardBoundary(
        source: String,
        start: Int,
        window: Int,
        predicate: (Char) -> Boolean,
    ): Int? {
        val end = (start - window).coerceAtLeast(0)
        for (index in start downTo end + 1) {
            if (predicate(source[index - 1])) {
                return index
            }
        }
        return null
    }

    private fun isStrongBoundary(char: Char): Boolean = char == '\n'

    private fun isSoftBoundary(char: Char): Boolean = when (char) {
        '。', '！', '？', ';', '；', '.' -> true
        else -> false
    }

    companion object {
        const val CONTEXT_CONTENT_MAX_LENGTH = 480
        private const val OMITTED_MARKER = "\n...[omitted]...\n"
        private const val STRONG_BOUNDARY_SEARCH_WINDOW = 120
        private const val SOFT_BOUNDARY_SEARCH_WINDOW = 80
    }
}
