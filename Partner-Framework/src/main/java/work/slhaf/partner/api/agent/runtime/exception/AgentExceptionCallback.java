package work.slhaf.partner.api.agent.runtime.exception;

public interface AgentExceptionCallback {
    void onRuntimeException(AgentRuntimeException e);
    void onFailedException(AgentLaunchFailedException e);
}
