package work.slhaf.partner.core.cognition.impression

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Entity @JvmOverloads constructor(
    val uuid: String = UUID.randomUUID().toString(),
    private val relations: MutableMap<String, MutableMap<String, Double>> = mutableMapOf(),
    private val impressions: MutableMap<String, ImpressionData> = mutableMapOf()
) {

    private val impressionLock = ReentrantLock()
    private val relationLock = ReentrantLock()

    @JvmOverloads
    fun updateRelation(
        target: String,
        relation: String,
        strength: Double = 1.0
    ) = relationLock.withLock {
        relations.computeIfAbsent(target) { mutableMapOf() }[relation] = strength
    }

    @JvmOverloads
    fun updateImpression(
        impression: String,
        newImpression: String? = null,
        strength: Double = 1.0
    ): ImpressionData = impressionLock.withLock {
        if (newImpression == null) {
            impressions.computeIfAbsent(impression) { ImpressionData(strength) }
                .also { it.confidence = strength }
        } else {
            impressions.remove(impression)
            ImpressionData(strength).also {
                impressions[newImpression] = it
            }
        }
    }

    fun removeImpression(impression: String) = impressionLock.withLock {
        impressions.remove(impression)
    }

    @JvmOverloads
    fun removeRelation(
        target: String,
        relation: String? = null
    ) = relationLock.withLock {
        if (relation == null) {
            relations.remove(target)
        } else {
            relations[target]?.remove(relation)
            if (relations[target].isNullOrEmpty()) {
                relations.remove(target)
            }
        }
    }

    fun showRelations(): Set<RelationView> = relationLock.withLock {
        relations.map {
            RelationView(
                it.key,
                it.value.toMap()
            )
        }.toSet()
    }

    fun showImpressions(embeddingModel: String): Set<ImpressionView> = impressionLock.withLock {
        impressions.map {
            ImpressionView(
                it.key,
                it.value.confidence,
                it.value.getVector(embeddingModel)
            )
        }.toSet()
    }

    data class ImpressionData(
        var confidence: Double
    ) {
        private val vectors: ConcurrentHashMap<String, DoubleArray> = ConcurrentHashMap()

        fun updateVector(
            embeddingModel: String,
            vector: DoubleArray
        ) {
            vectors[embeddingModel] = vector
        }

        fun getVector(embeddingModel: String): DoubleArray? {
            return vectors[embeddingModel]?.copyOf()
        }
    }

    data class RelationView(
        val target: String,
        val relations: Map<String, Double>
    )

    @Suppress("ArrayInDataClass")
    data class ImpressionView(
        val impression: String,
        val confidence: Double,
        val vector: DoubleArray?
    )
}