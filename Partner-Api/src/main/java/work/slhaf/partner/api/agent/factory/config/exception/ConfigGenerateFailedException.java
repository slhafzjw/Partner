package work.slhaf.partner.api.agent.factory.config.exception;

public class ConfigGenerateFailedException extends ConfigFactoryInitFailedException {
    public ConfigGenerateFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigGenerateFailedException(String message) {
        super(message);
    }
}
