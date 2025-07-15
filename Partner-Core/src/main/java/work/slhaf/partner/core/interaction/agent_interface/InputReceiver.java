package work.slhaf.partner.core.interaction.agent_interface;


import work.slhaf.partner.core.interaction.data.InteractionInputData;

import java.io.IOException;

public interface InputReceiver {

    void receiveInput(InteractionInputData inputData) throws IOException, ClassNotFoundException;
}
