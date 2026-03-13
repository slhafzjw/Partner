package work.slhaf.partner.core.action.runner.policy

object DirectPolicyProvider : PolicyProvider(
    policyName = "direct"
) {

    override fun prepare(
        policy: ExecutionPolicy,
        commands: List<String>
    ): WrappedLaunchSpec {
        val (command, args) = splitCommands(commands)
        return WrappedLaunchSpec(
            command = command,
            args = args,
            workingDirectory = policy.workingDirectory,
            environment = resolveEnvironment(policy)
        )
    }

}