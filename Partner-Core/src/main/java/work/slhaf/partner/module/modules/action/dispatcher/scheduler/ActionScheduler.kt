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
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule
import work.slhaf.partner.api.agent.factory.module.annotation.Init
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule
import work.slhaf.partner.core.action.ActionCapability
import work.slhaf.partner.core.action.ActionCore
import work.slhaf.partner.core.action.entity.Schedulable
import work.slhaf.partner.core.action.entity.SchedulableExecutableAction
import work.slhaf.partner.core.action.entity.StateAction
import work.slhaf.partner.module.modules.action.dispatcher.executor.ActionExecutor
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ActionExecutorInput
import java.io.Closeable
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors
import kotlin.jvm.optionals.getOrNull

class ActionScheduler : AbstractAgentModule.Sub<Set<Schedulable>, Void?>() {
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
        fun loadScheduledActions() {
            val listScheduledActions: () -> Set<SchedulableExecutableAction> = {
                actionCapability.listActions(null, null)
                    .stream()
                    .filter { it is SchedulableExecutableAction }
                    .map { it as SchedulableExecutableAction }
                    .collect(Collectors.toSet())
            }
            val onTrigger: (Set<Schedulable>) -> Unit = { schedulableSet ->
                val executableActions = mutableSetOf<SchedulableExecutableAction>()
                val stateActions = mutableSetOf<StateAction>()
                for (schedulable in schedulableSet) {
                    when (schedulable) {
                        is SchedulableExecutableAction -> executableActions.add(schedulable)
                        is StateAction -> stateActions.add(schedulable)
                    }
                }
                actionExecutor.execute(ActionExecutorInput(executableActions))
                actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL)
                    .execute { stateActions.forEach { it.trigger.onTrigger() } }
            }
            timeWheel = TimeWheel(listScheduledActions, onTrigger)
        }
        loadScheduledActions()
        setupShutdownHook()
    }

    private fun setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            timeWheel.close()
            schedulerScope.cancel()
        })
    }

    override fun execute(input: Set<Schedulable>): Void? {
        schedulerScope.launch {
            for (schedulableData in input) {
                log.debug("New data to schedule: {}", schedulableData)
                timeWheel.schedule(schedulableData)
                if (schedulableData is SchedulableExecutableAction) {
                    actionCapability.putAction(schedulableData)
                }
            }
        }
        return null
    }

    private class TimeWheel(
        val listSource: () -> Set<Schedulable>,
        val onTrigger: (toTrigger: Set<Schedulable>) -> Unit
    ) : Closeable {
        private val schedulableGroupByHour = Array<MutableSet<Schedulable>>(24) { mutableSetOf() }
        private val wheel = Array<MutableSet<Schedulable>>(60 * 60) { mutableSetOf() }
        private var recordHour: Int = -1
        private var recordDay: Int = -1
        private val state = MutableStateFlow(WheelState.SLEEPING)
        private val wheelActionsLock = Mutex()
        private val timeWheelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("TimeWheel"))
        private val cronDefinition: CronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
        private val cronParser: CronParser = CronParser(cronDefinition)

        init {
            // 启动时间轮
            wheel()
        }

        suspend fun schedule(schedulableData: Schedulable) {
            checkThenExecute {
                val parseToZonedDateTime = parseToZonedDateTime(
                    schedulableData.scheduleType,
                    schedulableData.scheduleContent,
                    it
                ) ?: run {
                    logFailedStatus(schedulableData)
                    return@checkThenExecute
                }
                log.debug("Action next execution time: {}", parseToZonedDateTime)
                val hour = parseToZonedDateTime.hour
                schedulableGroupByHour[hour].add(schedulableData)
                log.debug("Action scheduled at {}", hour)
                if (it.hour == hour) {
                    val wheelOffset = parseToZonedDateTime.minute * 60 + parseToZonedDateTime.second
                    wheel[wheelOffset].add(schedulableData)
                    state.value = WheelState.ACTIVE
                    log.debug("Action scheduled at wheel offset {}", wheelOffset)
                }
            }
        }

        private fun wheel() {
            data class WheelStepResult(
                val toTrigger: Set<Schedulable>?,
                val shouldBreak: Boolean
            )

            fun collectToTrigger(tick: Int, previousTick: Int, triggerHour: Int): Set<Schedulable>? {
                if (tick > previousTick) {
                    val toTrigger = mutableSetOf<Schedulable>()
                    for (i in previousTick..tick) {
                        val bucket = wheel[i]
                        if (bucket.isNotEmpty()) {
                            toTrigger.addAll(bucket)
                            val bucketUuids = bucket.asSequence().map { it.uuid }.toHashSet()
                            schedulableGroupByHour[triggerHour].removeIf { it.uuid in bucketUuids }
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
                    val stepResult = run {
                        var shouldBreak = false
                        var toTrigger: Set<Schedulable>? = null
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
                            if (tick >= wheel.lastIndex || schedulableGroupByHour[launchingHour].isEmpty()) {
                                state.value = WheelState.SLEEPING
                                shouldBreak = true
                            }
                        }
                        WheelStepResult(toTrigger, shouldBreak)
                    }
                    stepResult.toTrigger?.let { trigger ->
                        timeWheelScope.launch {
                            onTrigger(trigger)
                        }
                    }
                    if (stepResult.shouldBreak) {
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
                        shouldWait = schedulableGroupByHour[it.hour].isEmpty()
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
                    source: Set<Schedulable>,
                    now: ZonedDateTime,
                    load: (latestExecutingTime: ZonedDateTime, schedulableData: Schedulable) -> Unit,
                    repair: () -> Unit
                ) {
                    val runLoading = {
                        for (schedulableData in source) {
                            val nextExecutingTime =
                                parseToZonedDateTime(
                                    schedulableData.scheduleType,
                                    schedulableData.scheduleContent,
                                    now
                                ) ?: run {
                                    logFailedStatus(schedulableData)
                                    continue
                                }
                            load(nextExecutingTime, schedulableData)
                        }
                    }
                    repair()
                    runLoading()
                }

                fun loadHourActions(currentTime: ZonedDateTime) {
                    val load: (ZonedDateTime, Schedulable) -> Unit =
                        { latestExecutionTime, schedulableData ->
                            val secondsTime = latestExecutionTime.minute * 60 + latestExecutionTime.second
                            wheel[secondsTime].add(schedulableData)
                            log.debug("Action loaded to hour: {}", schedulableData)
                        }
                    val repair: () -> Unit = {
                        for (set in wheel) {
                            set.clear()
                        }
                    }
                    loadActions(schedulableGroupByHour[currentTime.hour], currentTime, load, repair)
                }

                fun loadDayActions(currentTime: ZonedDateTime) {
                    val load: (ZonedDateTime, Schedulable) -> Unit =
                        { latestExecutingTime, schedulableData ->
                            schedulableGroupByHour[latestExecutingTime.hour].add(schedulableData)
                            log.debug("Action loaded to day: {}", schedulableData)
                        }
                    val repair: () -> Unit = {
                        for (set in schedulableGroupByHour) {
                            set.clear()
                        }
                    }
                    loadActions(listSource(), currentTime, load, repair)
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
            scheduleType: Schedulable.ScheduleType,
            scheduleContent: String,
            now: ZonedDateTime
        ): ZonedDateTime? {
            return when (scheduleType) {
                Schedulable.ScheduleType.CYCLE
                    -> {
                    val cron = try {
                        cronParser.parse(scheduleContent).validate()
                    } catch (_: Exception) {
                        return null
                    }
                    val executionTime = ExecutionTime.forCron(cron)
                    executionTime.nextExecution(now).getOrNull()
                }

                Schedulable.ScheduleType.ONCE -> {
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

        private fun logFailedStatus(scheduleData: Schedulable) {
            log.warn(
                "行动未加载，scheduleType: {}, scheduleContent: {}",
                scheduleData.scheduleType,
                scheduleData.scheduleContent,
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