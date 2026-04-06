package work.slhaf.partner.framework.agent.factory.capability.exception;

public class EmptyCapabilityHolderException extends CapabilityCheckFailedException {
    public EmptyCapabilityHolderException(String message) {
        super(message);
    }

    public EmptyCapabilityHolderException(String message, Throwable cause) {
        super(message, cause);
    }
}
