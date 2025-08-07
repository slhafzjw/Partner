package work.slhaf.partner.api.agent.factory.config.exception;

public class ModelPromptDirNotExistException extends ModelConfigFactoryFailedException{
    public ModelPromptDirNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModelPromptDirNotExistException(String message) {
        super(message);
    }
}
