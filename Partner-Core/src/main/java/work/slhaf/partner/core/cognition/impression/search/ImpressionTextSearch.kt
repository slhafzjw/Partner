package work.slhaf.partner.core.cognition.impression.search

interface ImpressionTextSearch {

    fun rebuild(documents: Collection<ImpressionSearchDocument>)

    fun upsert(document: ImpressionSearchDocument)

    fun removeByTarget(target: ImpressionSearchTarget)

    fun search(
        query: String,
        limit: Int = 20,
    ): List<ImpressionSearchHit>
}
