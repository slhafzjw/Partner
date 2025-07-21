package work.slhaf.partner.gateway;

import work.slhaf.partner.core.interaction.data.InteractionOutputData;

public interface MessageSender {
    void sendMessage(InteractionOutputData outputData);
}
