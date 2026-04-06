package work.slhaf.partner.module

import work.slhaf.partner.common.base.Block
import work.slhaf.partner.framework.agent.model.pojo.Message

abstract class TaskBlock @JvmOverloads constructor(
    blockName: String = "task_input"
) : Block(blockName) {

    fun encodeToMessage(): Message {
        return Message(Message.Character.USER, encodeToXmlString())
    }

}