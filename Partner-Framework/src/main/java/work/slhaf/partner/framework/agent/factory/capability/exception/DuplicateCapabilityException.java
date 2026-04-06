package work.slhaf.partner.framework.agent.factory.capability.exception;

public class DuplicateCapabilityException extends CapabilityCheckFailedException {
    public DuplicateCapabilityException(String message) {
        super(message);
    }

    public DuplicateCapabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
