package work.slhaf.partner.api.agent.runtime.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalExceptionHandler {

    public static GlobalExceptionHandler INSTANCE = new GlobalExceptionHandler();

    private AgentExceptionCallback exceptionCallback = new LogAgentExceptionCallback();

    public boolean handle(Throwable e) {
        boolean exit;
        Throwable cause = e.getCause();
        switch (cause) {
            case AgentRunningFailedException arfe -> {
                exit = true;
                exceptionCallback.onRuntimeException((AgentRuntimeException) cause);
            }
            case AgentRuntimeException are -> {
                exit = false;
                exceptionCallback.onRuntimeException((AgentRuntimeException) cause);
            }
            case AgentLaunchFailedException alfe -> {
                exit = true;
                exceptionCallback.onFailedException((AgentLaunchFailedException) cause);
            }
            default -> {
                exit = true;
                log.error("意外异常: ", cause);
            }
        }
        return exit;
    }

    public static void setExceptionCallback(AgentExceptionCallback callback) {
        INSTANCE.exceptionCallback = callback;
    }
}
