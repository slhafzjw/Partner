package work.slhaf.partner.api.factory.config.exception;

public class UnExistModelConfigException extends ModelConfigFactoryFailedException {
    public UnExistModelConfigException(String message, Throwable e) {
        super(message, e);
    }

    public UnExistModelConfigException(String message) {
        super(message);
    }
}
