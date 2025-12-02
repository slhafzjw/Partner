package work.slhaf.partner.core.action.entity;

import cn.hutool.json.JSONObject;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    protected Map<Integer, List<MetaAction>> actionChain;
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
        /**
         * 执行成功
         */
        SUCCESS,
        /**
         * 执行失败
         */
        FAILED,
        /**
         * 执行中
         */
        EXECUTING,
        /**
         * 暂时中断
         */
        INTERRUPTED,
        /**
         * 预备执行
         */
        PREPARE
    }
}
