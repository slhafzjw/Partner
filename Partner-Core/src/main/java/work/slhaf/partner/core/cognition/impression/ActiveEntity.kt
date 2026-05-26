package work.slhaf.partner.core.cognition.impression

import org.w3c.dom.Document
import org.w3c.dom.Element
import work.slhaf.partner.core.cognition.context.BlockContent
import java.util.concurrent.atomic.AtomicReference

class ActiveEntity @JvmOverloads constructor(
    timestamp: Long = System.currentTimeMillis(),
    private val _evidences: MutableList<String> = mutableListOf(),
) : BlockContent("active_entity_$timestamp", "impression") {
    val evidences: List<String>
        get() = synchronized(_evidences) { _evidences.toList() }

    private val _subject = AtomicReference("UNKNOWN")
    val subject: String get() = _subject.get()

    private val _projectedFeatures: MutableMap<String, Double> = mutableMapOf()
    val projectedFeatures: Map<String, Double>
        get() = synchronized(_projectedFeatures) { _projectedFeatures.toMap() }

    private val _projectedImpressions: MutableMap<String, Double> = mutableMapOf()
    val projectedImpressions: Map<String, Double>
        get() = synchronized(_projectedImpressions) { _projectedImpressions.toMap() }

    fun addEvidence(evidence: String) = synchronized(_evidences) {
        _evidences.add(evidence)
    }

    fun updateSubject(subject: String) = _subject.set(subject)

    fun addProjectedFeatures(vararg features: Pair<String, Double>) = synchronized(_projectedFeatures) {
        features.forEach { _projectedFeatures[it.first] = it.second }
    }

    fun addProjectedImpressions(vararg impressions: Pair<String, Double>) = synchronized(_projectedImpressions) {
        impressions.forEach { _projectedImpressions[it.first] = it.second }
    }

    override fun fillXml(document: Document, root: Element) {
        appendTextElement(document, root, "subject", subject)

        appendListElement(
            document,
            root,
            "evidences",
            "evidence",
            synchronized(_evidences) { _evidences.toList() }
        )

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
}