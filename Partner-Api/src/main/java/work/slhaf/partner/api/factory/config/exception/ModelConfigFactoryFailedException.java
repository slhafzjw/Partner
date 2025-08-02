package work.slhaf.partner.api.factory.config.exception;

public class ModelConfigFactoryFailedException extends RuntimeException {
    public ModelConfigFactoryFailedException(String message, Throwable cause) {
        super("ModelConfigManager 执行失败: " + message, cause);
    }

    public ModelConfigFactoryFailedException(String message) {
        super("ModelConfigManager 执行失败: " + message);
    }
}
