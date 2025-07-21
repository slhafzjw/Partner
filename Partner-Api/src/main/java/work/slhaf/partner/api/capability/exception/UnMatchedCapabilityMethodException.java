package work.slhaf.partner.api.capability.exception;

public class UnMatchedCapabilityMethodException extends CapabilityCheckFailedException {
    public UnMatchedCapabilityMethodException(String message) {
        super(message);
    }

    public UnMatchedCapabilityMethodException(String message, Throwable cause) {
        super(message, cause);
    }
}
