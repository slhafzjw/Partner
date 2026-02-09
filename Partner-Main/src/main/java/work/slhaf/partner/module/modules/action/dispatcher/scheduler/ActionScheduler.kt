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
                    log.debug("New action to schedule: {}", scheduledActionData)
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
                log.debug("Action next execution time: {}", parseToZonedDateTime)

                val hour = parseToZonedDateTime.hour
                actionsGroupByHour[hour].add(actionData)
                log.debug("Action scheduled at {}", hour)

                if (it.hour == hour) {
                    val wheelOffset = parseToZonedDateTime.minute * 60 + parseToZonedDateTime.second
                    wheel[wheelOffset].add(actionData)
                    state.value = WheelState.ACTIVE
                    log.debug("Action scheduled at wheel offset {}", wheelOffset)
                }

            }
        }

        private fun launchWheel() {

            fun collectToTrigger(tick: Int, previousTick: Int, triggerHour: Int): Set<ScheduledActionData>? {
                if (tick > previousTick) {
                    val toTrigger = mutableSetOf<ScheduledActionData>()
                    for (i in previousTick..tick) {
                        val bucket = wheel[i]
                        if (bucket.isNotEmpty()) {
                            toTrigger.addAll(bucket)
                            val bucketUuids = bucket.asSequence().map { it.uuid }.toHashSet()
                            actionsGroupByHour[triggerHour].removeIf { it.uuid in bucketUuids }
                            bucket.clear() // 避免重复触发
                        }
                    }
                    return toTrigger
                }
                return null
            }

            suspend fun CoroutineScope.wheel(launchingTime: ZonedDateTime, primaryTickAdvanceTime: Long) {
                val launchingHour = launchingTime.hour
                var tick = launchingTime.minute * 60 + launchingTime.second

                // 让节拍器从“启动时刻的下一秒”开始（避免立即 step=0）
                var nextTickNanos = primaryTickAdvanceTime + 1_000_000_000L

                while (isActive) {
                    // 1) 计算落后多少秒：至少 1（正常推进），也可能 >1（追赶）
                    val now0 = System.nanoTime()
                    val lagNanos = now0 - nextTickNanos
                    val step = if (lagNanos < 0) 1 else (lagNanos / 1_000_000_000L).toInt() + 1

                    val previousTick = tick
                    tick = (tick + step).coerceAtMost(wheel.lastIndex)

                    // 2) 推进节拍器：按“理论秒”前进 step 次
                    nextTickNanos += step.toLong() * 1_000_000_000L

                    var shouldBreak = false
                    var toTrigger: Set<ScheduledActionData>? = null

                    checkThenExecute(false) {
                        if (it.hour != launchingHour) {
                            shouldBreak = true
                            toTrigger = collectToTrigger(wheel.lastIndex, previousTick, launchingHour)
                            log.debug(
                                "Hour changed, previousTick: {}, tick: {}, toTriggerSize: {}",
                                previousTick,
                                tick,
                                toTrigger?.size
                            )
                            return@checkThenExecute
                        }

                        toTrigger = collectToTrigger(tick, previousTick, launchingHour)

                        if (tick >= wheel.lastIndex || actionsGroupByHour[launchingHour].isEmpty()) {
                            state.value = WheelState.SLEEPING
                            shouldBreak = true
                            return@checkThenExecute
                        }
                    }

                    toTrigger?.takeIf { it.isNotEmpty() }?.let {
                        onTrigger(it)
                        log.debug("Executing action at hour {} tick {}", launchingHour, tick)
                    }

                    if (shouldBreak) {
                        log.debug("Wheel stopped at tick {}", tick)
                        break
                    }

                    // 3) 精确睡到下一次理论 tick（用最新 nanoTime）
                    val now1 = System.nanoTime()
                    val sleepNanos = nextTickNanos - now1
                    if (sleepNanos > 0) {
                        delay(sleepNanos / 1_000_000L) // 毫秒级 delay 足够；剩余 nanos 不必忙等
                    }
                }
            }

            suspend fun wait(currentTime: ZonedDateTime) {
                val nextHour = currentTime.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                val seconds = Duration.between(
                    currentTime, nextHour
                ).toMillis()
                // withTimeoutOrNull 内部已处理 seconds 小于 0 的情况
                log.debug("Start waiting {} ms at {}, target time: {}", seconds, currentTime, nextHour)
                withTimeoutOrNull(seconds) {
                    state.first { it == WheelState.ACTIVE }
                }
                log.debug("Waiting ended at {}", ZonedDateTime.now())
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

        suspend fun checkThenExecute(finallyToExecute: Boolean = true, then: (currentTime: ZonedDateTime) -> Unit) =
            wheelActionsLock.withLock {
                fun loadActions(
                    source: Set<ScheduledActionData>,
                    now: ZonedDateTime,
                    load: (latestExecutingTime: ZonedDateTime, actionData: ScheduledActionData) -> Unit,
                    repair: () -> Unit
                ) {
                    val runLoading = {
                        for (actionData in source) {
                            val nextExecutingTime =
                                parseToZonedDateTime(
                                    actionData.scheduleType,
                                    actionData.scheduleContent,
                                    now
                                ) ?: run {
                                    logFailedStatus(actionData)
                                    continue
                                }

                            load(nextExecutingTime, actionData)
                        }
                    }

                    repair()
                    runLoading()
                }

                fun loadHourActions(currentTime: ZonedDateTime) {
                    val load: (ZonedDateTime, ScheduledActionData) -> Unit = { latestExecutionTime, actionData ->
                        val secondsTime = latestExecutionTime.minute * 60 + latestExecutionTime.second
                        wheel[secondsTime].add(actionData)
                        log.debug("Action loaded to hour: {}", actionData)
                    }

                    val repair: () -> Unit = {
                        for (set in wheel) {
                            set.clear()
                        }
                    }

                    loadActions(actionsGroupByHour[currentTime.hour], currentTime, load, repair)
                }

                fun loadDayActions(currentTime: ZonedDateTime) {
                    val load: (ZonedDateTime, ScheduledActionData) -> Unit = { latestExecutingTime, actionData ->
                        actionsGroupByHour[latestExecutingTime.hour].add(actionData)
                        log.debug("Action loaded to day: {}", actionData)
                    }

                    val repair: () -> Unit = {
                        for (set in actionsGroupByHour) {
                            set.clear()
                        }
                    }

                    loadActions(listScheduledActions(), currentTime, load, repair)
                }

                fun refreshIfNeeded(now: ZonedDateTime) {
                    val d = now.dayOfMonth
                    val h = now.hour
                    if (d != recordDay) {
                        recordDay = d
                        recordHour = h
                        loadDayActions(now)
                        loadHourActions(now)
                    } else if (h != recordHour) {
                        recordHour = h
                        loadHourActions(now)
                    }
                }

                val now = ZonedDateTime.now()

                if (finallyToExecute) {
                    refreshIfNeeded(now)
                    then(now)
                } else {
                    then(now)
                    refreshIfNeeded(now)
                }
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
                    if (executionTime.plusSeconds(1).isBefore(now) || executionTime.dayOfMonth != now.dayOfMonth)
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