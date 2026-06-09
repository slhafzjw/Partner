package work.slhaf.partner.core.cognition.impression

import org.w3c.dom.Document
import org.w3c.dom.Element
import work.slhaf.partner.core.cognition.context.BlockContent
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class ActiveEntity @JvmOverloads constructor(
    val runtimeId: String = newActiveEntityRuntimeId(),
    val createdAt: Instant = Instant.now(),
    boundEntityUuid: String? = null,
    private val _evidences: MutableList<EntityEvidence> = mutableListOf(),
) : BlockContent("active_entity_$runtimeId", "impression") {
    val evidences: List<EntityEvidence>
        get() = synchronized(_evidences) { _evidences.toList() }

    @Volatile
    var lastMentionedAt: Instant = createdAt
        private set

    private val _subject = AtomicReference("UNKNOWN")
    val subject: String get() = _subject.get()

    private val _boundEntityUuid = AtomicReference<String?>(boundEntityUuid)
    val boundEntityUuid: String? get() = _boundEntityUuid.get()

    private val _projectedFeatures: MutableMap<String, Double> = mutableMapOf()
    val projectedFeatures: Map<String, Double>
        get() = synchronized(_projectedFeatures) { _projectedFeatures.toMap() }

    private val _projectedImpressions: MutableMap<String, Double> = mutableMapOf()
    val projectedImpressions: Map<String, Double>
        get() = synchronized(_projectedImpressions) { _projectedImpressions.toMap() }

    @JvmOverloads
    fun addEvidence(
        content: String,
        associationConfidence: Double = 1.0,
        source: EntityEvidence.Source = EntityEvidence.Source.USER_INPUT,
        timestamp: Long = System.currentTimeMillis(),
    ) = addEvidence(EntityEvidence(content, associationConfidence, source, timestamp))

    fun addEvidence(evidence: EntityEvidence) = synchronized(_evidences) {
        _evidences.add(evidence)
        touch(Instant.ofEpochMilli(evidence.timestamp))
    }

    fun updateSubject(subject: String) = _subject.set(subject)

    fun bindEntity(uuid: String?) = _boundEntityUuid.set(uuid)

    fun touch(time: Instant = Instant.now()) {
        lastMentionedAt = time
    }

    fun addProjectedFeatures(vararg features: Pair<String, Double>) = synchronized(_projectedFeatures) {
        features.forEach { _projectedFeatures[it.first] = it.second }
    }

    fun addProjectedImpressions(vararg impressions: Pair<String, Double>) = synchronized(_projectedImpressions) {
        impressions.forEach { _projectedImpressions[it.first] = it.second }
    }

    override fun fillXml(document: Document, root: Element) {
        root.setAttribute("runtime_id", runtimeId)
        boundEntityUuid?.let { root.setAttribute("bound_entity_uuid", it) }
        root.setAttribute("created_at", modelTime(createdAt))
        root.setAttribute("last_mentioned_at", modelTime(lastMentionedAt))

        appendTextElement(document, root, "subject", subject)

        appendListElement(
            document,
            root,
            "evidences",
            "evidence",
            synchronized(_evidences) { _evidences.toList() }
        ) { evidence ->
            setAttribute("association_confidence", evidence.associationConfidence.toString())
            setAttribute("source", evidence.source.name)
            setAttribute("timestamp", evidence.timestamp.toString())
            setAttribute("truncated", evidence.isContentTruncated().toString())
            setAttribute("original_length", evidence.content.length.toString())
            textContent = evidence.contentForContext()
        }

        appendListElement(
            document,
            root,
            "projected_features",
            "feature",
            synchronized(_projectedFeatures) { _projectedFeatures.entries.toList() }
        ) { entry ->
            setAttribute("confidence", entry.value.toString())
            textContent = entry.key
        }

        appendListElement(
            document,
            root,
            "projected_impressions",
            "impression",
            synchronized(_projectedImpressions) { _projectedImpressions.entries.toList() }
        ) { entry ->
            setAttribute("confidence", entry.value.toString())
            textContent = entry.key
        }
    }

    private fun modelTime(time: Instant): String =
        time.atZone(ZoneId.systemDefault()).toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActiveEntity) return false
        return runtimeId == other.runtimeId
    }

    override fun hashCode(): Int {
        return runtimeId.hashCode()
    }
}

private fun newActiveEntityRuntimeId(): String =
    UUID.randomUUID().toString().replace("-", "").take(12)
