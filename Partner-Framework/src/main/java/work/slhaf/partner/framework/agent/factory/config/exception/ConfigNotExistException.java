package work.slhaf.partner.framework.agent.factory.config.exception;

public class ConfigNotExistException extends ConfigFactoryInitFailedException {
    public ConfigNotExistException(String message, Throwable e) {
        super(message, e);
    }

    public ConfigNotExistException(String message) {
        super(message);
    }
}
