package work.slhaf.partner.module.modules.action.dispatcher.scheduler

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinition
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    companion object {
        private val log = LoggerFactory.getLogger(ActionScheduler::class.java)
    }

    @Init
    fun init() {
        val actions = actionCapability.listActions(null, null)
            .stream()
            .filter { actionData -> actionData is ScheduledActionData }
            .map { actionData -> actionData as ScheduledActionData }
            .collect(Collectors.toSet())
        timeWheel = TimeWheel(actions) { actionDataSet ->
            actionExecutor.execute(ActionExecutorInput(actionDataSet))
        }
    }

    override fun execute(scheduledActionDataSet: Set<ScheduledActionData>?): Void? {
        scheduledActionDataSet?.run {
            for (scheduledActionData in scheduledActionDataSet) {
                actionCapability.putAction(scheduledActionData)
                timeWheel.schedule(scheduledActionData)
            }
        }
        return null
    }

    private class TimeWheel(
        val primaryActions: Set<ScheduledActionData>,
        val onTrigger: (Set<ScheduledActionData>) -> Unit
    ) {

        private val actionsGroupByHour = Array<MutableSet<ScheduledActionData>>(24) { mutableSetOf() }
        private val wheel = Array<MutableSet<ScheduledActionData>>(60 * 60) { mutableSetOf() }
        private var currentHour: Int = 0
        private val state = MutableStateFlow(WheelState.SLEEPING)

        private val cronDefinition: CronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
        private val cronParser: CronParser = CronParser(cronDefinition)

        /**
         * 根据 primaryActions 建立时间轮，并只加载当天任务，同时启动 tick 线程
         */
        init {
            // 加载 primaryActions 进 actionsGroupByHour
            loadDayActions()
            // 依据当前时间移动至合适的 hour 并启动时间轮
            launchWheel()
        }

        fun schedule(actionData: ScheduledActionData) {
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

            val hour = parseToZonedDateTime.hour
            actionsGroupByHour[hour].add(actionData)
            if (currentHour == hour) {
                state.value = WheelState.ACTIVE
            }
        }

        private fun launchWheel() {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("TimeWheel"))
            fun tickOnTrigger(tick: Int, previousTick: Int) {
                if (tick > previousTick) {
                    val toTrigger = linkedSetOf<ScheduledActionData>()
                    for (i in (previousTick + 1)..tick) {
                        val bucket = wheel[i]
                        if (bucket.isNotEmpty()) {
                            toTrigger.addAll(bucket)
                            actionsGroupByHour[currentHour].removeAll(bucket)
                            bucket.clear() // 避免重复触发
                        }
                    }

                    if (toTrigger.isNotEmpty()) {
                        onTrigger(toTrigger)
                    }
                }
            }

            scope.launch {
                while (isActive) {
                    // 判断是否该步入下一小时
                    val actionsLoadingTime = loadHourActions()
                    currentHour = actionsLoadingTime.hour

                    // 如果该时无任务则等待，插入事件可提前唤醒
                    if (actionsGroupByHour[currentHour].isEmpty()) {
                        // 计算距离下一小时的时间，等待
                        val seconds = java.time.Duration.between(
                            actionsLoadingTime, actionsLoadingTime.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                        ).toMillis()
                        withTimeout(seconds) {
                            state.first { it == WheelState.ACTIVE }
                        }
                        state.value = WheelState.SLEEPING
                        continue
                    }

                    // 唤醒进行时间轮循环
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
                        if (tick >= wheel.lastIndex || actionsGroupByHour[currentHour].isEmpty()) {
                            break
                        }

                        // 休眠一秒
                        delay(1000)
                    }
                }

            }
        }

        private fun loadHourActions(): ZonedDateTime {
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

            return loadActions(load, invalid, repair)
        }

        private fun loadDayActions() {
            val load: (ZonedDateTime, ScheduledActionData) -> Unit = { latestExecutingTime, actionData ->
                actionsGroupByHour[latestExecutingTime.hour].add(actionData)
            }

            val invalid: (ZonedDateTime, ZonedDateTime) -> Boolean = { before, after ->
                before.dayOfYear != after.dayOfYear
            }

            val repair: () -> Unit = {
                for (set in actionsGroupByHour) {
                    set.clear()
                }
            }

            loadActions(load, invalid, repair)

        }

        private fun loadActions(
            load: (latestExecutingTime: ZonedDateTime, actionData: ScheduledActionData) -> Unit,
            invalid: (before: ZonedDateTime, after: ZonedDateTime) -> Boolean,
            repair: () -> Unit
        ): ZonedDateTime {
            val runLoading = {
                val now = ZonedDateTime.now()
                for (actionData in primaryActions) {
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
            return after

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

        private enum class WheelState {
            ACTIVE,
            SLEEPING,
        }
    }
}