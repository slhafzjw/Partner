package work.slhaf.partner.core.action.entity;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 行动模块传递的行动数据，包含行动uuid、倾向、状态、行动链、结果、发起原因、行动描述等信息。
 */
@Data
public abstract class ActionData {
    protected String uuid;
    protected String tendency;
    protected ActionStatus status;
    protected LinkedHashMap<Integer, List<MetaAction>> actionChain;
    protected String result;
    protected String reason;
    protected String description;
    protected String source;

    public enum ActionStatus {
        SUCCESS, FAILED, EXECUTING, WAITING, PREPARE
    }
}
