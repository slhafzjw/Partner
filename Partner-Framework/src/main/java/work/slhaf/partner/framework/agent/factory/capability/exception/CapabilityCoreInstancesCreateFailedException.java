package work.slhaf.partner.framework.agent.factory.capability.exception;

public class CapabilityCoreInstancesCreateFailedException extends CapabilityFactoryExecuteFailedException {
    public CapabilityCoreInstancesCreateFailedException(String message) {
        super(message);
    }

    public CapabilityCoreInstancesCreateFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
