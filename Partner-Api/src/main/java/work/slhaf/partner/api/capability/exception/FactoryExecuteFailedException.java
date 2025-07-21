package work.slhaf.partner.api.capability.exception;

public class FactoryExecuteFailedException extends RuntimeException {
    public FactoryExecuteFailedException(String message) {
        super("CapabilityRegisterFactory 执行失败: " + message);
    }

    public FactoryExecuteFailedException(String message, Throwable cause) {
        super("CapabilityRegisterFactory 执行失败: " + message, cause);
    }
}
