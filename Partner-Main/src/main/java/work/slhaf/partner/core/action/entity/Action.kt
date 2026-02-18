package work.slhaf.partner.core.action.entity

import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.HistoryAction
import java.time.ZonedDateTime
import java.util.*

sealed class Action {
    /**
     * 行动ID
     */
    val uuid: String = UUID.randomUUID().toString()

    /**
     * 行动来源
     */
    abstract val source: String

    /**
     * 行动原因
     */
    abstract val reason: String

    /**
     * 行动描述
     */
    abstract val description: String

}

sealed interface Schedulable {

    val scheduleType: ScheduleType
    val scheduleContent: String
    val uuid: String

    enum class ScheduleType {
        CYCLE,
        ONCE
    }
}

/**
 * 行动模块传递的行动数据，包含行动uuid、倾向、状态、行动链、结果、发起原因、行动描述等信息。
 */
sealed class ExecutableAction : Action() {
    /**
     * 行动倾向
     */
    abstract val tendency: String

    /**
     * 行动状态
     */
    var status: Status = Status.PREPARE

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

    enum class Status {
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
 * 计划行动数据类，继承自[Action]，扩展了[Schedulable]相关调度属性，用于标识计划类型(单次还是周期性任务)和计划内容
 */
data class SchedulableExecutableAction(
    override val tendency: String,
    override val actionChain: MutableMap<Int, MutableList<MetaAction>>,
    override val reason: String,
    override val description: String,
    override val source: String,
    override val scheduleType: Schedulable.ScheduleType,
    override val scheduleContent: String
) : ExecutableAction(), Schedulable {

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

        status = Status.PREPARE
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
data class ImmediateExecutableAction(
    override val tendency: String,
    override val actionChain: MutableMap<Int, MutableList<MetaAction>>,
    override val reason: String,
    override val description: String,
    override val source: String,
) : ExecutableAction()

/**
 * 用于计时的一次性触发或者针对某一数据源进行内容更新的行动
 */
data class StateAction(
    override val source: String,
    override val reason: String,
    override val description: String,

    override val scheduleType: Schedulable.ScheduleType,
    override val scheduleContent: String,

    val trigger: Trigger
) : Action(), Schedulable {

    sealed interface Trigger {

        fun onTrigger()

        /**
         * State 更新触发
         */
        class Update<T>(val stateSource: T, val update: (stateSource: T) -> Unit) : Trigger {
            override fun onTrigger() {
                update(stateSource)
            }
        }

        /**
         *  常规逻辑触发
         */
        class Call(val call: () -> Unit) : Trigger {
            override fun onTrigger() {
                call()
            }
        }

    }
}