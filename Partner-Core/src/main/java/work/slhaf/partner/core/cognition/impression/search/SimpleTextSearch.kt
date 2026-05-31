package work.slhaf.partner.core.cognition.impression.search

class SimpleTextSearch(
    private val tokenizer: ImpressionTokenizer = JiebaImpressionTokenizer(),
) : ImpressionTextSearch {

    private val documents = linkedMapOf<String, IndexedDocument>()
    private val invertedIndex = linkedMapOf<String, MutableSet<String>>()

    @Synchronized
    override fun rebuild(documents: Collection<ImpressionSearchDocument>) {
        this.documents.clear()
        invertedIndex.clear()
        documents.forEach(::upsertInternal)
    }

    @Synchronized
    override fun upsert(document: ImpressionSearchDocument) {
        removeByDocumentId(document.id)
        upsertInternal(document)
    }

    @Synchronized
    override fun removeByTarget(target: ImpressionSearchTarget) {
        documents.values
            .asSequence()
            .filter { it.document.target == target }
            .map { it.document.id }
            .toList()
            .forEach(::removeByDocumentId)
    }

    @Synchronized
    override fun search(query: String, limit: Int): List<ImpressionSearchHit> {
        if (limit <= 0) {
            return emptyList()
        }

        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        val queryTerms = tokenizer.tokenize(normalizedQuery)
        val candidateIds = if (queryTerms.isEmpty()) {
            documents.keys.toSet()
        } else {
            queryTerms
                .asSequence()
                .flatMap { invertedIndex[it].orEmpty().asSequence() }
                .toSet()
        }

        return candidateIds
            .asSequence()
            .mapNotNull { documentId -> scoreDocument(documents[documentId] ?: return@mapNotNull null, normalizedQuery, queryTerms) }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<ImpressionSearchHit> { it.score }.thenBy { it.document.id })
            .take(limit)
            .toList()
    }

    private fun upsertInternal(document: ImpressionSearchDocument) {
        val normalizedText = normalize(document.text)
        val terms = tokenizer.tokenize(normalizedText)
        val indexedDocument = IndexedDocument(document, normalizedText, terms)
        documents[document.id] = indexedDocument
        terms.forEach { term ->
            invertedIndex.getOrPut(term) { linkedSetOf() }.add(document.id)
        }
    }

    private fun removeByDocumentId(documentId: String) {
        val indexedDocument = documents.remove(documentId) ?: return
        indexedDocument.terms.forEach { term ->
            val ids = invertedIndex[term] ?: return@forEach
            ids.remove(documentId)
            if (ids.isEmpty()) {
                invertedIndex.remove(term)
            }
        }
    }

    private fun scoreDocument(
        indexedDocument: IndexedDocument,
        normalizedQuery: String,
        queryTerms: Set<String>,
    ): ImpressionSearchHit? {
        val matchedTerms = if (queryTerms.isEmpty()) {
            emptySet()
        } else {
            queryTerms.intersect(indexedDocument.terms)
        }
        val exactPhraseMatched = indexedDocument.normalizedText.contains(normalizedQuery)

        if (matchedTerms.isEmpty() && !exactPhraseMatched) {
            return null
        }

        val coverage = if (queryTerms.isEmpty()) 0.0 else matchedTerms.size.toDouble() / queryTerms.size.toDouble()
        val termScore = matchedTerms.size.toDouble()
        val exactPhraseBonus = if (exactPhraseMatched) EXACT_PHRASE_BONUS else 0.0
        val fieldBonus = fieldBonus(indexedDocument.document.field)
        val score = (termScore + coverage + exactPhraseBonus + fieldBonus) * indexedDocument.document.weight

        return ImpressionSearchHit(
            document = indexedDocument.document,
            score = score,
            matchedTerms = matchedTerms,
        )
    }

    private fun fieldBonus(field: ImpressionSearchField): Double = when (field) {
        ImpressionSearchField.SUBJECT -> 0.8
        ImpressionSearchField.FEATURE -> 0.35
        ImpressionSearchField.IMPRESSION -> 0.25
        ImpressionSearchField.RELATION -> 0.15
        ImpressionSearchField.EVIDENCE -> 0.0
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private data class IndexedDocument(
        val document: ImpressionSearchDocument,
        val normalizedText: String,
        val terms: Set<String>,
    )

    companion object {
        private const val EXACT_PHRASE_BONUS = 1.5
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
