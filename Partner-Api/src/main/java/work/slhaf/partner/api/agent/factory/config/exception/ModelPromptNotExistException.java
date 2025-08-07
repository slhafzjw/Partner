package work.slhaf.partner.api.agent.factory.config.exception;

public class ModelPromptNotExistException extends ModelConfigFactoryFailedException{
    public ModelPromptNotExistException(String message) {
        super(message);
    }

    public ModelPromptNotExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
