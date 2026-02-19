package work.slhaf.partner.api.agent.factory.config.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentLaunchFailedException;

public class ConfigFactoryInitFailedException extends AgentLaunchFailedException {
    public ConfigFactoryInitFailedException(String message, Throwable cause) {
        super("AgentConfigManager 执行失败: " + message, cause);
    }

    public ConfigFactoryInitFailedException(String message) {
        super("AgentConfigManager 执行失败: " + message);
    }
}
