package work.slhaf.partner.core.action.entity;

import cn.hutool.json.JSONObject;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 行动模块传递的行动数据，包含行动uuid、倾向、状态、行动链、结果、发起原因、行动描述等信息。
 */
@Data
public abstract class ActionData {
    /**
     * 行动ID
     */
    protected String uuid;
    /**
     * 行动倾向
     */
    protected String tendency;

    /**
     * 行动状态
     */
    protected ActionStatus status;
    /**
     * 行动链
     */
    protected LinkedHashMap<Integer, List<MetaAction>> actionChain = new LinkedHashMap<>();
    /**
     * 行动阶段（当前阶段）
     */
    protected int executingStage;
    /**
     * 行动结果
     */
    protected String result;
    protected List<JSONObject> history = new ArrayList<>();

    /**
     * 行动原因
     */
    protected String reason;
    /**
     * 行动描述
     */
    protected String description;
    /**
     * 行动来源
     */
    protected String source;

    public enum ActionStatus {
        SUCCESS, FAILED, EXECUTING, PREPARE
    }
}
