package work.slhaf.partner.module

import work.slhaf.partner.api.chat.pojo.Message
import work.slhaf.partner.common.base.Block

abstract class TaskBlock @JvmOverloads constructor(
    blockName: String = "task_input"
) : Block(blockName) {

    fun encodeToMessage(): Message {
        return Message(Message.Character.USER, encodeToXmlString())
    }

}