package work.slhaf.partner.api.factory.capability.exception;

public class DuplicateMethodException extends CapabilityCheckFailedException{
    public DuplicateMethodException(String message) {
        super(message);
    }

    public DuplicateMethodException(String message, Throwable cause) {
        super(message, cause);
    }
}
