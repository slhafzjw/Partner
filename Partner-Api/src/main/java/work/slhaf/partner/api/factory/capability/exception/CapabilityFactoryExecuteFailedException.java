package work.slhaf.partner.api.factory.capability.exception;

public class CapabilityFactoryExecuteFailedException extends RuntimeException {
    public CapabilityFactoryExecuteFailedException(String message) {
        super("CapabilityRegisterFactory 执行失败: " + message);
    }

    public CapabilityFactoryExecuteFailedException(String message, Throwable cause) {
        super("CapabilityRegisterFactory 执行失败: " + message, cause);
    }
}
