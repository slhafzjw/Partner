package work.slhaf.partner.api.factory.capability.exception;

public class UnMatchedCoordinatedMethodException extends CapabilityCheckFailedException {
    public UnMatchedCoordinatedMethodException(String message) {
        super(message);
    }

    public UnMatchedCoordinatedMethodException(String message, Throwable cause) {
        super(message, cause);
    }
}
