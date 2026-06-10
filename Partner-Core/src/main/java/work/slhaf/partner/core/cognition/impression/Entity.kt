package work.slhaf.partner.core.cognition.impression

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import work.slhaf.partner.framework.agent.state.State
import work.slhaf.partner.framework.agent.state.StateSerializable
import work.slhaf.partner.framework.agent.state.StateValue
import java.nio.file.Path
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Entity @JvmOverloads constructor(
    val uuid: String = UUID.randomUUID().toString(),
    subject: String,
    private val relations: MutableMap<String, MutableMap<String, Double>> = mutableMapOf(),
    private val impressions: MutableMap<String, IndexableData> = mutableMapOf(),
    private val features: MutableMap<String, IndexableData> = mutableMapOf(),
    private val aliases: MutableMap<String, AliasMetadata> = mutableMapOf()
) : StateSerializable {

    private var _subject: String = normalizeIdentityText(subject)

    private val impressionLock = ReentrantLock()
    private val relationLock = ReentrantLock()
    private val featureLock = ReentrantLock()
    private val identityLock = ReentrantLock()

    val subject: String
        get() = identityLock.withLock { _subject }

    @JvmOverloads
    fun renameSubject(newSubject: String, keepOldSubjectAsAlias: Boolean = true): Boolean = identityLock.withLock {
        val normalizedSubject = normalizeIdentityText(newSubject)
        if (normalizedSubject.isBlank() || normalizedSubject == _subject) {
            return@withLock false
        }

        val previousSubject = _subject
        if (keepOldSubjectAsAlias && previousSubject.isNotBlank()) {
            aliases[previousSubject] = aliases[previousSubject]?.copy(deprecated = true)
                ?: AliasMetadata(Instant.now(), deprecated = true)
        }

        aliases.remove(normalizedSubject)
        _subject = normalizedSubject
        true
    }

    @JvmOverloads
    fun addAlias(alias: String, deprecated: Boolean = false): Boolean = identityLock.withLock {
        val normalizedAlias = normalizeIdentityText(alias)
        if (normalizedAlias.isBlank() || normalizedAlias == _subject) {
            return@withLock false
        }

        aliases[normalizedAlias] = aliases[normalizedAlias]?.copy(deprecated = deprecated)
            ?: AliasMetadata(Instant.now(), deprecated)
        true
    }

    @JvmOverloads
    fun showAliases(includeDeprecated: Boolean = false): Set<AliasView> = identityLock.withLock {
        aliases.asSequence()
            .filter { (_, metadata) -> includeDeprecated || !metadata.deprecated }
            .map { (alias, metadata) ->
                AliasView(alias, metadata.instant, metadata.deprecated)
            }
            .sortedWith(compareBy<AliasView> { it.createdAt }.thenBy { it.alias })
            .toCollection(LinkedHashSet())
    }

    fun snapshotAliases(): Map<String, AliasMetadata> = identityLock.withLock {
        aliases.mapValues { (_, metadata) -> metadata.copy() }
    }

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

    fun snapshotImpressions(): Map<String, IndexableData> = impressionLock.withLock {
        impressions.toMap()
    }

    fun snapshotFeatures(): Map<String, IndexableData> = featureLock.withLock {
        features.toMap()
    }

    override fun statePath(): Path = Path.of("core", "impression", "entity-$uuid.json")

    override fun load(state: JSONObject) {
        state.getJSONObject("relations")?.let { loadedRelations ->
            relationLock.withLock {
                relations.clear()
                loadedRelations.forEach { (target, relationValue) ->
                    val relationObject = relationValue as? JSONObject ?: return@forEach
                    val relationMap = mutableMapOf<String, Double>()
                    relationObject.forEach { (relation, strengthValue) ->
                        doubleValue(strengthValue)?.let { relationMap[relation] = it }
                    }
                    if (relationMap.isNotEmpty()) {
                        relations[target] = relationMap
                    }
                }
            }
        }

        state.getJSONObject("impressions")?.let { loadedImpressions ->
            impressionLock.withLock {
                impressions.clear()
                impressions.putAll(loadIndexableDataMap(loadedImpressions))
            }
        }

        identityLock.withLock {
            state.getString("subject")
                ?.let(::normalizeIdentityText)
                ?.takeIf(String::isNotBlank)
                ?.let { _subject = it }
        }

        state.getJSONObject("features")?.let { loadedFeatures ->
            featureLock.withLock {
                features.clear()
                features.putAll(loadIndexableDataMap(loadedFeatures))
            }
        }

        state.getJSONObject("aliases")?.let { loadedAliases ->
            identityLock.withLock {
                aliases.clear()
                loadedAliases.forEach { (alias, metadataValue) ->
                    val normalizedAlias = normalizeIdentityText(alias)
                    if (normalizedAlias.isBlank() || normalizedAlias == _subject) {
                        return@forEach
                    }

                    val metadata = when (metadataValue) {
                        is JSONObject -> loadAliasMetadata(metadataValue)
                        else -> AliasMetadata(Instant.now(), deprecated = false)
                    }
                    aliases[normalizedAlias] = metadata
                }
            }
        }
    }

    override fun convert(): State {
        val state = State()
        state.append("uuid", StateValue.str(uuid))

        val identityState = identityLock.withLock {
            IdentityState(
                subject = _subject,
                aliases = aliases.mapValues { (_, metadata) ->
                    mapOf(
                        "timestamp" to metadata.instant.toEpochMilli(),
                        "deprecated" to metadata.deprecated
                    )
                }
            )
        }
        state.append("subject", StateValue.str(identityState.subject))
        state.append("aliases", StateValue.obj(identityState.aliases))

        val relationState = relationLock.withLock {
            relations.mapValues { (_, relationMap) -> relationMap.toMap() }
        }
        state.append("relations", StateValue.obj(relationState))

        val impressionState = impressionLock.withLock {
            indexableDataState(impressions)
        }
        state.append("impressions", StateValue.obj(impressionState))

        val featureState = featureLock.withLock {
            indexableDataState(features)
        }
        state.append("features", StateValue.obj(featureState))

        return state
    }

    override fun autoLoadOnRegister(): Boolean = false

    private fun normalizeIdentityText(value: String): String =
        value.replace(IDENTITY_WHITESPACE_REGEX, " ").trim()

    private fun loadAliasMetadata(state: JSONObject): AliasMetadata {
        val instant = state.getLong("timestamp")
            ?.let(Instant::ofEpochMilli)
            ?: state.getString("instant")
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: Instant.now()

        return AliasMetadata(
            instant = instant,
            deprecated = state.getBoolean("deprecated") ?: false
        )
    }

    private fun loadIndexableDataMap(state: JSONObject): Map<String, IndexableData> {
        val loaded = mutableMapOf<String, IndexableData>()
        state.forEach { (key, value) ->
            val data = when (value) {
                is JSONObject -> loadIndexableData(value)
                else -> IndexableData(doubleValue(value) ?: return@forEach)
            }
            loaded[key] = data
        }
        return loaded
    }

    private fun loadIndexableData(state: JSONObject): IndexableData {
        val data = IndexableData(state.getDouble("confidence") ?: 1.0)
        state.getJSONObject("vectors")?.forEach { (embeddingModel, vectorValue) ->
            val vectorArray = vectorValue as? JSONArray ?: return@forEach
            val vector = FloatArray(vectorArray.size)
            for (index in vectorArray.indices) {
                vector[index] = floatValue(vectorArray[index]) ?: return@forEach
            }
            data.updateVector(embeddingModel, vector)
        }
        return data
    }

    private fun indexableDataState(source: Map<String, IndexableData>): Map<String, Map<String, Any>> =
        source.mapValues { (_, data) ->
            mapOf(
                "confidence" to data.confidence,
                "vectors" to data.snapshotVectors().mapValues { (_, vector) -> vector.toList() }
            )
        }

    private fun doubleValue(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private fun floatValue(value: Any?): Float? = when (value) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull()
        else -> null
    }

    data class IndexableData(
        var confidence: Double
    ) {
        private val vectors: ConcurrentHashMap<String, FloatArray> = ConcurrentHashMap()

        fun updateVector(
            embeddingModel: String,
            vector: FloatArray
        ) {
            vectors[embeddingModel] = vector.copyOf()
        }

        fun getVector(embeddingModel: String): FloatArray? {
            return vectors[embeddingModel]?.copyOf()
        }

        fun snapshotVectors(): Map<String, FloatArray> {
            return vectors.mapValues { (_, vector) -> vector.copyOf() }
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
        val vector: FloatArray?
    )

    private data class IdentityState(
        val subject: String,
        val aliases: Map<String, Map<String, Any>>
    )

    data class AliasView(
        val alias: String,
        val createdAt: Instant,
        val deprecated: Boolean
    )

    data class AliasMetadata(
        val instant: Instant,
        val deprecated: Boolean
    )

    companion object {
        private val IDENTITY_WHITESPACE_REGEX = Regex("\\s+")
    }
}
