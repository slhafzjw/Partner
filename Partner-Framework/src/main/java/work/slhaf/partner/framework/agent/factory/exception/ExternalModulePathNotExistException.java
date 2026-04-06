package work.slhaf.partner.framework.agent.factory.exception;

public class ExternalModulePathNotExistException extends AgentRegisterFactoryFailedException {
    public ExternalModulePathNotExistException(String message) {
        super(message);
    }

    public ExternalModulePathNotExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
