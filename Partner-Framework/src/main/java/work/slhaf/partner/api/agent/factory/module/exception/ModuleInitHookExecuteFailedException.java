package work.slhaf.partner.api.agent.factory.module.exception;

public class ModuleInitHookExecuteFailedException extends ModuleFactoryInitFailedException {
    public ModuleInitHookExecuteFailedException(String message) {
        super(message);
    }

    public ModuleInitHookExecuteFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
