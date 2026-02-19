package work.slhaf.partner.api.agent.factory.config.exception;

public class PromptDirNotExistException extends ConfigFactoryInitFailedException {
    public PromptDirNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public PromptDirNotExistException(String message) {
        super(message);
    }
}
