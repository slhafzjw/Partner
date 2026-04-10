package work.slhaf.partner.framework.agent.factory.capability.exception;

import work.slhaf.partner.framework.agent.exception.deprecated.AgentLaunchFailedException;

public class CapabilityFactoryExecuteFailedException extends AgentLaunchFailedException {
    public CapabilityFactoryExecuteFailedException(String message) {
        super("CapabilityRegisterFactory 执行失败: " + message);
    }

    public CapabilityFactoryExecuteFailedException(String message, Throwable cause) {
        super("CapabilityRegisterFactory 执行失败: " + message, cause);
    }
}
