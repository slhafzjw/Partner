package work.slhaf.partner.module.modules.action.dispatcher.executor.entity;

import lombok.Data;
import work.slhaf.partner.core.action.entity.ImmediateActionData;

import java.util.List;

@Data
public class ActionExecutorInput {
    /**
     * 用户ID
     */
    private String userId;
    /**
     * 即时行动数据列表
     */
    private List<ImmediateActionData> immediateActions;
}
