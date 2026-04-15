package work.slhaf.partner.runtime

import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext

class PartnerRunningFlowContext private constructor(
    override val source: String,
    inputs: List<InputEntry>,
    firstInputEpochMillis: Long,
    additionalUserInfo: Map<String, String> = emptyMap(),
    skippedModules: Set<String> = emptySet(),
    target: String = source
) : RunningFlowContext(inputs, firstInputEpochMillis, additionalUserInfo, skippedModules, target) {

    companion object {

        private const val SOURCE_SELF = "self"
        private const val SOURCE_SELF_PLATFORM = "AGENT_INTERNAL"
        private const val SOURCE_SELF_NICKNAME = "PARTNER"

        private object InfoKeys {
            const val PLATFORM = "platform"
            const val NICKNAME = "nickname"
        }

        private object SourceTag {
            private const val AGENT = "[AGENT]"
            private const val USER = "[USER]"

            fun buildUserSource(userId: String): String = "$USER: $userId"
            fun buildAgentSource(): String = "$AGENT: $SOURCE_SELF"
        }

        @JvmStatic
        fun fromUser(userId: String, input: String, receivedAtMillis: Long = System.currentTimeMillis()) =
            PartnerRunningFlowContext(
                SourceTag.buildUserSource(userId),
                listOf(InputEntry(0L, input)),
                receivedAtMillis
            )

        @JvmStatic
        fun fromSelf(input: String, receivedAtMillis: Long = System.currentTimeMillis()) =
            PartnerRunningFlowContext(
                SourceTag.buildAgentSource(),
                listOf(InputEntry(0L, input)),
                receivedAtMillis
            ).apply {
                putUserInfo(InfoKeys.PLATFORM, SOURCE_SELF_PLATFORM)
                putUserInfo(InfoKeys.NICKNAME, SOURCE_SELF_NICKNAME)
            }
    }

    override fun copyWith(
        inputs: List<InputEntry>,
        firstInputEpochMillis: Long,
        additionalUserInfo: Map<String, String>,
        skippedModules: Set<String>,
        target: String
    ): RunningFlowContext {
        return PartnerRunningFlowContext(
            source = source,
            inputs = inputs,
            firstInputEpochMillis = firstInputEpochMillis,
            additionalUserInfo = additionalUserInfo,
            skippedModules = skippedModules,
            target = target
        )
    }
}
