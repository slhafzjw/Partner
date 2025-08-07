package work.slhaf.partner.api.agent.factory.exception;

public class ExternalModulePathNotExistException extends AgentRegisterFactoryFailedException {
    public ExternalModulePathNotExistException(String message) {
        super(message);
    }

    public ExternalModulePathNotExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
