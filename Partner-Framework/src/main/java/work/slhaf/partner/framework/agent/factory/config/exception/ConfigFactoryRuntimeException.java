package work.slhaf.partner.framework.agent.factory.config.exception;

import work.slhaf.partner.framework.agent.exception.deprecated.AgentRuntimeException;

public class ConfigFactoryRuntimeException extends AgentRuntimeException {
    public ConfigFactoryRuntimeException(String message, Throwable cause) {
        super("ConfigFactory 运行出错: " + message, cause);
    }

    public ConfigFactoryRuntimeException(String message) {
        super("ConfigFactory 运行出错: " + message);
    }
}
