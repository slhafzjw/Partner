package work.slhaf.partner.runtime

import org.w3c.dom.Document
import org.w3c.dom.Element
import work.slhaf.partner.common.base.Block
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext

class PartnerRunningFlowContext private constructor(
    override val source: String,
    inputs: List<InputEntry>,
    firstInputEpochMillis: Long
) : RunningFlowContext(inputs, firstInputEpochMillis) {

    companion object {

        private const val SOURCE_SELF = "self"
        private const val SOURCE_SELF_PLATFORM = "AGENT_INTERNAL"
        private const val SOURCE_SELF_NICKNAME = "PARTNER"

        private object InfoKeys {
            const val PLATFORM = "platform"
            const val NICKNAME = "nickname"
        }

        private object SourceTag {
            private const val AGENT = "[AGENT]"
            private const val USER = "[USER]"

            fun buildUserSource(userId: String): String = "$USER: $userId"
            fun buildAgentSource(): String = "$AGENT: $SOURCE_SELF"
        }

        @JvmStatic
        @JvmOverloads
        fun fromUser(userId: String, input: String, receivedAtMillis: Long = System.currentTimeMillis()) =
            PartnerRunningFlowContext(
                SourceTag.buildUserSource(userId),
                listOf(InputEntry(0L, input)),
                receivedAtMillis
            )

        @JvmStatic
        @JvmOverloads
        fun fromSelf(input: String, receivedAtMillis: Long = System.currentTimeMillis()) =
            PartnerRunningFlowContext(
                SourceTag.buildAgentSource(),
                listOf(InputEntry(0L, input)),
                receivedAtMillis
            ).apply {
                putUserInfo(InfoKeys.PLATFORM, SOURCE_SELF_PLATFORM)
                putUserInfo(InfoKeys.NICKNAME, SOURCE_SELF_NICKNAME)
            }
    }

    override fun recreate(inputs: List<InputEntry>): RunningFlowContext {
        return PartnerRunningFlowContext(
            source = source,
            inputs = inputs,
            firstInputEpochMillis = System.currentTimeMillis()
        )
    }

    fun encodeInputsBlock(): Block = object : Block("inputs") {
        override fun fillXml(document: Document, root: Element) {
            appendRepeatedElements(document, root, "input", inputs) {
                this.setAttribute("interval-to-first", it.offsetMillis.toString())
                this.textContent = it.content
            }
        }
    }

}
