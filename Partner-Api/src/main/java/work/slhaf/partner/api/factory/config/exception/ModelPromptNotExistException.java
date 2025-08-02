package work.slhaf.partner.api.factory.config.exception;

public class ModelPromptNotExistException extends ModelConfigFactoryFailedException{
    public ModelPromptNotExistException(String message) {
        super(message);
    }

    public ModelPromptNotExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
