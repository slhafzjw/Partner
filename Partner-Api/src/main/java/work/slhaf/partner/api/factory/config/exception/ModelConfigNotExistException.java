package work.slhaf.partner.api.factory.config.exception;

public class ModelConfigNotExistException extends ModelConfigFactoryFailedException {
    public ModelConfigNotExistException(String message, Throwable e) {
        super(message, e);
    }

    public ModelConfigNotExistException(String message) {
        super(message);
    }
}
