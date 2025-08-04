package work.slhaf.partner.api.factory.module.exception;

public class ModuleProxyGenerateFailedException extends ModuleFactoryFailedException{
    public ModuleProxyGenerateFailedException(String message) {
        super(message);
    }

    public ModuleProxyGenerateFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
