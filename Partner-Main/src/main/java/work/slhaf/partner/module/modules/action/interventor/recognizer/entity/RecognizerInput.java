package work.slhaf.partner.module.modules.action.interventor.recognizer.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.PhaserRecord;

import java.util.List;

@Data
public class RecognizerInput {
    private String input;
    private List<Message> recentMessages;
    /**
     * 当前用户对应的近两日对话缓存
     */
    private String userDialogMapStr;
    /**
     * 正在执行的行动-Phaser记录列表，在Recognizer中结合本次输入并发评估(考虑到不同行动链之间对LLM的影响)
     */
    private List<PhaserRecord> executingActions;
    private List<ActionData> preparedActions;
}
