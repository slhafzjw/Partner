package work.slhaf.agent.core.interaction;

import work.slhaf.agent.core.interaction.data.InteractionInputData;

import java.io.IOException;

public interface InputReceiver {

    void receiveInput(InteractionInputData inputData) throws IOException, ClassNotFoundException, InterruptedException;
}
