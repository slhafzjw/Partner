package work.slhaf.partner.core.action.runner.policy

import com.alibaba.fastjson2.JSONObject
import work.slhaf.partner.framework.agent.config.Config
import work.slhaf.partner.framework.agent.config.ConfigRegistration
import work.slhaf.partner.framework.agent.config.Configurable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

object ExecutionPolicyRegistry : Configurable, ConfigRegistration<ExecutionPolicy> {

    private const val DEFAULT_PROVIDER = "direct"

    private val policyProviders = ConcurrentHashMap<String, PolicyProvider>().apply {
        put(DEFAULT_PROVIDER, DirectPolicyProvider)
    }

    private val listeners = CopyOnWriteArraySet<RunnerExecutionPolicyListener>()

    init {
        register()
    }

    @Volatile
    private lateinit var currentPolicy: ExecutionPolicy

    fun prepare(commands: List<String>): WrappedLaunchSpec {
        val policy = currentPolicy
        val provider = policyProviders[policy.provider]
            ?: policyProviders[DEFAULT_PROVIDER]
            ?: error("Default provider '${DEFAULT_PROVIDER}' is not registered")
        return provider.prepare(policy, commands)
    }
    fun updatePolicy(policy: ExecutionPolicy) {
        currentPolicy = policy
        listeners.forEach { it.onPolicyChanged(policy) }
    }

    fun addListener(listener: RunnerExecutionPolicyListener) {
        listeners += listener
    }

    fun removeListener(listener: RunnerExecutionPolicyListener) {
        listeners -= listener
    }

    fun registerPolicyProvider(policyProvider: PolicyProvider) {
        val name = policyProvider.policyName
        if (policyProviders.containsKey(name)) {
            return
        }
        policyProviders[name] = policyProvider
    }

    override fun declare(): Map<Path, ConfigRegistration<out Config>> {
        return mapOf(Path.of("action", "runner_policy.json") to this)
    }

    override fun type(): Class<ExecutionPolicy> {
        return ExecutionPolicy::class.java
    }

    override fun init(
        config: ExecutionPolicy,
        json: JSONObject?
    ) {
        this.currentPolicy = config
    }

    override fun defaultConfig(): ExecutionPolicy {
        return ExecutionPolicy(
            provider = "direct",
            mode = ExecutionPolicy.Mode.DIRECT,
            net = ExecutionPolicy.Network.ENABLE,
            inheritEnv = true,
            env = emptyMap(),
            workingDirectory = null,
            readOnlyPaths = emptySet(),
            writablePaths = emptySet(),
        )
    }

    override fun onReload(
        config: ExecutionPolicy,
        json: JSONObject?
    ) {
        this.currentPolicy = config
    }
}

data class ExecutionPolicy(
    val mode: Mode,
    val provider: String,
    val net: Network,
    val inheritEnv: Boolean,
    val env: Map<String, String>,
    val workingDirectory: String?,
    val readOnlyPaths: Set<String>,
    val writablePaths: Set<String>,
) : Config() {

    enum class Mode {
        DIRECT,
        SANDBOX
    }

    enum class Network {
        DISABLE,
        ENABLE
    }

}

data class WrappedLaunchSpec(
    val command: String,
    val args: List<String>,
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap()
)

abstract class PolicyProvider(
    val policyName: String
) {

    abstract fun prepare(
        policy: ExecutionPolicy,
        commands: List<String>
    ): WrappedLaunchSpec

    protected fun resolveEnvironment(policy: ExecutionPolicy): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        if (policy.inheritEnv) {
            result.putAll(System.getenv())
        }
        result.putAll(policy.env)
        return result
    }

    protected fun splitCommands(commands: List<String>): Pair<String, List<String>> {
        require(commands.isNotEmpty()) { "commands must not be empty" }
        return commands.first() to commands.drop(1)
    }

}

interface RunnerExecutionPolicyListener {
    fun onPolicyChanged(policy: ExecutionPolicy)

    fun registerPolicyListener() {
        ExecutionPolicyRegistry.addListener(this)
    }

    fun unregisterPolicyListener() {
        ExecutionPolicyRegistry.removeListener(this)
    }
}
