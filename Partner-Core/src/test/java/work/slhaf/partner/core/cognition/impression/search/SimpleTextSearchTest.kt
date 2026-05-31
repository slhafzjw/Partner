package work.slhaf.partner.core.cognition.impression.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import work.slhaf.partner.core.cognition.impression.ActiveEntity

class SimpleTextSearchTest {

    @Test
    fun `search ranks subject hit before evidence hit when both match similar terms`() {
        val search = SimpleTextSearch(TestTokenizer())
        val targetA = activeTarget("a")
        val targetB = activeTarget("b")

        search.rebuild(
            listOf(
                document("a-subject", targetA, ImpressionSearchField.SUBJECT, "城南旧书店老板", 1.0),
                document("b-evidence", targetB, ImpressionSearchField.EVIDENCE, "用户提到城南旧书店附近有一家打印店", 0.8),
            )
        )

        val hits = search.search("城南旧书店", limit = 10)

        assertEquals(listOf("a-subject", "b-evidence"), hits.map { it.document.id })
        assertTrue(hits.first().score > hits[1].score)
        assertTrue(hits.first().matchedTerms.containsAll(setOf("城南", "旧书店")))
    }

    @Test
    fun `exact phrase match can beat partial subject match`() {
        val search = SimpleTextSearch(TestTokenizer())
        val partialSubject = activeTarget("partial")
        val exactEvidence = activeTarget("exact")

        search.rebuild(
            listOf(
                document("partial-subject", partialSubject, ImpressionSearchField.SUBJECT, "工程教材", 1.0),
                document("exact-evidence", exactEvidence, ImpressionSearchField.EVIDENCE, "旧书店老板推荐过工程教材", 0.8),
            )
        )

        val hits = search.search("旧书店老板推荐过工程教材", limit = 10)

        assertEquals("exact-evidence", hits.first().document.id)
        assertTrue(hits.first().matchedTerms.containsAll(setOf("旧书店", "老板", "推荐", "工程", "教材")))
    }

    @Test
    fun `search recalls bookstore owner from generated active entity documents`() {
        val search = SimpleTextSearch(TestTokenizer())
        val bookstoreOwner = activeEntity("bookstore", "城南旧书店老板") {
            addEvidence("用户上周提到城南旧书店老板推荐过一本水利工程教材")
            addProjectedFeatures("熟悉工程类旧书" to 0.9)
        }
        val technicalPartner = activeEntity("technical", "Java 技术搭子") {
            addEvidence("用户正在讨论 Jieba 分词、SimpleTextSearch 和倒排索引")
            addProjectedFeatures("熟悉 Kotlin 与检索实现" to 0.9)
        }
        val reportRoommate = activeEntity("report", "实验报告室友") {
            addEvidence("用户帮室友整理 Vivado 进阶仿真实验报告模板和 docx 文件")
        }

        search.rebuild(
            listOf(bookstoreOwner, technicalPartner, reportRoommate)
                .flatMap(ImpressionSearchDocuments::fromActiveEntity)
        )

        val hits = search.search("旧书店老板推荐的工程教材", limit = 10)

        assertFalse(hits.isEmpty())
        assertEquals("bookstore", hits.first().document.target.id)
    }

    @Test
    fun `search recalls technical active entity from implementation terms`() {
        val search = SimpleTextSearch(TestTokenizer())
        val technicalPartner = activeEntity("technical", "Java 技术搭子") {
            addEvidence("用户正在讨论 Jieba 分词、SimpleTextSearch 和倒排索引")
            addProjectedImpressions("需要补充搜索召回测试" to 0.8)
        }
        val reportRoommate = activeEntity("report", "实验报告室友") {
            addEvidence("用户帮室友整理 Vivado 进阶仿真实验报告模板和 docx 文件")
        }

        search.rebuild(
            listOf(technicalPartner, reportRoommate)
                .flatMap(ImpressionSearchDocuments::fromActiveEntity)
        )

        val hits = search.search("jieba 分词 SimpleTextSearch 倒排索引", limit = 10)

        assertFalse(hits.isEmpty())
        assertEquals("technical", hits.first().document.target.id)
    }

    @Test
    fun `search recalls report active entity from document task terms`() {
        val search = SimpleTextSearch(TestTokenizer())
        val technicalPartner = activeEntity("technical", "Java 技术搭子") {
            addEvidence("用户正在讨论 Kotlin、Jieba 分词和 SimpleTextSearch")
        }
        val reportRoommate = activeEntity("report", "实验报告室友") {
            addEvidence("用户帮室友整理 Vivado 进阶仿真实验报告模板和 docx 文件")
        }

        search.rebuild(
            listOf(technicalPartner, reportRoommate)
                .flatMap(ImpressionSearchDocuments::fromActiveEntity)
        )

        val hits = search.search("Vivado 实验报告模板", limit = 10)

        assertFalse(hits.isEmpty())
        assertEquals("report", hits.first().document.target.id)
    }

