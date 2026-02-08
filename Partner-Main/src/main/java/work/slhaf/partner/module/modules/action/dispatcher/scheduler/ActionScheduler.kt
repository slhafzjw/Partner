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

            val parseToZonedDateTime = parseToZonedDateTime(
                actionData.scheduleType,
                actionData.scheduleContent,
                ZonedDateTime.now()
            ) ?: run {
                logFailedStatus(actionData)
                return
            }

            checkTimeAndLoad()

            wheelActionsLock.withLock {
                val hour = parseToZonedDateTime.hour
                actionsGroupByHour[hour].add(actionData)
                if (recordHour == hour) {
                    state.value = WheelState.ACTIVE
                }
            }
        }

        private fun launchWheel() {

            suspend fun tickOnTrigger(tick: Int, previousTick: Int) {
                if (tick > previousTick) {
                    val toTrigger = linkedSetOf<ScheduledActionData>()
                    wheelActionsLock.withLock {
                        for (i in (previousTick + 1)..tick) {
                            val bucket = wheel[i]
                            if (bucket.isNotEmpty()) {
                                toTrigger.addAll(bucket)
                                actionsGroupByHour[recordHour].removeAll(bucket)
                                bucket.clear() // 避免重复触发
                            }
                        }
                    }

                    if (toTrigger.isNotEmpty()) {
                        onTrigger(toTrigger)
                    }
                }
            }

            suspend fun CoroutineScope.wheel() {
                // 计算当前距离时内下次任务的剩余时间, 秒级推进
                val now = ZonedDateTime.now()
                var tick = now.minute * 60 + now.second
                var lastTickAdvanceTime = System.nanoTime()
                while (isActive) {
                    // tick 推进（nano -> second）
                    val current = System.nanoTime()
                    val step = ((current - lastTickAdvanceTime) / 1_000_000_000L).toInt()
                    if (step <= 0) {
                        delay(50) // 避免空转
                        continue
                    }

                    val previousTick = tick
                    tick = (tick + step).coerceAtMost(wheel.lastIndex)
                    lastTickAdvanceTime = current

                    // 取当前 tick、推进过程中经过的 tick 对应任务，异步启动
                    tickOnTrigger(tick, previousTick)

                    // 推进到顶时停止循环、当前时无任务时停止循环
                    if (tick >= wheel.lastIndex || actionsGroupByHour[recordHour].isEmpty()) {
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
                withTimeoutOrNull(seconds) {
                    state.first { it == WheelState.ACTIVE }
                }
                state.value = WheelState.SLEEPING
            }

            timeWheelScope.launch {
                while (isActive) {
                    // 判断是否该步入下一小时
                    val currentTime = checkTimeAndLoad()

                    // 如果该时无任务则等待，插入事件可提前唤醒
                    if (actionsGroupByHour[recordHour].isEmpty()) {
                        // 计算距离下一小时的时间，等待
                        wait(currentTime)
                        continue
                    }

                    // 唤醒进行时间轮循环
                    wheel()
                }
            }
        }

        suspend fun checkTimeAndLoad(): ZonedDateTime {
            val currentTime = ZonedDateTime.now()
            val currentDay = currentTime.dayOfMonth
            if (currentDay != recordDay) {
                recordDay = currentDay
                recordHour = currentTime.hour
                loadDayActions()
                loadHourActions()
            } else if (currentTime.hour != recordHour) {
                recordHour = currentTime.hour
                loadHourActions()
            }
            return currentTime
        }

        fun loadActions(
            load: (latestExecutingTime: ZonedDateTime, actionData: ScheduledActionData) -> Unit,
            invalid: (before: ZonedDateTime, after: ZonedDateTime) -> Boolean,
            repair: () -> Unit
        ) {
            val runLoading = {
                val now = ZonedDateTime.now()
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

            val before = ZonedDateTime.now()
            runLoading()
            val after = ZonedDateTime.now()
            if (invalid(before, after)) {
                repair()
                runLoading()
            }
        }

        suspend fun loadHourActions() {
            val load: (ZonedDateTime, ScheduledActionData) -> Unit = { latestExecutionTime, actionData ->
                val secondsTime = latestExecutionTime.minute * 60 + latestExecutionTime.second
                wheel[secondsTime].add(actionData)
            }

            val invalid: (ZonedDateTime, ZonedDateTime) -> Boolean = { before, after ->
                before.hour != after.hour
            }

            val repair: () -> Unit = {
                for (set in wheel) {
                    set.clear()
                }
            }

            wheelActionsLock.withLock {
                loadActions(load, invalid, repair)
            }
        }

        suspend fun loadDayActions() {
            val load: (ZonedDateTime, ScheduledActionData) -> Unit = { latestExecutingTime, actionData ->
                actionsGroupByHour[latestExecutingTime.hour].add(actionData)
            }

            val invalid: (ZonedDateTime, ZonedDateTime) -> Boolean = { before, after ->
                before.dayOfMonth != after.dayOfMonth
            }

            val repair: () -> Unit = {
                for (set in actionsGroupByHour) {
                    set.clear()
                }
            }

            wheelActionsLock.withLock {
                loadActions(load, invalid, repair)
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