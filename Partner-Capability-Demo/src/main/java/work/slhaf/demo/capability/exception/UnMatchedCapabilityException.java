package work.slhaf.demo.capability.exception;

public class UnMatchedCapabilityException extends CapabilityCheckFailedException{
    public UnMatchedCapabilityException(String message) {
        super(message);
    }
}
