package work.slhaf.partner.api.agent.runtime.exception;

public class GlobalExceptionHandler {

    public static GlobalExceptionHandler INSTANCE = new GlobalExceptionHandler();

    private AgentExceptionCallback exceptionCallback = new DefaultAgentExceptionCallback();

    public void handle(Throwable e) {

        switch (e.getClass().getSimpleName()) {
            case "AgentRuntimeException":
                exceptionCallback.onRuntimeException((AgentRuntimeException) e);
                break;
            case "AgentLaunchFailedException":
                exceptionCallback.onFailedException((AgentLaunchFailedException) e);
                break;
            default:
                throw new RuntimeException("未经处理的异常!", e);
        }
    }

    public static void setExceptionCallback(AgentExceptionCallback callback) {
        INSTANCE.exceptionCallback = callback;
    }
}
