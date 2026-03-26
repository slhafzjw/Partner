package work.slhaf.partner.core.cognition

import org.w3c.dom.Document
import org.w3c.dom.Element
import work.slhaf.partner.common.base.Block
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlin.math.max
import kotlin.math.min

class ContextWorkspace {

    private val stateSet = mutableSetOf<ContextBlock>()
    private val lock = ReentrantReadWriteLock()

    /**
     * 根据传入的 [ContextBlock.VisibleDomain] 列表，获取上下文块
     * @param domains 需要获取上下文的域列表，顺序将决定权重优先级，按照列表排序将具备线性权重分层，最终反映到 blockContent 列表的排序上
     */
    fun resolve(domains: List<ContextBlock.VisibleDomain>): ResolvedContext = lock.write {
        if (domains.isEmpty()) {
            return@write ResolvedContext(emptyList())
        }
        val primaryDomain = domains.first()

        val domainWeights = domains
            .distinct()
            .withIndex()
            .associate { (index, domain) -> domain to (domains.size - index) }

        val activeBlocks = mutableListOf<ResolvedContextBlock>()
        val iterator = stateSet.iterator()
        while (iterator.hasNext()) {
            val block = iterator.next()
            val fadedScore = block.applyTimeFade()
            if (fadedScore <= 0.0) {
                iterator.remove()
                continue
            }

            val matchedDomains = block.visibleTo.intersect(domainWeights.keys)
            if (matchedDomains.isEmpty()) {
                continue
            }

            val exposure = if (primaryDomain in matchedDomains) {
                ContextBlock.Exposure.PRIMARY
            } else {
                ContextBlock.Exposure.SECONDARY
            }
            val activationScore = block.activate(exposure)
            if (activationScore <= 0.0) {
                iterator.remove()
                continue
            }

            activeBlocks += ResolvedContextBlock(
                block = block,
                domainWeight = matchedDomains.sumOf { domainWeights.getValue(it) },
                activationScore = activationScore,
                renderedBlock = block.render(exposure)
            )
        }

        val blocks = activeBlocks
            .sortedWith(
                compareBy<ResolvedContextBlock> { it.domainWeight }
                    .thenBy { it.block.sourceKey.blockName }
                    .thenBy { it.block.sourceKey.source }
                    .thenBy { it.activationScore }
                    .thenBy { it.block.blockContent.encodeToXmlString() }
            )
            .groupBy { it.block.sourceKey }
            .values
            .map { groupedBlocks ->
                if (groupedBlocks.size == 1) {
                    renderResolvedBlock(groupedBlocks.first())
                } else {
                    AggregatedBlockContent(groupedBlocks)
                }
            }
        ResolvedContext(blocks)
    }

    private fun renderResolvedBlock(resolved: ResolvedContextBlock): BlockContent {
        return resolved.renderedBlock
    }


    /**
     * @param contextBlock 注册的新上下文块
     */
    fun register(contextBlock: ContextBlock) = lock.write {
        val iterator = stateSet.iterator()
        while (iterator.hasNext()) {
            val currentBlock = iterator.next()
            if (!currentBlock.sameWith(contextBlock)) {
                continue
            }

            if (currentBlock.applyReplaceFade() <= 0.0) {
                iterator.remove()
            }
        }
        stateSet += contextBlock
    }

    fun expire(blockName: String, source: String) = lock.write {
        val sourceKey = ContextBlock.SourceKey(blockName, source)
        val iterator = stateSet.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().sourceKey == sourceKey) {
                iterator.remove()
            }
        }
    }

}

private data class ResolvedContextBlock(
    val block: ContextBlock,
    val domainWeight: Int,
    val activationScore: Double,
    val renderedBlock: BlockContent
)

