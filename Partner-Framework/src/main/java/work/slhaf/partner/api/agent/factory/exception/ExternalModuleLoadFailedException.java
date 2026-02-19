package work.slhaf.partner.api.agent.factory.exception;

public class ExternalModuleLoadFailedException extends AgentRegisterFactoryFailedException{
    public ExternalModuleLoadFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalModuleLoadFailedException(String message) {
        super(message);
    }
}
