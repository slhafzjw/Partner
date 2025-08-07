package work.slhaf.partner.api.agent.factory.module.exception;

public class ModuleFactoryFailedException extends RuntimeException {
    public ModuleFactoryFailedException(String message) {
        super("ModuleFactory 执行失败: "+message);
    }

    public ModuleFactoryFailedException(String message, Throwable cause) {
        super("ModuleFactory 执行失败: "+message, cause);
    }
}
