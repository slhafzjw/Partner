package work.slhaf.partner.framework.agent.factory.capability.exception;

public class DuplicateMethodException extends CapabilityCheckFailedException {
    public DuplicateMethodException(String message) {
        super(message);
    }

    public DuplicateMethodException(String message, Throwable cause) {
        super(message, cause);
    }
}
