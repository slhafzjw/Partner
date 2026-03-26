package work.slhaf.partner.core.action.entity

import work.slhaf.partner.module.action.executor.entity.HistoryAction
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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

    abstract val timeout: Duration

    val timeoutMills: Long
        get() = timeout.inWholeMilliseconds

    /**
     * 行动状态
     */
    var status: Status = Status.PREPARE

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

sealed interface Schedulable {

    val scheduleType: ScheduleType
    val scheduleContent: String
    val uuid: String
    val timeout: Duration
    val timeoutMills: Long
    var enabled: Boolean

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

    override val timeout: Duration = 10.minutes

    /**
     * @param timeout 最长打断时间
     * @return 是否超时结束
     */
    fun interrupt(timeout: Int): Boolean {
        status = Status.INTERRUPTED
        val interruptAt = Instant.now().epochSecond

        while (status == Status.INTERRUPTED) {
            Thread.sleep(500);
            if (Instant.now().epochSecond - interruptAt > timeout) {
                return false
            }
        }
        return true;
    }

    fun resume() {
        status = Status.EXECUTING
    }

    fun snapshot(): ExecutableActionSnapshot {
        val schedulable = this as? Schedulable

        return ExecutableActionSnapshot(
            uuid = uuid,
            source = source,
            reason = reason,
            description = description,
            timeoutMills = timeoutMills,
            status = status,
            tendency = tendency,
            actionChainSize = actionChain.size,
            executingStage = executingStage,
            result = if (::result.isInitialized) result else null,
            history = history.mapValues { (_, value) -> value.toList() },
            additionalContext = additionalContext.mapValues { (_, value) -> value.toList() },
            scheduleType = schedulable?.scheduleType,
            scheduleContent = schedulable?.scheduleContent,
            enabled = schedulable?.enabled
        )
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
    override val scheduleContent: String,
) : ExecutableAction(), Schedulable {

    override var enabled = true
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
 * 用于计时的一次性或周期性触发或者针对某一数据源进行内容更新的行动
 */
data class StateAction @JvmOverloads constructor(
    override val source: String,
    override val reason: String,
    override val description: String,

    override val scheduleType: Schedulable.ScheduleType,
    override val scheduleContent: String,

    val trigger: Trigger,

    override var enabled: Boolean = true,
    override val timeout: Duration = 5.minutes,
) : Action(), Schedulable {

    fun snapshot(): StateActionSnapshot {
        return StateActionSnapshot(
            uuid = uuid,
            source = source,
            reason = reason,
            description = description,
            timeoutMills = timeoutMills,
            status = status,
            scheduleType = scheduleType,
            scheduleContent = scheduleContent,
            enabled = enabled,
        )
    }

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

data class ExecutableActionSnapshot(
    val uuid: String,
    val source: String,
    val reason: String,
    val description: String,
    val timeoutMills: Long,
    val status: Action.Status,

    val tendency: String,
    val actionChainSize: Int,
    val executingStage: Int,
    val result: String?,
    val history: Map<Int, List<HistoryAction>>,
    val additionalContext: Map<Int, List<String>>,

    val scheduleType: Schedulable.ScheduleType? = null,
    val scheduleContent: String? = null,
    val enabled: Boolean? = null
)

data class StateActionSnapshot(
    val uuid: String,
    val source: String,
    val reason: String,
    val description: String,
    val timeoutMills: Long,
    val status: Action.Status,
    val scheduleType: Schedulable.ScheduleType,
    val scheduleContent: String,
    val enabled: Boolean
)