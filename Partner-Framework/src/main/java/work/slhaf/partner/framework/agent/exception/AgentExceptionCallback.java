package work.slhaf.partner.framework.agent.exception;

public interface AgentExceptionCallback {
    void onRuntimeException(AgentRuntimeException e);

    void onFailedException(AgentLaunchFailedException e);
}
