package work.slhaf.partner.framework.agent.factory.capability.exception;

import work.slhaf.partner.framework.agent.exception.deprecated.AgentLaunchFailedException;

public class CapabilityCheckFailedException extends AgentLaunchFailedException {
    public CapabilityCheckFailedException(String message) {
        super("Capability注册失败: " + message);
    }

    public CapabilityCheckFailedException(String message, Throwable cause) {
        super("Capability注册失败: " + message, cause);
    }
}
