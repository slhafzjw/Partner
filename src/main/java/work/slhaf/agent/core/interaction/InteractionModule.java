package work.slhaf.agent.core.interaction;

import work.slhaf.agent.core.interaction.data.InteractionContext;

import java.io.IOException;

public interface InteractionModule {
    void execute(InteractionContext context) throws IOException, ClassNotFoundException, InterruptedException;
}
