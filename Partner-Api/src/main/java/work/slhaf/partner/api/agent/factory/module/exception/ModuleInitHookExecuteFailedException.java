package work.slhaf.partner.api.agent.factory.module.exception;

public class ModuleInitHookExecuteFailedException extends ModuleFactoryFailedException{
    public ModuleInitHookExecuteFailedException(String message) {
        super(message);
    }

    public ModuleInitHookExecuteFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
