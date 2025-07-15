package work.slhaf.demo.capability.exception;

public class DuplicateCapabilityException extends CapabilityCheckFailedException{
    public DuplicateCapabilityException(String message) {
        super(message);
    }
}
