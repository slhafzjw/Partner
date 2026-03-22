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
            listOf("low", "mid", "high"),
            resolved.map { (it as TestBlockContent).content }
        )
    }

    @Test
    fun `resolve uses activation score only within same source`() {
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

        assertEquals(
            listOf("older", "newer", "other-source"),
            resolved.map { (it as TestBlockContent).content }
        )
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

        assertEquals(listOf("replacement"), resolved.map { (it as TestBlockContent).content })
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

        assertEquals(listOf("survivor"), resolved.map { (it as TestBlockContent).content })
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

        assertEquals(listOf("restored"), resolved.map { (it as TestBlockContent).content })
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

    private class TestBlockContent(
        blockName: String,
        source: String,
        val content: String
    ) : BlockContent(blockName, source) {
        override fun fillXml(document: org.w3c.dom.Document, root: org.w3c.dom.Element) {
            appendTextElement(document, root, "content", content)
        }
    }
}
