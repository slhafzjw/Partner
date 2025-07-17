package work.slhaf.demo.capability.exception;

public class ProxySetFailedException extends FactoryExecuteFailedException{
  public ProxySetFailedException(String message) {
    super(message);
  }

  public ProxySetFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
