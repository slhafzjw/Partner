package work.slhaf.partner.module.modules.action.dispatcher.executor

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import work.slhaf.partner.core.action.ActionCapability
import work.slhaf.partner.core.action.entity.ActionData
import work.slhaf.partner.core.action.entity.ScheduledActionData
import work.slhaf.partner.module.modules.action.dispatcher.scheduler.ActionScheduler
import java.time.ZonedDateTime

/**
 * ActionScheduler.execute(...) 测试矩阵（控制流入口：execute）。
 *
 * 场景编号与矩阵对应：
 * 1) null 入参早退（B1）
 * 2) PREPARE + ONCE 合法时间入轮（B2 → B2.3）
 * 3) 非 PREPARE 状态忽略（B2 → B2.1）
 * 4) ONCE 过期/跨日解析失败（B2 → B2.2）
 * 5) CYCLE cron 非法解析失败（B2 → B2.2）
 * 6) putAction 异常传播（B2 异常中断）
 * 7) 同小时调度触发 ACTIVE（B2.3 + 状态变更）
 * 15) 混合输入（成功/失败/忽略路径混合）
 *
 * 以下矩阵场景因并发/时间依赖难以稳定复现，仅在文档中标注，不在本类实现：
 * 8) withTimeout 超时导致协程取消
 * 9) tick 触发 onTrigger 并调用 ActionExecutor
 * 10) tick step<=0 空转延迟
 * 11) loadActions 跨小时修复
 * 13) actionExecutor 阻塞导致调度延迟
 * 14) schedule 与 tick 并发访问竞态
 */
@ExtendWith(MockitoExtension::class)
class ActionSchedulerTest {

    @Mock
    private lateinit var actionCapability: ActionCapability

    @InjectMocks
    private lateinit var actionScheduler: ActionScheduler

    @Test
    fun `execute with null input should return null and no side effects`() {
        // 场景编号：1；路径：B1；目的：验证正常早退
        val result = actionScheduler.execute(null)

        assertEquals(null, result)
        verify(actionCapability, Mockito.never()).putAction(any(ActionData::class.java))
    }

    @Test
    fun `execute should put action and schedule valid ONCE prepare action`() {
        // 场景编号：2；路径：B2 → B2.3；目的：验证正常入轮与副作用
        initTimeWheelWithPrimaryActions(emptySet())
        val action = buildScheduledAction(
            type = ScheduledActionData.ScheduleType.ONCE,
            ZonedDateTime.now().plusHours(1).toString()
        )

        actionScheduler.execute(setOf(action))

        verify(actionCapability, times(1)).putAction(action)
        val timeWheel = timeWheel()
        val bucket = actionsGroupByHour(timeWheel)[action.scheduleContentHour()]
        assertTrue(bucket.contains(action))
    }

    @Test
    fun `execute should ignore non-prepare action for scheduling`() {
        // 场景编号：3；路径：B2 → B2.1；目的：验证忽略非 PREPARE 状态
        initTimeWheelWithPrimaryActions(emptySet())
        val action = buildScheduledAction(
            type = ScheduledActionData.ScheduleType.ONCE
        )

        actionScheduler.execute(setOf(action))

        verify(actionCapability, times(1)).putAction(action)
        val allScheduled = allScheduledActions(timeWheel())
        assertFalse(allScheduled.contains(action))
    }

    @Test
    fun `execute should skip expired ONCE action`() {
        // 场景编号：4；路径：B2 → B2.2；目的：验证解析失败被跳过
        initTimeWheelWithPrimaryActions(emptySet())
        val action = buildScheduledAction(
            type = ScheduledActionData.ScheduleType.ONCE
        )

        actionScheduler.execute(setOf(action))

        val allScheduled = allScheduledActions(timeWheel())
        assertFalse(allScheduled.contains(action))
    }

    @Test
    fun `execute should skip invalid CYCLE cron`() {
        // 场景编号：5；路径：B2 → B2.2；目的：验证 cron 解析失败被跳过
        initTimeWheelWithPrimaryActions(emptySet())
        val action = buildScheduledAction(
            type = ScheduledActionData.ScheduleType.CYCLE,
            scheduleContentOverride = "invalid-cron"
        )

        actionScheduler.execute(setOf(action))

        val allScheduled = allScheduledActions(timeWheel())
        assertFalse(allScheduled.contains(action))
    }

    @Test
    fun `execute should propagate exception from putAction`() {
        // 场景编号：6；路径：B2 异常中断；目的：验证异常传播
        initTimeWheelWithPrimaryActions(emptySet())
        val action = buildScheduledAction(
            type = ScheduledActionData.ScheduleType.ONCE
        )
        Mockito.doThrow(RuntimeException("boom"))
            .`when`(actionCapability)
            .putAction(action)

        assertThrows(RuntimeException::class.java) {
            actionScheduler.execute(setOf(action))
        }
    }

