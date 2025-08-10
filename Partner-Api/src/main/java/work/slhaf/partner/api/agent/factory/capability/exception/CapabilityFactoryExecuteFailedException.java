package work.slhaf.partner.api.agent.factory.capability.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentLaunchFailedException;

public class CapabilityFactoryExecuteFailedException extends AgentLaunchFailedException {
    public CapabilityFactoryExecuteFailedException(String message) {
        super("CapabilityRegisterFactory 执行失败: " + message);
    }

    public CapabilityFactoryExecuteFailedException(String message, Throwable cause) {
        super("CapabilityRegisterFactory 执行失败: " + message, cause);
    }
}
