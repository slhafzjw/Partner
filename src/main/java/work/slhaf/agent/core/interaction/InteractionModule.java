package work.slhaf.agent.core.interaction;

import work.slhaf.agent.core.interaction.data.InteractionContext;

public interface InteractionModule {
    void execute(InteractionContext context);
}
