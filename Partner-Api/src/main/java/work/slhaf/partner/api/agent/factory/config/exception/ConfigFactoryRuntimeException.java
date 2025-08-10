package work.slhaf.partner.api.agent.factory.config.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentRuntimeException;

public class ConfigFactoryRuntimeException extends AgentRuntimeException {
    public ConfigFactoryRuntimeException(String message, Throwable cause) {
        super("ConfigFactory 运行出错: " + message, cause);
    }

    public ConfigFactoryRuntimeException(String message) {
        super("ConfigFactory 运行出错: " + message);
    }
}
