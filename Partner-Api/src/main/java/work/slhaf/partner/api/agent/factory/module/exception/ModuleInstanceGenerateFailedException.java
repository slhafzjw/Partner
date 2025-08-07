package work.slhaf.partner.api.agent.factory.module.exception;

public class ModuleInstanceGenerateFailedException extends ModuleFactoryFailedException{
    public ModuleInstanceGenerateFailedException(String message) {
        super(message);
    }

    public ModuleInstanceGenerateFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
