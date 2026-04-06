package work.slhaf.partner.framework.agent.factory.component.exception;

public class ModuleInstanceGenerateFailedException extends ModuleFactoryInitFailedException {
    public ModuleInstanceGenerateFailedException(String message) {
        super(message);
    }

    public ModuleInstanceGenerateFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
