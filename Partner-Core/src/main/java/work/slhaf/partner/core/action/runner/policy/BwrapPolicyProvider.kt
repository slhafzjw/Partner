package work.slhaf.partner.core.action.runner.policy

import work.slhaf.partner.core.action.exception.ActionInfrastructureStartupException

private const val BWRAP_COMMAND = "bwrap"

object BwrapPolicyProvider : PolicyProvider(
    policyName = "bwrap"
) {

    init {
        requireBwrapAvailable()
    }

    override fun prepare(
        policy: ExecutionPolicy,
        commands: List<String>
    ): WrappedLaunchSpec {
        val (command, args) = splitCommands(commands)
        val wrappedArgs = buildList {
            add("--ro-bind")
            add("/")
            add("/")
            add("--proc")
            add("/proc")
            add("--dev")
            add("/dev")
            if (policy.net == ExecutionPolicy.Network.DISABLE) {
                add("--unshare-net")
            }
            if (!policy.workingDirectory.isNullOrBlank()) {
                add("--chdir")
                add(policy.workingDirectory)
            }
            policy.readOnlyPaths.forEach { path ->
                add("--ro-bind")
                add(path)
                add(path)
            }
            policy.writablePaths.forEach { path ->
                add("--bind")
                add(path)
                add(path)
            }
            add("--")
            add(command)
            addAll(args)
        }
        return WrappedLaunchSpec(
            command = BWRAP_COMMAND,
            args = wrappedArgs,
            workingDirectory = policy.workingDirectory,
            environment = resolveEnvironment(policy)
        )
    }

    private fun requireBwrapAvailable() {
        val available = try {
            val process = ProcessBuilder(BWRAP_COMMAND, "--version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            throw ActionInfrastructureStartupException(
                "Failed to detect executable command '$BWRAP_COMMAND'",
                "bwrap-policy-provider",
                null,
                BWRAP_COMMAND,
                e
            )
        }
        if (!available) {
            throw ActionInfrastructureStartupException(
                "Executable command '$BWRAP_COMMAND' is not available",
                "bwrap-policy-provider",
                null,
                BWRAP_COMMAND
            )
        }
    }
}
