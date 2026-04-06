package work.slhaf.partner.framework.agent.factory.capability.exception;

public class UnMatchedCapabilityException extends CapabilityCheckFailedException {
    public UnMatchedCapabilityException(String message) {
        super(message);
    }

    public UnMatchedCapabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
