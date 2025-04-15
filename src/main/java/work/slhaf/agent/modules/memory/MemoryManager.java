package work.slhaf.agent.modules.memory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;

@Data
@Slf4j
public class MemoryManager {

    private static MemoryManager memoryManager;

    private MemoryGraph memoryGraph;
    private SliceEvaluator sliceEvaluator;

    public static MemoryManager initialize(Config config){
        if (memoryManager == null) {
            memoryManager = new MemoryManager();
            memoryManager.setMemoryGraph(MemoryGraph.initialize(config.getAgentId()));
            memoryManager.setSliceEvaluator(SliceEvaluator.initialize(config));
            log.info("MemoryManager注册完毕...");
        }
        return memoryManager;
    }

}
