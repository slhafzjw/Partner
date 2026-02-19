package work.slhaf.partner.api.agent.factory.capability.exception;

public class CapabilityCoreInstancesCreateFailedException extends CapabilityFactoryExecuteFailedException {
    public CapabilityCoreInstancesCreateFailedException(String message) {
        super(message);
    }

    public CapabilityCoreInstancesCreateFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
