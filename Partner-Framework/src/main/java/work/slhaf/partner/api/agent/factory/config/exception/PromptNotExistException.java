package work.slhaf.partner.api.agent.factory.config.exception;

public class PromptNotExistException extends ConfigFactoryInitFailedException {
    public PromptNotExistException(String message) {
        super(message);
    }

    public PromptNotExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
