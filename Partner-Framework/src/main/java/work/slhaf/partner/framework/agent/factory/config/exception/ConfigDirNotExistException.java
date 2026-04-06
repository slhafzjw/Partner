package work.slhaf.partner.framework.agent.factory.config.exception;

public class ConfigDirNotExistException extends ConfigFactoryInitFailedException {
    public ConfigDirNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigDirNotExistException(String message) {
        super(message);
    }
}
