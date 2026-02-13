package work.slhaf.partner.core.action.entity

import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.HistoryAction
import java.time.ZonedDateTime
import java.util.*

/**
 * 行动模块传递的行动数据，包含行动uuid、倾向、状态、行动链、结果、发起原因、行动描述等信息。
 */
sealed class ActionData {
    /**
     * 行动ID
     */
    val uuid: String = UUID.randomUUID().toString()

    /**
     * 行动倾向
     */
    abstract val tendency: String

    /**
     * 行动状态
     */
    var status: ActionStatus = ActionStatus.PREPARE

    /**
     * 行动链
     */
    abstract val actionChain: MutableMap<Int, MutableList<MetaAction>>

    /**
     * 行动阶段（当前阶段）
     */
    var executingStage: Int = 0

    /**
     * 行动结果
     */
    lateinit var result: String

    val history: MutableMap<Int, MutableList<HistoryAction>> = mutableMapOf()

    /**
     * 修复上下文
     */
    val additionalContext: MutableMap<Int, MutableList<String>> = mutableMapOf()

    /**
     * 行动原因
     */
    abstract val reason: String

    /**
     * 行动描述
     */
    abstract val description: String

    /**
     * 行动来源
     */
    abstract val source: String

    enum class ActionStatus {
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

/**
 * 计划行动数据类，继承自{@link ActionData}，扩展了属性{@link ScheduledActionData#type}和{@link ScheduledActionData#scheduleContent}，用于标识计划类型(单次还是周期性任务)和计划内容
 */
data class ScheduledActionData(
    override val tendency: String,
    override val actionChain: MutableMap<Int, MutableList<MetaAction>>,
    override val reason: String,
    override val description: String,
    override val source: String,
    val scheduleType: ScheduleType,
    val scheduleContent: String,
) : ActionData() {

    val scheduleHistories = ArrayList<ScheduleHistory>()

    fun recordAndReset() {
        val newHistory = ScheduleHistory(ZonedDateTime.now(), result, history.toMap())
        scheduleHistories.add(newHistory)

        additionalContext.clear()
        executingStage = 0
        for (entry in actionChain) {
            for (action in entry.value) {
                action.params.clear()
                action.result.reset()
            }
        }

        status = ActionStatus.PREPARE
    }

    enum class ScheduleType {
        CYCLE,
        ONCE
    }

    data class ScheduleHistory(
        val endTime: ZonedDateTime,
        val result: String,
        val history: Map<Int, List<HistoryAction>>
    )
}

/**
 * 即时行动数据类
 */
data class ImmediateActionData(
    override val tendency: String,
    override val actionChain: MutableMap<Int, MutableList<MetaAction>>,
    override val reason: String,
    override val description: String,
    override val source: String,
) : ActionData()

// TODO 考虑是否新增 SYNC、ASYNC 分类，用于适应后台与非后台行动，但是否引入则需要权衡，分析原因和引入后果、是否值得
