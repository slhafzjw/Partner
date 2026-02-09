package work.slhaf.partner.module.modules.action.dispatcher.scheduler

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinition
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule
import work.slhaf.partner.api.agent.factory.module.annotation.Init
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule
import work.slhaf.partner.core.action.ActionCapability
import work.slhaf.partner.core.action.entity.ActionData
import work.slhaf.partner.core.action.entity.ScheduledActionData
import work.slhaf.partner.module.modules.action.dispatcher.executor.ActionExecutor
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ActionExecutorInput
import java.io.Closeable
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors
import kotlin.jvm.optionals.getOrNull

@AgentSubModule
class ActionScheduler : AgentRunningSubModule<Set<ScheduledActionData>, Void>() {

    @InjectCapability
    private lateinit var actionCapability: ActionCapability

    @InjectModule
    private lateinit var actionExecutor: ActionExecutor

    private lateinit var timeWheel: TimeWheel

    private val schedulerScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("ActionScheduler"))

    companion object {
        private val log = LoggerFactory.getLogger(ActionScheduler::class.java)
    }

    @Init
    fun init() {
        val listScheduledActions: () -> Set<ScheduledActionData> = {
            actionCapability.listActions(null, null)
                .stream()
                .filter { it is ScheduledActionData }
                .map { it as ScheduledActionData }
                .collect(Collectors.toSet())
        }

        val onTrigger: (Set<ScheduledActionData>) -> Unit = { actionExecutor.execute(ActionExecutorInput(it)) }

        timeWheel = TimeWheel(listScheduledActions, onTrigger)

        setupShutdownHook()
    }

    private fun setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            timeWheel.close()
            schedulerScope.cancel()
        })
    }

    override fun execute(scheduledActionDataSet: Set<ScheduledActionData>?): Void? {
        schedulerScope.launch {
            scheduledActionDataSet?.run {
                for (scheduledActionData in scheduledActionDataSet) {
                    actionCapability.putAction(scheduledActionData)
                    timeWheel.schedule(scheduledActionData)
                }
            }
        }
        return null
    }

    private class TimeWheel(
        val listScheduledActions: () -> Set<ScheduledActionData>,
        val onTrigger: (toTrigger: Set<ScheduledActionData>) -> Unit
    ) : Closeable {

        private val actionsGroupByHour = Array<MutableSet<ScheduledActionData>>(24) { mutableSetOf() }
        private val wheel = Array<MutableSet<ScheduledActionData>>(60 * 60) { mutableSetOf() }
        private var recordHour: Int = -1
        private var recordDay: Int = -1
        private val state = MutableStateFlow(WheelState.SLEEPING)

        private val wheelActionsLock = Mutex()
        private val timeWheelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("TimeWheel"))

        private val cronDefinition: CronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
        private val cronParser: CronParser = CronParser(cronDefinition)

        /**
         * 根据 primaryActions 建立时间轮，并只加载当天任务，同时启动 tick 线程
         */
        init {
            // 启动时间轮
            launchWheel()
        }

        suspend fun schedule(actionData: ScheduledActionData) {
            if (actionData.status != ActionData.ActionStatus.PREPARE) {
                return
            }

            checkThenExecute {
                val parseToZonedDateTime = parseToZonedDateTime(
                    actionData.scheduleType,
                    actionData.scheduleContent,
                    it
                ) ?: run {
                    logFailedStatus(actionData)
                    return@checkThenExecute
                }

                val hour = parseToZonedDateTime.hour
                actionsGroupByHour[hour].add(actionData)
                if (it.hour == hour) {
                    state.value = WheelState.ACTIVE
                }

            }
        }

        private fun launchWheel() {

            fun collectToTrigger(tick: Int, previousTick: Int, triggerHour: Int): Set<ScheduledActionData>? {
                if (tick > previousTick) {
                    val toTrigger = mutableSetOf<ScheduledActionData>()
                    for (i in (previousTick + 1)..tick) {
                        val bucket = wheel[i]
                        if (bucket.isNotEmpty()) {
                            toTrigger.addAll(bucket)
                            actionsGroupByHour[triggerHour].removeAll(bucket)
                            bucket.clear() // 避免重复触发
                        }
                    }
                    return toTrigger
                }
                return null
            }

            suspend fun CoroutineScope.wheel(launchingTime: ZonedDateTime, primaryTickAdvanceTime: Long) {
                // 计算当前距离时内下次任务的剩余时间, 秒级推进
                val launchingHour = launchingTime.hour
                var tick = launchingTime.minute * 60 + launchingTime.second
                var lastTickAdvanceTime = primaryTickAdvanceTime

                while (isActive) {
                    // tick 推进（nano -> second）
                    val current = System.nanoTime()
                    val step = ((current - lastTickAdvanceTime) / 1_000_000_000L).toInt()

                    val previousTick = tick
                    tick = (tick + step).coerceAtMost(wheel.lastIndex)
                    lastTickAdvanceTime = current

                    var shouldBreak: Boolean? = null
                    var toTrigger: Set<ScheduledActionData>? = null
                    checkThenExecute {

                        if (it.hour != launchingHour) {
                            // recordHout 已更新，此时后执行无意义，不等于启动时的 hour，则需要停止
                            shouldBreak = true
                            return@checkThenExecute
                        }

                        toTrigger = collectToTrigger(tick, previousTick, it.hour)

                        // 推进到顶时停止循环、当前时无任务时停止循环
                        if (tick >= wheel.lastIndex || actionsGroupByHour[it.hour].isEmpty()) {
                            state.value = WheelState.SLEEPING
                            shouldBreak = true
                            return@checkThenExecute
                        }

                        // 取当前 tick、推进过程中经过的 tick 对应任务，异步启动
                    }
                    toTrigger?.let { onTrigger(it) }
                    if (shouldBreak!!) {
                        break
                    }

                    // 休眠一秒
                    delay(1000)
                }
            }

            suspend fun wait(currentTime: ZonedDateTime) {
                val seconds = Duration.between(
                    currentTime, currentTime.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                ).toMillis()
                // withTimeoutOrNull 内部已处理 seconds 小于 0 的情况
                withTimeoutOrNull(seconds) {
                    state.first { it == WheelState.ACTIVE }
                }
            }

            timeWheelScope.launch {
                while (isActive) {
                    // 判断是否该步入下一小时
                    var shouldWait: Boolean? = null
                    var currentTime: ZonedDateTime? = null
                    var primaryTickAdvanceTime: Long? = null
                    checkThenExecute {
                        currentTime = it
                        shouldWait = actionsGroupByHour[it.hour].isEmpty()
                        // 由于 wheel 的启动时间可能存在延迟，而时内推进由 nanoTime 保证不会漏发，
                        // 正常的时序结束又由 tick 是否触顶、当前时是否存在额外任务触发，
                        // 而启动时无触发保障，此时一并初始化 tick 推进时间，足以应对 check 与 wheel 间的这段时间间隔
                        primaryTickAdvanceTime = System.nanoTime()
                    }

                    // 如果该时无任务则等待，插入事件可提前唤醒
                    if (shouldWait!!) {
                        // 计算距离下一小时的时间，等待
                        currentTime?.let { wait(it) }
                        continue
                    }

                    // 唤醒进行时间轮循环
                    wheel(currentTime!!, primaryTickAdvanceTime!!)
                }
            }
        }

        suspend fun checkThenExecute(then: (currentTime: ZonedDateTime) -> Unit) = wheelActionsLock.withLock {
            fun loadActions(
                now: ZonedDateTime,
                load: (latestExecutingTime: ZonedDateTime, actionData: ScheduledActionData) -> Unit,
                repair: () -> Unit
            ) {
                val runLoading = {
                    for (actionData in listScheduledActions()) {
                        val latestExecutingTime =
                            parseToZonedDateTime(
                                actionData.scheduleType,
                                actionData.scheduleContent,
                                now
                            ) ?: run {
                                logFailedStatus(actionData)
                                continue
                            }

                        load(latestExecutingTime, actionData)
                    }
                }

                repair()
                runLoading()
            }

            fun loadHourActions(currentTime: ZonedDateTime) {
                val load: (ZonedDateTime, ScheduledActionData) -> Unit = { latestExecutionTime, actionData ->
                    val secondsTime = latestExecutionTime.minute * 60 + latestExecutionTime.second
                    wheel[secondsTime].add(actionData)
                }

                val repair: () -> Unit = {
                    for (set in wheel) {
                        set.clear()
                    }
                }

                loadActions(currentTime, load, repair)
            }

            fun loadDayActions(currentTime: ZonedDateTime) {
                val load: (ZonedDateTime, ScheduledActionData) -> Unit = { latestExecutingTime, actionData ->
                    actionsGroupByHour[latestExecutingTime.hour].add(actionData)
                }

                val repair: () -> Unit = {
                    for (set in actionsGroupByHour) {
                        set.clear()
                    }
                }

                loadActions(currentTime, load, repair)
            }

            val currentTime = ZonedDateTime.now()
            val currentDay = currentTime.dayOfMonth
            val currentHour = currentTime.hour
            if (currentDay != recordDay) {
                recordDay = currentDay
                recordHour = currentHour
                loadDayActions(currentTime)
                loadHourActions(currentTime)
            } else if (currentHour != recordHour) {
                recordHour = currentHour
                loadHourActions(currentTime)
            }

            then(currentTime)
        }

        private fun parseToZonedDateTime(
            scheduleType: ScheduledActionData.ScheduleType,
            scheduleContent: String,
            now: ZonedDateTime
        ): ZonedDateTime? {
            return when (scheduleType) {
                ScheduledActionData.ScheduleType.CYCLE
                    -> {
                    val cron = try {
                        cronParser.parse(scheduleContent).validate()
                    } catch (_: Exception) {
                        return null
                    }
                    val executionTime = ExecutionTime.forCron(cron)
                    executionTime.nextExecution(now).getOrNull()
                }

                ScheduledActionData.ScheduleType.ONCE -> {
                    val executionTime = try {
                        ZonedDateTime.parse(scheduleContent)
                    } catch (_: Exception) {
                        return null
                    }
                    if (executionTime.isBefore(now) || executionTime.dayOfYear != now.dayOfYear)
                        null
                    else
                        executionTime
                }

            }

        }

        private fun logFailedStatus(actionData: ScheduledActionData) {
            log.warn(
                "行动未加载，uuid: {}, source: {}, tendency: {}, scheduleContent: {}",
                actionData.uuid,
                actionData.source,
                actionData.tendency,
                actionData.scheduleContent,
            )
        }

        override fun close() {
            timeWheelScope.cancel()
        }

        private enum class WheelState {
            ACTIVE,
            SLEEPING,
        }
    }
}