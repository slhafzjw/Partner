package work.slhaf.partner.api.factory.capability.exception;

public class ProxySetFailedExceptionCapability extends CapabilityFactoryExecuteFailedException {
  public ProxySetFailedExceptionCapability(String message) {
    super(message);
  }

  public ProxySetFailedExceptionCapability(String message, Throwable cause) {
    super(message, cause);
  }
}
