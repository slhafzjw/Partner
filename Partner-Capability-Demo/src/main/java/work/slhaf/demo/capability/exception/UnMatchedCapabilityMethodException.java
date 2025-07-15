package work.slhaf.demo.capability.exception;

public class UnMatchedCapabilityMethodException extends CapabilityCheckFailedException{
    public UnMatchedCapabilityMethodException(String message) {
        super(message);
    }
}
