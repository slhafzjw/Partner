package work.slhaf.partner.framework.agent.factory.capability.exception;

public class CapabilityProxySetFailedException extends CapabilityFactoryExecuteFailedException {
    public CapabilityProxySetFailedException(String message) {
        super(message);
    }

    public CapabilityProxySetFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
