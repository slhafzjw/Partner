package work.slhaf.agent.module.modules.perceive.selector;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.core.interaction.module.InteractionModule;
import work.slhaf.agent.module.common.PreModuleActions;

import java.io.IOException;

@Slf4j
@Setter
public class PerceiveSelector implements InteractionModule, PreModuleActions {

    private static volatile PerceiveSelector perceiveSelector;


    public static PerceiveSelector getInstance() throws IOException, ClassNotFoundException {
        if (perceiveSelector == null) {
            synchronized (PerceiveSelector.class) {
                if (perceiveSelector == null) {
                    perceiveSelector = new PerceiveSelector();
                }
            }
        }
        return perceiveSelector;
    }

    @Override
    public void execute(InteractionContext context) throws IOException, ClassNotFoundException {

    }

    @Override
    public void setAppendedPrompt(InteractionContext context) {

    }

    @Override
    public void setActiveModule(InteractionContext context) {

    }

    @Override
    public String getModuleName() {
        return "";
    }
}
