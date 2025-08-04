package work.slhaf.partner.api.factory.exception;

public class ExternalModulePathNotExistException extends AgentRegisterFactoryFailedException {
    public ExternalModulePathNotExistException(String message) {
        super(message);
    }

    public ExternalModulePathNotExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
