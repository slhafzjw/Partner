package work.slhaf.partner.module.modules.action.dispatcher.executor.entity;

import lombok.Builder;
import lombok.Data;
import work.slhaf.partner.core.action.entity.ActionData;

import java.util.Set;

@Data
@Builder
public class ActionExecutorInput {
    /**
     * 用户ID
     */
    private String userId;
    /**
     * 将执行的行动数据列表
     */
    private Set<? extends ActionData> actions;
}
