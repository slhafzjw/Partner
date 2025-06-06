package work.slhaf.agent.module.modules.perceive.selector;

import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.core.interaction.module.InteractionModule;

import java.io.IOException;

public class PerceiveSelector  implements InteractionModule {

    private static PerceiveSelector perceiveSelector;

    public static PerceiveSelector getInstance() throws IOException, ClassNotFoundException {
        if (perceiveSelector == null) {
            perceiveSelector = new PerceiveSelector();
        }

        return perceiveSelector;
    }

    @Override
    public void execute(InteractionContext context) throws IOException, ClassNotFoundException {

    }
}