    @Test
    fun `upsert replaces previous index terms for the same document id`() {
        val search = SimpleTextSearch(TestTokenizer())
        val target = activeTarget("entity")

        search.upsert(document("doc", target, ImpressionSearchField.EVIDENCE, "旧书店老板", 1.0))
        assertEquals(listOf("doc"), search.search("老板", limit = 10).map { it.document.id })

        search.upsert(document("doc", target, ImpressionSearchField.EVIDENCE, "实验报告模板", 1.0))

        assertTrue(search.search("老板", limit = 10).isEmpty())
        assertEquals(listOf("doc"), search.search("实验报告", limit = 10).map { it.document.id })
    }

    @Test
    fun `removeByTarget removes all documents belonging to that target`() {
        val search = SimpleTextSearch(TestTokenizer())
        val removed = activeTarget("removed")
        val kept = activeTarget("kept")

        search.rebuild(
            listOf(
                document("removed-subject", removed, ImpressionSearchField.SUBJECT, "旧书店老板", 1.0),
                document("removed-evidence", removed, ImpressionSearchField.EVIDENCE, "工程教材", 0.8),
                document("kept-evidence", kept, ImpressionSearchField.EVIDENCE, "实验报告模板", 0.8),
            )
        )

        search.removeByTarget(removed)

        val hits = search.search("实验报告", limit = 10)
        assertEquals(listOf("kept-evidence"), hits.map { it.document.id })
        assertFalse(hits.any { it.document.target == removed })
        assertTrue(search.search("旧书店", limit = 10).isEmpty())
    }

    @Test
    fun `rebuild clears previous documents and index terms`() {
        val search = SimpleTextSearch(TestTokenizer())
        val target = activeTarget("entity")

        search.rebuild(listOf(document("old", target, ImpressionSearchField.SUBJECT, "旧书店老板", 1.0)))
        assertEquals(listOf("old"), search.search("老板", limit = 10).map { it.document.id })

        search.rebuild(listOf(document("new", target, ImpressionSearchField.SUBJECT, "实验报告模板", 1.0)))

        assertTrue(search.search("老板", limit = 10).isEmpty())
        assertEquals(listOf("new"), search.search("实验报告", limit = 10).map { it.document.id })
    }

    @Test
    fun `blank unmatched and zero limit queries return empty hits`() {
        val search = SimpleTextSearch(TestTokenizer())
        val target = activeTarget("entity")
        search.rebuild(listOf(document("doc", target, ImpressionSearchField.SUBJECT, "旧书店老板", 1.0)))

        assertTrue(search.search("   ", limit = 10).isEmpty())
        assertTrue(search.search("完全不存在", limit = 10).isEmpty())
        assertTrue(search.search("旧书店", limit = 0).isEmpty())
    }

    private fun activeTarget(id: String) =
        ImpressionSearchTarget(ImpressionSearchTarget.Type.ACTIVE_ENTITY, id)

    private fun activeEntity(
        runtimeId: String,
        subject: String,
        configure: ActiveEntity.() -> Unit,
    ): ActiveEntity = ActiveEntity(runtimeId = runtimeId).apply {
        updateSubject(subject)
        configure()
    }

    private fun document(
        id: String,
        target: ImpressionSearchTarget,
        field: ImpressionSearchField,
        text: String,
        weight: Double,
    ) = ImpressionSearchDocument(
        id = id,
        target = target,
        field = field,
        text = text,
        weight = weight,
    )

    private class TestTokenizer : ImpressionTokenizer {
        private val dictionary = listOf(
            "城南", "旧书店", "老板", "推荐", "工程", "教材", "水利", "熟悉", "旧书",
            "java", "kotlin", "jieba", "分词", "simpletextsearch", "倒排", "索引", "检索", "测试", "召回",
            "vivado", "实验报告", "实验", "报告", "模板", "docx", "室友", "整理", "文件"
        )
        private val alphaNumericRegex = Regex("[a-z0-9]+(?:[-_./][a-z0-9]+)*")

        override fun tokenize(text: String): Set<String> {
            val normalized = text.lowercase().trim()
            if (normalized.isBlank()) {
                return emptySet()
            }

            return buildSet {
                dictionary.filterTo(this) { normalized.contains(it) }
                alphaNumericRegex.findAll(normalized).mapTo(this) { it.value }
            }
        }
    }
}
