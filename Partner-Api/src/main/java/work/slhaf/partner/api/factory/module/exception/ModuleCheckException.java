package work.slhaf.partner.api.factory.module.exception;

public class ModuleCheckException extends ModuleFactoryFailedException{
    public ModuleCheckException(String message) {
        super(message);
    }

    public ModuleCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
