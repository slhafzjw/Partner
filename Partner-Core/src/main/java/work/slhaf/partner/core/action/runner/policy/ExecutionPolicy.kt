package work.slhaf.partner.core.action.runner.policy

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

object ExecutionPolicyRegistry {

    private const val DEFAULT_PROVIDER = "direct"

    private val policyProviders = ConcurrentHashMap<String, PolicyProvider>().apply {
        put(DEFAULT_PROVIDER, DirectPolicyProvider)
    }

    private val listeners = CopyOnWriteArraySet<RunnerExecutionPolicyListener>()

    @Volatile
    private var currentPolicy = ExecutionPolicy(
        provider = "direct",
        mode = ExecutionPolicy.Mode.DIRECT,
        net = ExecutionPolicy.Network.ENABLE,
        inheritEnv = true,
        env = emptyMap(),
        workingDirectory = null,
        readOnlyPaths = emptySet(),
        writablePaths = emptySet(),
    )

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
) {

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
}
