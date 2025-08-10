package work.slhaf.partner.api.agent.factory.module.exception;

public class ModuleCheckException extends ModuleFactoryInitFailedException {
    public ModuleCheckException(String message) {
        super(message);
    }

    public ModuleCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
