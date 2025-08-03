package work.slhaf.partner.api.factory.exception;

public class ExternalModulePathNotExistException extends RuntimeException {
    public ExternalModulePathNotExistException(String message) {
        super("AgentRegisterFactory 执行失败: " + message);
    }
}
