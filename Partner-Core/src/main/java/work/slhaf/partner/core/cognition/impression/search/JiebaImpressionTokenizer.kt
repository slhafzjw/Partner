package work.slhaf.partner.core.cognition.impression.search

import com.huaban.analysis.jieba.JiebaSegmenter

class JiebaImpressionTokenizer(
    private val segmenter: JiebaSegmenter = JiebaSegmenter(),
    private val mode: JiebaSegmenter.SegMode = JiebaSegmenter.SegMode.SEARCH,
) : ImpressionTokenizer {

    override fun tokenize(text: String): Set<String> {
        val normalized = normalize(text)
        if (normalized.isBlank()) {
            return emptySet()
        }

        val jiebaTerms = segmenter.process(normalized, mode)
            .asSequence()
            .map { it.word }
            .map(::normalize)
            .filter { it.isNotBlank() }

        return (jiebaTerms + alphaNumericTerms(normalized)).toSet()
    }

    private fun alphaNumericTerms(text: String): Sequence<String> =
        ALPHA_NUMERIC_REGEX.findAll(text).map { it.value }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val ALPHA_NUMERIC_REGEX = Regex("[a-z0-9]+(?:[-_./][a-z0-9]+)*")
    }
}
