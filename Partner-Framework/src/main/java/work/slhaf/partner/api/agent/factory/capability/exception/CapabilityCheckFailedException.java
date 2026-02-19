package work.slhaf.partner.api.agent.factory.capability.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentLaunchFailedException;

public class CapabilityCheckFailedException extends AgentLaunchFailedException {
    public CapabilityCheckFailedException(String message) {
        super("Capability注册失败: " + message);
    }

    public CapabilityCheckFailedException(String message, Throwable cause) {
        super("Capability注册失败: " + message, cause);
    }
}
