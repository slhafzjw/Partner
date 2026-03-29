package work.slhaf.partner.core.cognition

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ContextWorkspaceTest {

    @Test
    fun `sameWith uses blockName and source only`() {
        val left = contextBlock(
            blockName = "memory",
            source = "main",
            content = "left",
            compactContent = "left-compact",
            abstractContent = "left-abstract"
        )
        val sameSource = contextBlock(
            blockName = "memory",
            source = "main",
            content = "right"
        )
        val differentSource = contextBlock(
            blockName = "memory",
            source = "backup",
            content = "right"
        )

        assertTrue(left.sameWith(sameSource))
        assertFalse(left.sameWith(differentSource))
    }

    @Test
    fun `resolve sorts by accumulated domain weight across sources`() {
        val manager = ContextWorkspace()
        val lowWeight = contextBlock(
            blockName = "low",
            source = "source-low",
            content = "low",
            visibleTo = setOf(ContextBlock.VisibleDomain.COGNITION)
        )
        val midWeight = contextBlock(
            blockName = "mid",
            source = "source-mid",
            content = "mid",
            visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY)
        )
        val highWeight = contextBlock(
            blockName = "high",
            source = "source-high",
            content = "high",
            visibleTo = setOf(ContextBlock.VisibleDomain.ACTION, ContextBlock.VisibleDomain.MEMORY)
        )

        manager.register(highWeight)
        manager.register(lowWeight)
        manager.register(midWeight)

        val resolved = manager.resolve(
            listOf(
                ContextBlock.VisibleDomain.ACTION,
                ContextBlock.VisibleDomain.MEMORY,
                ContextBlock.VisibleDomain.COGNITION
            )
        )

        assertEquals(
            listOf("low-compact", "mid-compact", "high"),
            resolved.blocks.map { (it as TestBlockContent).content }
        )
    }

    @Test
    fun `resolve aggregates same source blocks while preserving activation ordering within the group`() {
        val manager = ContextWorkspace()
        val older = contextBlock(
            blockName = "memory",
            source = "main",
            content = "older",
            replaceFadeFactor = 20.0,
            visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY)
        )
        val newer = contextBlock(
            blockName = "memory",
            source = "main",
            content = "newer",
            replaceFadeFactor = 20.0,
            visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY)
        )
        val otherSource = contextBlock(
            blockName = "memory",
            source = "secondary",
            content = "other-source",
            visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY)
        )

        manager.register(older)
        manager.register(newer)
        manager.register(otherSource)

        val resolved = manager.resolve(listOf(ContextBlock.VisibleDomain.MEMORY))

        assertEquals(2, resolved.blocks.size)
        assertEquals("other-source", (resolved.blocks[1] as TestBlockContent).content)

        val aggregatedXml = resolved.blocks[0].encodeToXmlString()
        assertTrue(aggregatedXml.contains("<snapshot"))
        assertTrue(aggregatedXml.contains("<content>newer</content>"))
        assertTrue(aggregatedXml.contains("<history_snapshot"))
        assertTrue(aggregatedXml.contains("<content>older</content>"))
    }

    @Test
    fun `aggregated snapshots preserve rendered block root attributes`() {
        val manager = ContextWorkspace()
        manager.register(
            ContextBlock(
                blockContent = AttributedTestBlockContent("memory", "main", "older", "historic"),
                compactBlock = AttributedTestBlockContent("memory", "main", "older-compact", "historic"),
                abstractBlock = AttributedTestBlockContent("memory", "main", "older-abstract", "historic"),
                visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY),
                replaceFadeFactor = 20.0,
                timeFadeFactor = 0.0,
                activateFactor = 0.0
            )
        )
        manager.register(
            ContextBlock(
                blockContent = AttributedTestBlockContent("memory", "main", "newer", "latest"),
                compactBlock = AttributedTestBlockContent("memory", "main", "newer-compact", "latest"),
                abstractBlock = AttributedTestBlockContent("memory", "main", "newer-abstract", "latest"),
                visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY),
                replaceFadeFactor = 20.0,
                timeFadeFactor = 0.0,
                activateFactor = 0.0
            )
        )

        val resolved = manager.resolve(listOf(ContextBlock.VisibleDomain.MEMORY))

        val aggregatedXml = resolved.blocks.single().encodeToXmlString()
        assertTrue(aggregatedXml.contains("<snapshot category=\"latest\" source=\"main\" urgency=\"normal\">"))
        assertTrue(aggregatedXml.contains("<history_snapshot category=\"historic\" source=\"main\" urgency=\"normal\">"))
        assertTrue(aggregatedXml.contains("<content>newer</content>"))
        assertTrue(aggregatedXml.contains("<content>older</content>"))
    }

    @Test
    fun `register fades matching source blocks and removes zero score ones`() {
        val manager = ContextWorkspace()
        val evicted = contextBlock(
            blockName = "memory",
            source = "main",
            content = "evicted",
            replaceFadeFactor = 100.0,
            visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY)
        )
        val replacement = contextBlock(
            blockName = "memory",
            source = "main",
            content = "replacement",
            replaceFadeFactor = 100.0,
            visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY)
        )

        manager.register(evicted)
        manager.register(replacement)

        val resolved = manager.resolve(listOf(ContextBlock.VisibleDomain.MEMORY))

        assertEquals(listOf("replacement"), resolved.blocks.map { (it as TestBlockContent).content })
    }

    @Test
    fun `expire removes all matching source blocks`() {
        val manager = ContextWorkspace()
        val first = contextBlock(
            blockName = "memory",
            source = "main",
            content = "first"
        )
        val second = contextBlock(
            blockName = "memory",
            source = "main",
            content = "second"
        )
        val survivor = contextBlock(
            blockName = "memory",
            source = "secondary",
            content = "survivor"
        )

        manager.register(first)
        manager.register(second)
        manager.register(survivor)

        manager.expire("memory", "main")

        val resolved = manager.resolve(listOf(ContextBlock.VisibleDomain.MEMORY))

        assertEquals(listOf("survivor"), resolved.blocks.map { (it as TestBlockContent).content })
    }

    @Test
    fun `expire allows same source to be registered again`() {
        val manager = ContextWorkspace()
        val original = contextBlock(
            blockName = "memory",
            source = "main",
            content = "original"
        )
        val restored = contextBlock(
            blockName = "memory",
            source = "main",
            content = "restored"
        )

        manager.register(original)
        manager.expire("memory", "main")
        manager.register(restored)

        val resolved = manager.resolve(listOf(ContextBlock.VisibleDomain.MEMORY))

        assertEquals(listOf("restored"), resolved.blocks.map { (it as TestBlockContent).content })
    }

    @Test
    fun `render switches projections by activation score`() {
        val original = TestBlockContent("memory", "main", "full")
        val compact = TestBlockContent("memory", "main", "compact")
        val summary = TestBlockContent("memory", "main", "summary")

        val compactBlock = ContextBlock(
            blockContent = original,
            compactBlock = compact,
            abstractBlock = summary,
            visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY),
            replaceFadeFactor = 40.0,
            timeFadeFactor = 0.0,
            activateFactor = 0.0
        )
        compactBlock.applyReplaceFade()
        assertSame(compact, compactBlock.render())

        val summaryBlock = ContextBlock(
            blockContent = original,
            compactBlock = compact,
            abstractBlock = summary,
            visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY),
            replaceFadeFactor = 80.0,
            timeFadeFactor = 0.0,
            activateFactor = 0.0
        )
        summaryBlock.applyReplaceFade()
        assertSame(summary, summaryBlock.render())
    }

    @Test
    fun `primary exposure renders at least compact and can render full`() {
        val lowActivationBlock = contextBlock(
            blockName = "memory",
            source = "main",
            content = "full",
            compactContent = "compact",
            abstractContent = "abstract",
            replaceFadeFactor = 80.0
        )
        lowActivationBlock.applyReplaceFade()

        val highActivationBlock = contextBlock(
            blockName = "memory",
            source = "main",
            content = "full",
            compactContent = "compact",
            abstractContent = "abstract"
        )

        assertContent("compact", lowActivationBlock.render(ContextBlock.Exposure.PRIMARY))
        assertContent("full", highActivationBlock.render(ContextBlock.Exposure.PRIMARY))
    }

    @Test
    fun `secondary exposure never renders full`() {
        val block = contextBlock(
            blockName = "memory",
            source = "main",
            content = "full",
            compactContent = "compact",
            abstractContent = "abstract"
        )

        assertContent("compact", block.render(ContextBlock.Exposure.SECONDARY))
    }

    @Test
    fun `primary exposure promotes abstract only to compact in one activation`() {
        val block = contextBlock(
            blockName = "memory",
            source = "main",
            content = "full",
            compactContent = "compact",
            abstractContent = "abstract",
            replaceFadeFactor = 80.0,
            activateFactor = 50.0
        )
        block.applyReplaceFade()

        block.activate(ContextBlock.Exposure.PRIMARY)

        assertContent("compact", block.render())
        assertContent("compact", block.render(ContextBlock.Exposure.PRIMARY))
    }

    @Test
    fun `primary exposure promotes compact to full`() {
        val block = contextBlock(
            blockName = "memory",
            source = "main",
            content = "full",
            compactContent = "compact",
            abstractContent = "abstract",
            replaceFadeFactor = 40.0,
            activateFactor = 20.0
        )
        block.applyReplaceFade()

        block.activate(ContextBlock.Exposure.PRIMARY)

        assertContent("full", block.render())
    }

    @Test
    fun `secondary exposure keeps abstract within abstract tier`() {
        val block = contextBlock(
            blockName = "memory",
            source = "main",
            content = "full",
            compactContent = "compact",
            abstractContent = "abstract",
            replaceFadeFactor = 80.0,
            activateFactor = 200.0
        )
        block.applyReplaceFade()

        block.activate(ContextBlock.Exposure.SECONDARY)

        assertContent("abstract", block.render())
        assertContent("abstract", block.render(ContextBlock.Exposure.SECONDARY))
    }

    @Test
    fun `secondary exposure keeps compact within compact tier`() {
        val block = contextBlock(
            blockName = "memory",
            source = "main",
            content = "full",
            compactContent = "compact",
            abstractContent = "abstract",
            replaceFadeFactor = 40.0,
            activateFactor = 200.0
        )
        block.applyReplaceFade()

        block.activate(ContextBlock.Exposure.SECONDARY)

        assertContent("compact", block.render())
        assertContent("compact", block.render(ContextBlock.Exposure.SECONDARY))
    }

    @Test
    fun `resolve uses exposure-specific rendering for primary and secondary domains`() {
        val manager = ContextWorkspace()
        val block = contextBlock(
            blockName = "memory",
            source = "main",
            content = "full",
            compactContent = "compact",
            abstractContent = "abstract",
            visibleTo = setOf(ContextBlock.VisibleDomain.MEMORY, ContextBlock.VisibleDomain.ACTION),
            replaceFadeFactor = 80.0
        )
        block.applyReplaceFade()
        manager.register(block)

        val primaryResolved = manager.resolve(
            listOf(ContextBlock.VisibleDomain.MEMORY, ContextBlock.VisibleDomain.ACTION)
        )
        val secondaryResolved = manager.resolve(
            listOf(ContextBlock.VisibleDomain.PERCEIVE, ContextBlock.VisibleDomain.ACTION)
        )

        assertEquals(listOf("compact"), primaryResolved.blocks.map { (it as TestBlockContent).content })
        assertEquals(listOf("abstract"), secondaryResolved.blocks.map { (it as TestBlockContent).content })
    }

    @Test
    fun `aggregated blocks use rendered projection for urgency and snapshot`() {
        val manager = ContextWorkspace()
        manager.register(
            ContextBlock(
                blockContent = TestBlockContent("memory", "main", "full-critical", BlockContent.Urgency.CRITICAL),
                compactBlock = TestBlockContent("memory", "main", "compact-low", BlockContent.Urgency.LOW),
                abstractBlock = TestBlockContent("memory", "main", "abstract-low", BlockContent.Urgency.LOW),
                visibleTo = setOf(ContextBlock.VisibleDomain.ACTION),
                replaceFadeFactor = 20.0,
                timeFadeFactor = 0.0,
                activateFactor = 0.0
            )
        )
        manager.register(
            ContextBlock(
                blockContent = TestBlockContent("memory", "main", "full-normal", BlockContent.Urgency.NORMAL),
                compactBlock = TestBlockContent("memory", "main", "compact-normal", BlockContent.Urgency.NORMAL),
                abstractBlock = TestBlockContent("memory", "main", "abstract-normal", BlockContent.Urgency.NORMAL),
                visibleTo = setOf(ContextBlock.VisibleDomain.ACTION),
                replaceFadeFactor = 80.0,
                timeFadeFactor = 0.0,
                activateFactor = 0.0
            )
        )

        val resolved = manager.resolve(
            listOf(ContextBlock.VisibleDomain.PERCEIVE, ContextBlock.VisibleDomain.ACTION)
        )

        val aggregated = resolved.blocks.single()
        val xml = aggregated.encodeToXmlString()
        assertEquals(BlockContent.Urgency.NORMAL, aggregated.urgency)
        assertTrue(xml.contains("<content>compact-low</content>"))
        assertFalse(xml.contains("<content>full-critical</content>"))
    }

    private fun assertContent(expected: String, rendered: BlockContent) {
        assertEquals(expected, (rendered as TestBlockContent).content)
    }

    private fun contextBlock(
        blockName: String,
        source: String,
        content: String,
        compactContent: String = "${content}-compact",
        abstractContent: String = "${content}-abstract",
        visibleTo: Set<ContextBlock.VisibleDomain> = setOf(ContextBlock.VisibleDomain.MEMORY),
        replaceFadeFactor: Double = 10.0,
        timeFadeFactor: Double = 0.0,
        activateFactor: Double = 0.0
    ): ContextBlock {
        return ContextBlock(
            blockContent = TestBlockContent(blockName, source, content),
            compactBlock = TestBlockContent(blockName, source, compactContent),
            abstractBlock = TestBlockContent(blockName, source, abstractContent),
            visibleTo = visibleTo,
            replaceFadeFactor = replaceFadeFactor,
            timeFadeFactor = timeFadeFactor,
            activateFactor = activateFactor
        )
    }

    private open class TestBlockContent(
        blockName: String,
        source: String,
        val content: String,
        urgency: Urgency = Urgency.NORMAL
    ) : BlockContent(blockName, source, urgency) {
        override fun fillXml(document: org.w3c.dom.Document, root: org.w3c.dom.Element) {
            appendTextElement(document, root, "content", content)
        }
    }

    private class AttributedTestBlockContent(
        blockName: String,
        source: String,
        content: String,
        private val category: String,
        urgency: Urgency = Urgency.NORMAL
    ) : TestBlockContent(blockName, source, content, urgency) {
        override fun appendRootAttributes(): Map<String, String> {
            return super.appendRootAttributes() + ("category" to category)
        }
    }
}
