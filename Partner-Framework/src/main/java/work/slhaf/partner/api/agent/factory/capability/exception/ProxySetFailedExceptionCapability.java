package work.slhaf.partner.api.agent.factory.capability.exception;

public class ProxySetFailedExceptionCapability extends CapabilityFactoryExecuteFailedException {
  public ProxySetFailedExceptionCapability(String message) {
    super(message);
  }

  public ProxySetFailedExceptionCapability(String message, Throwable cause) {
    super(message, cause);
  }
}
