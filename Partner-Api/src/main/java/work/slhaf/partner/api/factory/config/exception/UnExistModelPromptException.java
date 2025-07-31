package work.slhaf.partner.api.factory.config.exception;

public class UnExistModelPromptException extends ModelConfigFactoryFailedException{
    public UnExistModelPromptException(String message) {
        super(message);
    }

    public UnExistModelPromptException(String message, Throwable cause) {
        super(message, cause);
    }
}
