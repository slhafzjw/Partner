package work.slhaf.partner.module

import org.w3c.dom.Document
import org.w3c.dom.Element
import work.slhaf.partner.common.base.Block
import work.slhaf.partner.core.cognition.CommunicationBlockContent
import work.slhaf.partner.core.cognition.ContextBlock
import work.slhaf.partner.framework.agent.model.pojo.Message

abstract class TaskBlock @JvmOverloads constructor(
    blockName: String = "task_input"
) : Block(blockName) {

    fun encodeToMessage(): Message {
        return Message(Message.Character.USER, encodeToXmlString())
    }

}

abstract class StateHintContent protected constructor(
    source: String,
    val stateMsg: String
) : CommunicationBlockContent("new_state", source, Urgency.NORMAL, Projection.SUPPLY) {

    override fun fillXml(document: Document, root: Element) {
        appendTextElement(document, root, "state_msg", stateMsg)
        appendChildElement(document, root, "state_content") {
            fillStateContent(document, this@appendChildElement)
        }
    }

    abstract fun fillStateContent(document: Document, stateElement: Element)

    private fun toContextBlock(): ContextBlock {
        return ContextBlock(
            blockContent = this,
            focusedOn = setOf(ContextBlock.FocusedDomain.COGNITION),
            replaceFadeFactor = 60.0,
            timeFadeFactor = 100.0,
            activateFactor = 0.0
        )
    }

    companion object {
        @JvmStatic
        fun createBlock(block: StateHintContent): ContextBlock {
            return block.toContextBlock()
        }
    }
}