data class ContextBlock @JvmOverloads constructor(
    val blockContent: BlockContent,
    val compactBlock: BlockContent = blockContent,
    val abstractBlock: BlockContent = blockContent,
    /**
     * 对哪些域可见
     */
    val visibleTo: Set<VisibleDomain>,

    /**
     * 新的 [blockContent] 属性与其相同时，发生的衰退步长
     */
    private val replaceFadeFactor: Double,

    /**
     * 随时间发生的衰退步长，按照分钟定义
     */
    private val timeFadeFactor: Double,

    /**
     * 触发一次激活时，发生的强化步长
     */
    private val activateFactor: Double
) {
    internal enum class Exposure {
        PRIMARY,
        SECONDARY
    }

    private enum class ProjectionLevel {
        ABSTRACT,
        COMPACT,
        FULL
    }

    /**
     * 默认活跃分数，降低至0时将在 [ContextWorkspace] 中移除该 block
     * 此外还参与到当同源 block 存在时的排序，按该分数升序排列，只影响同源 block 间的顺序
     */
    private var activationScore = 100.0
    private var lastTouchedAt = Instant.now()

    enum class VisibleDomain {
        ACTION,
        MEMORY,
        PERCEIVE,
        COGNITION,
        COMMUNICATION,
    }

    internal val sourceKey: SourceKey
        get() = SourceKey(blockContent.blockName, blockContent.source)

    fun applyTimeFade(): Double {
        refreshByElapsedTime()
        return activationScore
    }

    fun applyReplaceFade(): Double {
        refreshByElapsedTime()
        activationScore = max(0.0, activationScore - replaceFadeFactor)
        return activationScore
    }

    internal fun activate(exposure: Exposure): Double {
        refreshByElapsedTime()
        val currentLevel = currentProjectionLevel()
        val increasedScore = when (exposure) {
            Exposure.PRIMARY -> activationScore + when (currentLevel) {
                ProjectionLevel.FULL -> activateFactor
                ProjectionLevel.COMPACT -> activateFactor * 0.6
                ProjectionLevel.ABSTRACT -> activateFactor * 0.6
            }

            Exposure.SECONDARY -> activationScore + when (currentLevel) {
                ProjectionLevel.COMPACT -> activateFactor * 0.2
                ProjectionLevel.ABSTRACT -> activateFactor * 0.1
                ProjectionLevel.FULL -> 0.0
            }
        }
        activationScore = min(activationCeiling(exposure, currentLevel), increasedScore)
        return activationScore
    }

    private fun refreshByElapsedTime() {
        val now = Instant.now()
        val elapsedSeconds = Duration.between(lastTouchedAt, now).toMillis() / 1000.0
        activationScore = max(0.0, activationScore - elapsedSeconds * (timeFadeFactor / 60.0))
        lastTouchedAt = now
    }

    fun sameWith(contextBlock: ContextBlock): Boolean {
        return this.sourceKey == contextBlock.sourceKey
    }

    internal fun render(exposure: Exposure): BlockContent {
        return when (exposure) {
            Exposure.PRIMARY -> when (currentProjectionLevel()) {
                ProjectionLevel.FULL -> blockContent
                ProjectionLevel.COMPACT, ProjectionLevel.ABSTRACT -> compactBlock
            }

            Exposure.SECONDARY -> when (currentProjectionLevel()) {
                ProjectionLevel.ABSTRACT -> abstractBlock
                ProjectionLevel.COMPACT, ProjectionLevel.FULL -> compactBlock
            }
        }
    }

    fun render(): BlockContent {
        return when (currentProjectionLevel()) {
            ProjectionLevel.ABSTRACT -> abstractBlock
            ProjectionLevel.COMPACT -> compactBlock
            ProjectionLevel.FULL -> blockContent
        }
    }

    private fun currentProjectionLevel(): ProjectionLevel {
        return when {
            activationScore < ABSTRACT_TO_COMPACT_THRESHOLD -> ProjectionLevel.ABSTRACT
            activationScore < COMPACT_TO_FULL_THRESHOLD -> ProjectionLevel.COMPACT
            else -> ProjectionLevel.FULL
        }
    }

    private fun activationCeiling(exposure: Exposure, currentLevel: ProjectionLevel): Double {
        return when (exposure) {
            Exposure.PRIMARY -> when (currentLevel) {
                ProjectionLevel.ABSTRACT -> COMPACT_TO_FULL_THRESHOLD - PROJECTION_EPSILON
                ProjectionLevel.COMPACT, ProjectionLevel.FULL -> MAX_ACTIVATION_SCORE
            }

            Exposure.SECONDARY -> when (currentLevel) {
                ProjectionLevel.ABSTRACT -> ABSTRACT_TO_COMPACT_THRESHOLD - PROJECTION_EPSILON
                ProjectionLevel.COMPACT -> COMPACT_TO_FULL_THRESHOLD - PROJECTION_EPSILON
                ProjectionLevel.FULL -> MAX_ACTIVATION_SCORE
            }
        }
    }

    data class SourceKey(
        val blockName: String,
        val source: String
    )

    companion object {
        private const val MAX_ACTIVATION_SCORE = 100.0
        private const val ABSTRACT_TO_COMPACT_THRESHOLD = 30.0
        private const val COMPACT_TO_FULL_THRESHOLD = 70.0
        private const val PROJECTION_EPSILON = 0.000001
    }
}

private class AggregatedBlockContent(
    private val groupedBlocks: List<ResolvedContextBlock>
) : BlockContent(
    groupedBlocks.first().block.sourceKey.blockName,
    groupedBlocks.first().block.sourceKey.source,
    groupedBlocks.maxByOrNull { it.renderedBlock.urgency.ordinal }?.renderedBlock?.urgency ?: Urgency.NORMAL
) {

    override fun fillXml(document: Document, root: Element) {
        val snapshotIndex = groupedBlocks.withIndex()
            .maxWithOrNull(
                compareBy<IndexedValue<ResolvedContextBlock>> { it.value.activationScore }
                    .thenBy { it.index }
            )?.index ?: 0

        groupedBlocks.forEachIndexed { index, groupedBlock ->
            val tagName = if (index == snapshotIndex) "snapshot" else "history_snapshot"
            val wrapper = document.createElement(tagName)
            val renderedBlock = groupedBlock.renderedBlock
            wrapper.setAttribute("source", renderedBlock.source)
            wrapper.setAttribute("urgency", renderedBlock.urgency.name.lowercase(Locale.ROOT))
            root.appendChild(wrapper)

            val encoded = renderedBlock.encodeToXml()
            val childNodes = encoded.childNodes
            for (childIndex in 0 until childNodes.length) {
                wrapper.appendChild(document.importNode(childNodes.item(childIndex), true))
            }
        }
    }
}

abstract class BlockContent @JvmOverloads protected constructor(
    blockName: String,
    val source: String,
    val urgency: Urgency = Urgency.NORMAL
) : Block(blockName) {

    enum class Urgency {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    override fun appendRootAttributes(): Map<String, String> {
        return mapOf(
            "source" to source,
            "urgency" to urgency.name.lowercase(Locale.ROOT)
        )
    }

}

abstract class CommunicationBlockContent(
    blockName: String,
    source: String,
    val type: Projection,
) : BlockContent(
    blockName,
    source,
) {

    enum class Projection {
        SUPPLY,
        CONTEXT
    }
}
