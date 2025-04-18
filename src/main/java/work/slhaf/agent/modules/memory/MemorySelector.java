package work.slhaf.agent.modules.memory;

import lombok.Data;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;

import java.io.IOException;

@Data
public class MemorySelector implements InteractionModule {

    private static MemorySelector memorySelector;

    private MemoryManager memoryManager;
    private SliceEvaluator sliceEvaluator;

    private MemorySelector(){}

    public static MemorySelector getInstance() throws IOException, ClassNotFoundException {
        if (memorySelector == null) {
            memorySelector = new MemorySelector();
            memorySelector.setMemoryManager(MemoryManager.getInstance());
            memorySelector.setSliceEvaluator(SliceEvaluator.getInstance());
        }
        return memorySelector;
    }

    @Override
    public void execute(InteractionContext interactionContext) {

    }
}
