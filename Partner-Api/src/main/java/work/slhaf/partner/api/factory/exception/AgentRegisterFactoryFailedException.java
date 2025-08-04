package work.slhaf.partner.api.factory.exception;

public class AgentRegisterFactoryFailedException extends RuntimeException {
    public AgentRegisterFactoryFailedException(String message, Throwable cause) {
        super("AgentRegisterFactory 执行失败: " + message, cause);
    }

    public AgentRegisterFactoryFailedException(String message) {
        super("AgentRegisterFactory 执行失败: " + message);
    }
}
