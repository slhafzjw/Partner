package work.slhaf.partner.api.factory.capability.exception;

public class CoreInstancesCreateFailedExceptionCapability extends CapabilityFactoryExecuteFailedException {
    public CoreInstancesCreateFailedExceptionCapability(String message) {
        super(message);
    }

    public CoreInstancesCreateFailedExceptionCapability(String message, Throwable cause) {
        super(message, cause);
    }
}
