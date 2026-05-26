package work.slhaf.partner.core.cognition.impression

import com.alibaba.fastjson2.JSONObject
import work.slhaf.partner.framework.agent.state.State
import work.slhaf.partner.framework.agent.state.StateSerializable
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Entity @JvmOverloads constructor(
    val uuid: String = UUID.randomUUID().toString(),
    val subject: String,
    private val relations: MutableMap<String, MutableMap<String, Double>> = mutableMapOf(),
    private val impressions: MutableMap<String, IndexableData> = mutableMapOf(),
    private val features: MutableMap<String, IndexableData> = mutableMapOf()
) : StateSerializable {

    private val impressionLock = ReentrantLock()
    private val relationLock = ReentrantLock()
    private val featureLock = ReentrantLock()

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
        confidence: Double = 1.0
    ): IndexableData = impressionLock.withLock {
        if (newImpression == null) {
            impressions.computeIfAbsent(impression) { IndexableData(confidence) }
                .also {
                    it.confidence = confidence
                    if (it.confidence >= 0.9) {
                        featureLock.withLock { features[impression] = it }
                    }
                }
        } else {
            impressions.remove(impression)
            IndexableData(confidence).also {
                impressions[newImpression] = it
                if (it.confidence >= 0.9) {
                    featureLock.withLock { features[newImpression] = it }
                }
            }
        }
    }

    fun updateFeature(feature: String, newFeature: String? = null, confidence: Double = 1.0) = featureLock.withLock {
        if (newFeature == null) {
            features.computeIfAbsent(feature) { IndexableData(confidence) }
                .also { it.confidence = confidence }
        } else {
            features.remove(feature)
            IndexableData(confidence).also {
                features[newFeature] = it
            }
        }
    }

    fun removeFeature(feature: String) = featureLock.withLock {
        features.remove(feature)
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


    fun showFeatures(): Set<FeatureView> = featureLock.withLock {
        features.map {
            FeatureView(
                it.key,
                it.value.confidence
            )
        }.toSet()
    }

    override fun statePath(): Path = Path.of("core", "impression", "entity-$uuid.json")

    override fun load(state: JSONObject) {
        TODO("Not yet implemented")
    }

    override fun convert(): State {
        TODO("Not yet implemented")
    }

    override fun autoLoadOnRegister(): Boolean = false

    data class IndexableData(
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

    data class FeatureView(
        val feature: String,
        val confidence: Double
    )

    @Suppress("ArrayInDataClass")
    data class ImpressionView(
        val impression: String,
        val confidence: Double,
        val vector: DoubleArray?
    )
}