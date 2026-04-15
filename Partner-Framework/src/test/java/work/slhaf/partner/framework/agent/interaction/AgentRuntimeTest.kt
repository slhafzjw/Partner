package work.slhaf.partner.framework.agent.interaction

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.framework.agent.factory.context.AgentContext
import work.slhaf.partner.framework.agent.factory.context.ModuleContextData
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AgentRuntimeTest {

    @BeforeEach
    fun setUp() {
        resetAgentRuntime()
        clearModules()
    }

    @AfterEach
    fun tearDown() {
        resetAgentRuntime()
        clearModules()
    }

    @Test
    fun `agent runtime keeps source queue in first arrival order`() {
        val recorder = RecordingModule(order = 1, expectedExecutions = 2)
        registerModule("queue-recorder", recorder)

        AgentRuntime.submit(TestRunningFlowContext.of("source-a", "alpha"))
        AgentRuntime.submit(TestRunningFlowContext.of("source-b", "beta"))

        assertTrue(recorder.latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("source-a", "source-b"), recorder.sources)
    }

    @Test
    fun `agent runtime waits debounce before first execution`() {
        setPrivateField("debounceWindow", 200)
        val recorder = RecordingModule(order = 1, expectedExecutions = 1)
        registerModule("debounce-recorder", recorder)

        AgentRuntime.submit(TestRunningFlowContext.of("source-a", "alpha"))

        assertFalse(recorder.latch.await(100, TimeUnit.MILLISECONDS))
        assertTrue(recorder.latch.await(500, TimeUnit.MILLISECONDS))
        assertEquals(listOf(1), recorder.inputSizes)
    }

    @Test
    fun `agent runtime resets debounce when same source receives new input`() {
        setPrivateField("debounceWindow", 200)
        val recorder = RecordingModule(order = 1, expectedExecutions = 1)
        registerModule("debounce-merge-recorder", recorder)

        AgentRuntime.submit(TestRunningFlowContext.of("source-a", "first", 1_000L))
        Thread.sleep(100)
        AgentRuntime.submit(TestRunningFlowContext.of("source-a", "second", 1_300L))

        assertFalse(recorder.latch.await(120, TimeUnit.MILLISECONDS))
        assertTrue(recorder.latch.await(500, TimeUnit.MILLISECONDS))
        assertEquals(listOf(2), recorder.inputSizes)
        assertEquals(listOf("first\nsecond"), recorder.historyInputs)
    }

    @Test
    fun `agent runtime debounce keeps queue head exclusive`() {
        setPrivateField("debounceWindow", 150)
        val recorder = RecordingModule(order = 1, expectedExecutions = 2)
        registerModule("debounce-queue-recorder", recorder)

        AgentRuntime.submit(TestRunningFlowContext.of("source-a", "alpha"))
        Thread.sleep(50)
        AgentRuntime.submit(TestRunningFlowContext.of("source-b", "beta"))

        assertFalse(recorder.latch.await(100, TimeUnit.MILLISECONDS))
        assertTrue(recorder.latch.await(800, TimeUnit.MILLISECONDS))
        assertEquals(listOf("source-a", "source-b"), recorder.sources)
    }

    @Test
    fun `agent runtime interrupts current source and reruns from chain head with merged context`() {
        setPrivateField("debounceWindow", 150)
        val blocking = BlockingModule()
        val finalizer = RecordingModule(order = 2, expectedExecutions = 1)
        registerModule("blocking-module", blocking)
        registerModule("finalizer-module", finalizer)

        AgentRuntime.submit(TestRunningFlowContext.of("source-a", "first", 1_000L))
        assertTrue(blocking.firstExecutionStarted.await(2, TimeUnit.SECONDS))

        AgentRuntime.submit(TestRunningFlowContext.of("source-a", "second", 1_300L))
        blocking.releaseFirstExecution.countDown()

        assertFalse(blocking.secondExecutionStarted.await(100, TimeUnit.MILLISECONDS))
        assertTrue(finalizer.latch.await(5, TimeUnit.SECONDS))
        waitUntil { blocking.seenInputSizes.size >= 2 }

        assertEquals(listOf(1, 2), blocking.seenInputSizes)
        assertEquals(listOf(2), finalizer.inputSizes)
        assertEquals(listOf("first\nsecond"), finalizer.historyInputs)
    }

    private fun registerModule(name: String, module: AbstractAgentModule.Running<*>) {
        @Suppress("UNCHECKED_CAST")
        AgentContext.addModule(
            name,
            ModuleContextData.Running(
                module.javaClass,
                module,
                ZonedDateTime.now(),
                null,
                module.order()
            ) as ModuleContextData<AbstractAgentModule>
        )
    }

    private fun clearModules() {
        @Suppress("UNCHECKED_CAST")
        val modules = AgentContext.modules as MutableMap<String, ModuleContextData<AbstractAgentModule>>
        modules.clear()
    }

    private fun resetAgentRuntime() {
        setPrivateField("runningModules", emptyMap<Int, List<AbstractAgentModule.Running<RunningFlowContext>>>())
        setPrivateField("maskedModules", emptySet<String>())
        setPrivateField("debounceWindow", 0)
        setPrivateField("currentExecutingSource", null)
        setPrivateField("currentExecutingContext", null)
        getPrivateMutableMap<String, RunningFlowContext>("latestContextsBySource").clear()
        getPrivateMutableMap<String, Long>("sourceVersions").clear()
        getPrivateDeque<String>("sourceQueue").clear()
    }

    private fun waitUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(20L)
        }
        error("Condition was not satisfied within $timeoutMillis ms")
    }

    private fun setPrivateField(fieldName: String, value: Any?) {
        val field = AgentRuntime::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(AgentRuntime, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <K, V> getPrivateMutableMap(fieldName: String): MutableMap<K, V> {
        val field = AgentRuntime::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(AgentRuntime) as MutableMap<K, V>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateDeque(fieldName: String): java.util.ArrayDeque<T> {
        val field = AgentRuntime::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(AgentRuntime) as java.util.ArrayDeque<T>
    }

    private class RecordingModule(
        private val order: Int,
        expectedExecutions: Int
    ) : AbstractAgentModule.Running<TestRunningFlowContext>() {
        val sources = CopyOnWriteArrayList<String>()
        val inputSizes = CopyOnWriteArrayList<Int>()
        val historyInputs = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(expectedExecutions)

        init {
            moduleName = "recording-$order"
        }

        override fun doExecute(context: TestRunningFlowContext) {
            sources.add(context.source)
            inputSizes.add(context.inputs.size)
            historyInputs.add(context.input)
            latch.countDown()
        }

        override fun order(): Int = order
    }

    private class BlockingModule : AbstractAgentModule.Running<TestRunningFlowContext>() {
        val seenInputSizes = CopyOnWriteArrayList<Int>()
        val firstExecutionStarted = CountDownLatch(1)
        val secondExecutionStarted = CountDownLatch(1)
        val releaseFirstExecution = CountDownLatch(1)
        private val invocationCount = AtomicInteger(0)

        init {
            moduleName = "blocking"
        }

        override fun doExecute(context: TestRunningFlowContext) {
            seenInputSizes.add(context.inputs.size)
            if (invocationCount.getAndIncrement() == 0) {
                firstExecutionStarted.countDown()
                releaseFirstExecution.await(5, TimeUnit.SECONDS)
            } else {
                secondExecutionStarted.countDown()
            }
        }

        override fun order(): Int = 1
    }

    private class TestRunningFlowContext private constructor(
        override val source: String,
        inputs: List<InputEntry>,
        firstInputEpochMillis: Long
    ) : RunningFlowContext(inputs, firstInputEpochMillis) {

        companion object {
            fun of(
                source: String,
                input: String,
                receivedAtMillis: Long = System.currentTimeMillis()
            ): TestRunningFlowContext {
                return TestRunningFlowContext(
                    source = source,
                    inputs = listOf(InputEntry(0L, input)),
                    firstInputEpochMillis = receivedAtMillis
                )
            }
        }

        override fun recreate(inputs: List<InputEntry>): RunningFlowContext {
            return TestRunningFlowContext(
                source = source,
                inputs = inputs,
                firstInputEpochMillis = System.currentTimeMillis()
            )
        }
    }
}
