package work.slhaf.agent.gateway;

import work.slhaf.agent.core.interaction.data.InteractionOutputData;

public interface MessageSender {
    void sendMessage(InteractionOutputData outputData);
}
