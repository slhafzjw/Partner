package work.slhaf.demo.capability.exception;

public class DuplicateCapabilityException extends CapabilityCheckFailedException{
    public DuplicateCapabilityException(String message) {
        super(message);
    }

    public DuplicateCapabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
