package work.slhaf.partner.core.cognition.impression.search

interface ImpressionTokenizer {
    fun tokenize(text: String): Set<String>
}