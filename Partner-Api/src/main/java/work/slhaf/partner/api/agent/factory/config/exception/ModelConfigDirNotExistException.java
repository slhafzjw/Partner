package work.slhaf.partner.api.agent.factory.config.exception;

public class ModelConfigDirNotExistException extends ModelConfigFactoryFailedException{
    public ModelConfigDirNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModelConfigDirNotExistException(String message) {
        super(message);
    }
}
