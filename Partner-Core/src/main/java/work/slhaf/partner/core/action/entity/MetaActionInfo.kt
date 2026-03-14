package work.slhaf.partner.core.action.entity

import com.alibaba.fastjson2.JSONObject


data class MetaActionInfo(
    /**
     * 是否 IO 密集
     */
    val io: Boolean,
    /**
     * 所需的启动器/解释器
     */
    val launcher: String?,
    /**
     * 参数描述
     */
    val params: Map<String, String>,
    /**
     * 行动功能描述
     */
    val description: String,
    /**
     * 行动标签
     */
    val tags: Set<String>,
    /**
     * 前置行动依赖
     */
    val preActions: Set<String>,
    /**
     * 后置行动依赖
     */
    val postActions: Set<String>,

    /**
     * 是否严格依赖前置行动的成功执行，若为true且前置行动失败则不执行该行动，后置任务多为触发式。默认即执行。
     */
    val strictDependencies: Boolean,
    /**
     * 响应格式说明
     */
    val responseSchema: JSONObject,
)
