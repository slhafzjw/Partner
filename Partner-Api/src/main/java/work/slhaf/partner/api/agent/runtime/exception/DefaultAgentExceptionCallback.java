package work.slhaf.partner.api.agent.runtime.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultAgentExceptionCallback implements AgentExceptionCallback {

    @Override
    public void onRuntimeException(AgentRuntimeException e) {
        log.error("Agent 运行异常: ", e);
    }

    @Override
    public void onFailedException(AgentLaunchFailedException e) {
        throw e;
    }
}
