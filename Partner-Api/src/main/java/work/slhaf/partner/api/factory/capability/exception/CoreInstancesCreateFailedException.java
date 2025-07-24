package work.slhaf.partner.api.factory.capability.exception;

public class CoreInstancesCreateFailedException extends FactoryExecuteFailedException {
    public CoreInstancesCreateFailedException(String message) {
        super(message);
    }

    public CoreInstancesCreateFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
