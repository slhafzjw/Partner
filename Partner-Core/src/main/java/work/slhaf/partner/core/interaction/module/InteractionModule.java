package work.slhaf.partner.core.interaction.module;

import work.slhaf.partner.core.interaction.data.context.InteractionContext;

import java.io.IOException;

public interface InteractionModule {
    void execute(InteractionContext context) throws IOException, ClassNotFoundException;
}
