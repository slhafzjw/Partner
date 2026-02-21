package work.slhaf.partner.api.agent.factory.component.exception;

public class ModuleProxyGenerateFailedException extends ModuleFactoryInitFailedException {
    public ModuleProxyGenerateFailedException(String message) {
        super(message);
    }

    public ModuleProxyGenerateFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
