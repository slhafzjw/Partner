package work.slhaf.agent.core.interaction.module;

import work.slhaf.agent.core.interaction.data.context.InteractionContext;

import java.io.IOException;

public interface InteractionModule {
    void execute(InteractionContext context) throws IOException, ClassNotFoundException;
}
