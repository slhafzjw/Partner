package work.slhaf.partner.core.cognition

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
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

            val activationScore = block.activate()
            if (activationScore <= 0.0) {
                iterator.remove()
                continue
            }

            activeBlocks += ResolvedContextBlock(
                block = block,
                domainWeight = matchedDomains.sumOf { domainWeights.getValue(it) },
                activationScore = activationScore,
                forceFullRender = primaryDomain in matchedDomains
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
        return if (resolved.forceFullRender) {
            resolved.block.blockContent
        } else {
            resolved.block.render()
        }
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
    val forceFullRender: Boolean
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

    fun activate(): Double {
        refreshByElapsedTime()
        activationScore = min(100.0, activationScore + activateFactor)
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

    fun render(): BlockContent {
        return when {
            activationScore < 30 -> abstractBlock
            activationScore < 70 -> compactBlock
            else -> blockContent
        }
    }

    data class SourceKey(
        val blockName: String,
        val source: String
    )
}

private class AggregatedBlockContent(
    private val groupedBlocks: List<ResolvedContextBlock>
) : BlockContent(
    groupedBlocks.first().block.sourceKey.blockName,
    groupedBlocks.first().block.sourceKey.source,
    groupedBlocks.maxByOrNull {
        if (it.forceFullRender) it.block.blockContent.urgency.ordinal else it.block.render().urgency.ordinal
    }?.let {
        if (it.forceFullRender) it.block.blockContent.urgency else it.block.render().urgency
    } ?: Urgency.NORMAL
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
            val renderedBlock = if (groupedBlock.forceFullRender) {
                groupedBlock.block.blockContent
            } else {
                groupedBlock.block.render()
            }
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
    val blockName: String,
    val source: String,
    val urgency: Urgency = Urgency.NORMAL
) {

    enum class Urgency {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    fun encodeToXml(): Element {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .newDocument()

        val root = document.createElement(blockName)
        root.setAttribute("source", source)
        root.setAttribute("urgency", urgency.name.lowercase(Locale.ROOT))
        document.appendChild(root)

        fillXml(document, root)

        return root
    }

    fun encodeToXmlString(): String {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }

        return StringWriter().use { writer ->
            transformer.transform(DOMSource(encodeToXml()), StreamResult(writer))
            writer.toString()
        }
    }

    protected abstract fun fillXml(document: Document, root: Element)

    protected fun appendTextElement(
        document: Document,
        parent: Element,
        tagName: String,
        value: Any?
    ): Element {
        val element = document.createElement(tagName)
        element.textContent = value?.toString() ?: ""
        parent.appendChild(element)
        return element
    }

    protected fun appendChildElement(
        document: Document,
        parent: Element,
        tagName: String,
        block: Element.() -> Unit = {}
    ): Element {
        val element = document.createElement(tagName)
        parent.appendChild(element)
        element.block()
        return element
    }

    protected fun appendCDataElement(
        document: Document,
        parent: Element,
        tagName: String,
        value: String?
    ): Element {
        val element = document.createElement(tagName)
        element.appendChild(document.createCDATASection(value ?: ""))
        parent.appendChild(element)
        return element
    }

    @JvmOverloads
    protected fun <T> appendListElement(
        document: Document,
        parent: Element,
        wrapperTagName: String,
        itemTagName: String,
        values: Iterable<T>,
        block: Element.(T) -> Unit = { value ->
            textContent = value?.toString() ?: ""
        }
    ): Element {
        val wrapper = document.createElement(wrapperTagName)
        parent.appendChild(wrapper)

        for (value in values) {
            val item = document.createElement(itemTagName)
            wrapper.appendChild(item)
            item.block(value)
        }

        return wrapper
    }

    @JvmOverloads
    protected fun <T> appendRepeatedElements(
        document: Document,
        parent: Element,
        itemTagName: String,
        values: Iterable<T>,
        block: Element.(T) -> Unit = { value ->
            textContent = value?.toString() ?: ""
        }
    ) {
        for (value in values) {
            val item = document.createElement(itemTagName)
            parent.appendChild(item)
            item.block(value)
        }
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
