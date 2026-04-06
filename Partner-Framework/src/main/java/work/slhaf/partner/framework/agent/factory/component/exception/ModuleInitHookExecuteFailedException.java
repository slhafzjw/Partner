package work.slhaf.partner.framework.agent.factory.component.exception;

public class ModuleInitHookExecuteFailedException extends ModuleFactoryInitFailedException {
    public ModuleInitHookExecuteFailedException(String message) {
        super(message);
    }

    public ModuleInitHookExecuteFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
