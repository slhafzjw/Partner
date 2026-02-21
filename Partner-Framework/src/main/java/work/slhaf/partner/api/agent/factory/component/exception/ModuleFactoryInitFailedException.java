package work.slhaf.partner.api.agent.factory.component.exception;

public class ModuleFactoryInitFailedException extends RuntimeException {
    public ModuleFactoryInitFailedException(String message) {
        super("ModuleFactory 执行失败: " + message);
    }

    public ModuleFactoryInitFailedException(String message, Throwable cause) {
        super("ModuleFactory 执行失败: " + message, cause);
    }
}
