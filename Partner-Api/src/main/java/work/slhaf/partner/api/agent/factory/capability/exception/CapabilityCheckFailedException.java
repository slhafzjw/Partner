package work.slhaf.partner.api.agent.factory.capability.exception;

public class CapabilityCheckFailedException extends RuntimeException {
    public CapabilityCheckFailedException(String message) {
        super("Capability注册失败: " + message);
    }

    public CapabilityCheckFailedException(String message, Throwable cause) {
        super("Capability注册失败: " + message, cause);
    }
}
