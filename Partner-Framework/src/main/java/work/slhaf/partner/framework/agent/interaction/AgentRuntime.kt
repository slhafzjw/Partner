package work.slhaf.partner.framework.agent.interaction

import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.annotation.JSONField
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import work.slhaf.partner.framework.agent.config.Config
import work.slhaf.partner.framework.agent.config.ConfigRegistration
import work.slhaf.partner.framework.agent.config.Configurable
import work.slhaf.partner.framework.agent.exception.ExceptionReporterHandler
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.framework.agent.factory.context.AgentContext
import work.slhaf.partner.framework.agent.factory.context.ModuleContextData
import work.slhaf.partner.framework.agent.interaction.data.InteractionEvent
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext
import work.slhaf.partner.framework.agent.support.Result
import java.nio.file.Path
import java.util.*

object AgentRuntime : Configurable, ConfigRegistration<ModuleMaskConfig> {

    private const val DEFAULT_LOG_CHANNEL = "log_channel"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val wakeSignal = Channel<Unit>(Channel.UNLIMITED)
    private val stateLock = Any()

    /**
     * 按照 source 分开存储的最新的 context，input 聚合、其余信息按照最新输入
     */
    private val latestContextsBySource = LinkedHashMap<String, RunningFlowContext>()

    /**
     * source 队列，其中元素不会重复，触发唤醒信号时，从该队列取出 source 并处理对应的 context
     */
    private val sourceQueue = ArrayDeque<String>()

    /**
     * 与对应 source 的最新 context 对应，用于记录 context 版本状态
     */
    private val sourceVersions = mutableMapOf<String, Long>()

    private val responseChannels = mutableMapOf<String, ResponseChannel>()

    @Volatile
    private var defaultChannel: String = DEFAULT_LOG_CHANNEL

    @Volatile
    private var runningModules: Map<Int, List<AbstractAgentModule.Running<RunningFlowContext>>> = emptyMap()

    @Volatile
    private var maskedModules: Set<String> = emptySet()

    @Volatile
    private var currentExecutingSource: String? = null

    @Volatile
    private var currentExecutingContext: RunningFlowContext? = null

    init {
        register()
        scope.launch {
            for (@Suppress("UNUSED_VARIABLE") ignored in wakeSignal) {
                drainQueue()
            }
        }
        responseChannels.putIfAbsent(DEFAULT_LOG_CHANNEL, LogChannel)
    }

    fun registerResponseChannel(channelName: String, responseChannel: ResponseChannel) {
        responseChannels[channelName] = responseChannel
    }

    fun unregisterResponseChannel(channelName: String) {
        if (channelName == DEFAULT_LOG_CHANNEL) {
            return
        }
        responseChannels.remove(channelName)
    }

    fun setDefaultResponseChannel(channelName: String) {
        defaultChannel = channelName
    }

    fun defaultResponseChannel(): String = defaultChannel

    @JvmOverloads
    fun response(event: InteractionEvent, channelName: String = defaultChannel) {
        val channel = responseChannels[channelName]
        if (channel == null) {
            responseChannels[defaultChannel]?.response(event)
                ?: responseChannels[DEFAULT_LOG_CHANNEL]?.response(event)
                ?: LogChannel.response(event)
        } else {
            channel.response(event)
        }
    }

    fun <C : RunningFlowContext> submit(context: C) = runBlocking {
        synchronized(stateLock) {
            val source = context.source
            latestContextsBySource[source] = latestContextsBySource[source]?.mergedWith(context) ?: context
            sourceVersions[source] = (sourceVersions[source] ?: 0L) + 1L
            if (!sourceQueue.contains(source)) {
                sourceQueue.addLast(source)
            }
            if (currentExecutingSource == source) {
                currentExecutingContext?.status?.interrupted = true
            }
        }
        wakeSignal.send(Unit)
    }

    private suspend fun drainQueue() {
        while (true) {
            val source = synchronized(stateLock) {
                sourceQueue.firstOrNull()
            } ?: return
            executeSource(source)
        }
    }

    private suspend fun executeSource(source: String) {
        while (true) {
            val execution = synchronized(stateLock) {
                val context = latestContextsBySource[source] ?: run {
                    sourceQueue.remove(source)
                    sourceVersions.remove(source)
                    return
                }
                currentExecutingSource = source
                currentExecutingContext = context
                context.status.interrupted = false
                SourceExecution(context, sourceVersions[source] ?: 0L)
            }

            val interrupted = executeTurn(execution.context)

            val shouldRetry = synchronized(stateLock) {
                currentExecutingSource = null
                currentExecutingContext = null
                val latestContext = latestContextsBySource[source]
                val latestVersion = sourceVersions[source] ?: execution.version
                when {
                    latestContext == null -> {
                        sourceQueue.remove(source)
                        sourceVersions.remove(source)
                        false
                    }

                    interrupted || latestVersion != execution.version -> true
                    else -> {
                        latestContextsBySource.remove(source)
                        sourceQueue.remove(source)
                        sourceVersions.remove(source)
                        false
                    }
                }
            }

            if (!shouldRetry) {
                return
            }
        }
    }

    private suspend fun executeTurn(runningFlowContext: RunningFlowContext): Boolean {
        if (runningModules.isEmpty()) {
            refreshRunningModules()
        }

        for (modules in runningModules.values) {
            if (runningFlowContext.status.interrupted) {
                return true
            }
            executeOrder(modules, runningFlowContext)
        }

        return runningFlowContext.status.interrupted
    }

    private fun refreshRunningModules() {
        runningModules = AgentContext.modules.values
            .filterIsInstance<ModuleContextData.Running<AbstractAgentModule.Running<RunningFlowContext>>>()
            .filterNot { maskedModules.contains(it.instance.moduleName) }
            .groupBy { it.order }
            .mapValues { it.value.map { contextData -> contextData.instance } }
            .toSortedMap()
    }

    private suspend fun executeOrder(
        modules: List<AbstractAgentModule.Running<RunningFlowContext>>,
        runningFlowContext: RunningFlowContext
    ) {
        coroutineScope {
            val jobs = modules.map { module ->
                async {
                    if (runningFlowContext.status.interrupted) {
                        return@async
                    }
                    if (runningFlowContext.skippedModules.contains(module.moduleName)) {
                        return@async
                    }
                    Result.runCatching { module.execute(runningFlowContext) }
                        .onFailure { ExceptionReporterHandler.report(it) }
                }
            }
            jobs.awaitAll()
        }
    }

    override fun declare(): Map<Path, ConfigRegistration<out Config>> {
        return mapOf(Path.of("masked_modules.json") to this)
    }

    override fun type(): Class<ModuleMaskConfig> {
        return ModuleMaskConfig::class.java
    }

    override fun init(
        config: ModuleMaskConfig,
        json: JSONObject?
    ) {
        applyModuleMask(config)
    }

    override fun onReload(
        config: ModuleMaskConfig,
        json: JSONObject?
    ) {
        applyModuleMask(config)
    }

    override fun defaultConfig(): ModuleMaskConfig {
        return ModuleMaskConfig(setOf())
    }

    private fun applyModuleMask(config: ModuleMaskConfig) {
        maskedModules = config.maskedModules
        refreshRunningModules()
    }

    private data class SourceExecution(
        val context: RunningFlowContext,
        val version: Long
    )
}

data class ModuleMaskConfig(
    @field:JSONField(name = "masked_modules")
    val maskedModules: Set<String>
) : Config()
