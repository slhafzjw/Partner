package work.slhaf.partner.framework.agent.model;

import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;

public interface StreamChatMessageConsumer {

    void onDelta(String delta);

    default void onComplete() {
    }

    default void onError(AgentRuntimeException exception) {
    }

    String collectResponse();
}
