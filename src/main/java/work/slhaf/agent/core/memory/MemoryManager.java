package work.slhaf.agent.core.memory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.modules.memory.SliceEvaluator;

import java.io.IOException;

@Data
@Slf4j
public class MemoryManager implements InteractionModule {

    private static MemoryManager memoryManager;

    private MemoryGraph memoryGraph;
    private SliceEvaluator sliceEvaluator;

    private MemoryManager(){}

    @Override
    public void execute(InteractionContext interactionContext) {

    }

    public static MemoryManager getInstance() throws IOException, ClassNotFoundException {
        if (memoryManager == null) {
            Config config = Config.getConfig();
            memoryManager = new MemoryManager();
            memoryManager.setMemoryGraph(MemoryGraph.getInstance(config.getAgentId()));
            memoryManager.setSliceEvaluator(SliceEvaluator.getInstance());
            log.info("MemoryManager注册完毕...");
        }
        return memoryManager;
    }

}
