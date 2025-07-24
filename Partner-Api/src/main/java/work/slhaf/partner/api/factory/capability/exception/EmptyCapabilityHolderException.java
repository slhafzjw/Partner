package work.slhaf.partner.api.factory.capability.exception;

public class EmptyCapabilityHolderException extends CapabilityCheckFailedException{
    public EmptyCapabilityHolderException(String message) {
        super(message);
    }

    public EmptyCapabilityHolderException(String message, Throwable cause) {
        super(message, cause);
    }
}
