package work.slhaf.partner.api.agent.factory.capability.exception;

public class CoreInstancesCreateFailedExceptionCapability extends CapabilityFactoryExecuteFailedException {
    public CoreInstancesCreateFailedExceptionCapability(String message) {
        super(message);
    }

    public CoreInstancesCreateFailedExceptionCapability(String message, Throwable cause) {
        super(message, cause);
    }
}
