package work.slhaf.partner.api.agent.runtime.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalExceptionHandler {

    public static GlobalExceptionHandler INSTANCE = new GlobalExceptionHandler();

    private AgentExceptionCallback exceptionCallback = new LogAgentExceptionCallback();

    public void handle(Throwable e) {

        switch (e.getClass().getSimpleName()) {
            case "AgentRuntimeException":
                exceptionCallback.onRuntimeException((AgentRuntimeException) e);
                break;
            case "AgentLaunchFailedException":
                exceptionCallback.onFailedException((AgentLaunchFailedException) e);
                break;
            default:
                log.error("未知异常: ", e);
        }
    }

    public static void setExceptionCallback(AgentExceptionCallback callback) {
        INSTANCE.exceptionCallback = callback;
    }
}
