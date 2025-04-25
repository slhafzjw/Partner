package work.slhaf.agent.modules.memory;

import lombok.Data;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.InteractionThreadPoolExecutor;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;

import java.io.IOException;

@Data
public class MemoryUpdater implements InteractionModule {

    private static MemoryUpdater memoryUpdater;

    private MemoryManager memoryManager;
    private InteractionThreadPoolExecutor executor;
    private MemorySelectExtractor memorySelectExtractor;

    private MemoryUpdater(){}

    public static MemoryUpdater getInstance() throws IOException, ClassNotFoundException {
        if (memoryUpdater == null) {
            memoryUpdater = new MemoryUpdater();
            memoryUpdater.setMemoryManager(MemoryManager.getInstance());
            memoryUpdater.setMemorySelectExtractor(MemorySelectExtractor.getInstance());
        }
        return memoryUpdater;
    }

    @Override
    public void execute(InteractionContext interactionContext) {
        if (interactionContext.isFinished()){
            return;
        }
        //如果token 大于阈值，则更新记忆
        if (interactionContext.getModuleContext().getIntValue("total_token") > 24000) {
            executor.execute(() -> {

            });
        }

        //更新确定性记忆


    }
}
