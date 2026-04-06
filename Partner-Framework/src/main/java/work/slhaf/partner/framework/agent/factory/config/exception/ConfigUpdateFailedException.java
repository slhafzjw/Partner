package work.slhaf.partner.framework.agent.factory.config.exception;

public class ConfigUpdateFailedException extends ConfigFactoryRuntimeException {
    public ConfigUpdateFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigUpdateFailedException(String message) {
        super(message);
    }
}