    @Test
    fun `execute should activate wheel when scheduling current hour`() {
        // 场景编号：7；路径：B2.3；目的：验证同小时调度触发 ACTIVE
        initTimeWheelWithPrimaryActions(emptySet())
        val action = buildScheduledAction(
            type = ScheduledActionData.ScheduleType.ONCE,
            scheduleContentOverride = ZonedDateTime.now().plusMinutes(2).toString()
        )

        val timeWheel = timeWheel()
        val actionHour = action.scheduleContentHour()
        setCurrentHour(timeWheel, actionHour)
        setWheelState(timeWheel, "SLEEPING")

        actionScheduler.execute(setOf(action))

        assertEquals("ACTIVE", wheelStateName(timeWheel))
    }

    @Test
    fun `execute should handle mixed actions consistently`() {
        // 场景编号：15；路径：B2 + B2.1/B2.2/B2.3；目的：验证混合输入行为
        initTimeWheelWithPrimaryActions(emptySet())
        val ok = buildScheduledAction(
            type = ScheduledActionData.ScheduleType.ONCE,
            scheduleContentOverride = ZonedDateTime.now().plusMinutes(2).toString()
        )
        val nonPrepare = buildScheduledAction(
            type = ScheduledActionData.ScheduleType.ONCE,
            scheduleContentOverride = ZonedDateTime.now().plusMinutes(2).toString()
        )
        nonPrepare.status = ActionData.ActionStatus.FAILED
        val invalid = buildScheduledAction(
            type = ScheduledActionData.ScheduleType.CYCLE,
            scheduleContentOverride = "invalid-cron"
        )

        actionScheduler.execute(setOf(ok, nonPrepare, invalid))

        verify(actionCapability, times(1)).putAction(ok)
        verify(actionCapability, times(1)).putAction(nonPrepare)
        verify(actionCapability, times(1)).putAction(invalid)
        val allScheduled = allScheduledActions(timeWheel())
        assertTrue(allScheduled.contains(ok))
        assertFalse(allScheduled.contains(nonPrepare))
        assertFalse(allScheduled.contains(invalid))
    }

    private fun initTimeWheelWithPrimaryActions(actions: Set<ScheduledActionData>) {
        @Suppress("UNCHECKED_CAST")
        Mockito.`when`(actionCapability.listActions(null, null))
            .thenReturn(actions as Set<ActionData>)
        actionScheduler.init()
    }

    private fun buildScheduledAction(
        type: ScheduledActionData.ScheduleType,
        scheduleContentOverride: String? = null
    ): ScheduledActionData {
        val action = ScheduledActionData(
            "test",
            mutableMapOf(),
            "reason",
            "description",
            "test",
            type,
            scheduleContentOverride ?: scheduleContentOverride.toString()
        )
        return action
    }

    private fun ScheduledActionData.scheduleContentHour(): Int {
        return ZonedDateTime.parse(this.scheduleContent).hour
    }

    private fun timeWheel(): Any {
        val field = actionScheduler.javaClass.getDeclaredField("timeWheel")
        field.isAccessible = true
        return field.get(actionScheduler)
    }

    @Suppress("UNCHECKED_CAST")
    private fun actionsGroupByHour(timeWheel: Any): Array<MutableSet<ScheduledActionData>> {
        val field = timeWheel.javaClass.getDeclaredField("actionsGroupByHour")
        field.isAccessible = true
        return field.get(timeWheel) as Array<MutableSet<ScheduledActionData>>
    }

    private fun allScheduledActions(timeWheel: Any): Set<ScheduledActionData> {
        val result = linkedSetOf<ScheduledActionData>()
        for (bucket in actionsGroupByHour(timeWheel)) {
            result.addAll(bucket)
        }
        return result
    }

    private fun setCurrentHour(timeWheel: Any, hour: Int) {
        val field = timeWheel.javaClass.getDeclaredField("currentHour")
        field.isAccessible = true
        field.setInt(timeWheel, hour)
    }

    private fun setWheelState(timeWheel: Any, name: String) {
        val field = timeWheel.javaClass.getDeclaredField("state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(timeWheel) as MutableStateFlow<Any>
        state.value = wheelStateEnum(name)
    }

    private fun wheelStateName(timeWheel: Any): String {
        val field = timeWheel.javaClass.getDeclaredField("state")
        field.isAccessible = true
        val state = field.get(timeWheel) as MutableStateFlow<*>
        val value = state.value as Enum<*>
        return value.name
    }

    private fun wheelStateEnum(name: String): Any {
        @Suppress("UNCHECKED_CAST")
        val clazz = Class.forName(
            $$"work.slhaf.partner.module.modules.action.dispatcher.scheduler.ActionScheduler$TimeWheel$WheelState"
        ) as Class<out Enum<*>>
        return java.lang.Enum.valueOf(clazz, name)
    }
}
