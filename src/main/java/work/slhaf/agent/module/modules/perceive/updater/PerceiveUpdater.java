package work.slhaf.agent.module.modules.perceive.updater;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.agent.core.cognation.CognationCapability;
import work.slhaf.agent.core.cognation.CognationManager;
import work.slhaf.agent.core.cognation.submodule.perceive.PerceiveCapability;
import work.slhaf.agent.core.cognation.submodule.perceive.pojo.User;
import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.core.interaction.module.InteractionModule;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;
import work.slhaf.agent.module.modules.perceive.updater.relation_extractor.pojo.RelationExtractResult;
import work.slhaf.agent.module.modules.perceive.updater.relation_extractor.RelationExtractor;
import work.slhaf.agent.module.modules.perceive.updater.static_extractor.StaticMemoryExtractor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 感知更新，异步
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class PerceiveUpdater extends Model implements InteractionModule {

    private static volatile PerceiveUpdater perceiveUpdater;

    private PerceiveCapability perceiveCapability;
    private CognationCapability cognationCapability;
    private InteractionThreadPoolExecutor executor;
    private RelationExtractor relationExtractor;
    private StaticMemoryExtractor staticMemoryExtractor;


    public static PerceiveUpdater getInstance() throws IOException, ClassNotFoundException {
        if (perceiveUpdater == null) {
            synchronized (PerceiveUpdater.class) {
                if (perceiveUpdater == null) {
                    perceiveUpdater = new PerceiveUpdater();
                    perceiveUpdater.setPerceiveCapability(CognationManager.getInstance());
                    perceiveUpdater.setCognationCapability(CognationManager.getInstance());
                    perceiveUpdater.setExecutor(InteractionThreadPoolExecutor.getInstance());
                    perceiveUpdater.setRelationExtractor(RelationExtractor.getInstance());
                    perceiveUpdater.setStaticMemoryExtractor(StaticMemoryExtractor.getInstance());
                    setModel(perceiveUpdater, ModelConstant.Prompt.PERCEIVE, true);
                }
            }
        }
        return perceiveUpdater;
    }

    @Override
    public void execute(InteractionContext context) throws IOException, ClassNotFoundException {
        executor.execute(() -> {
            boolean trigger = context.getModuleContext().getExtraContext().getBoolean("perceive_updater");
            if (!trigger){
                return;
            }
            ReentrantLock userLock = new ReentrantLock();
            User user = new User();
            user.setUuid(context.getUserId());
            List<Callable<Void>> tasks = new ArrayList<>();
            tasks.add(() -> {
                runStaticExtractorAction(context, userLock, user);
                return null;
            });
            tasks.add(() -> {
                runRelationExtractorAction(context, userLock, user);
                return null;
            });
            executor.invokeAll(tasks);
            perceiveCapability.updateUser(user);
        });
    }

    private void runRelationExtractorAction(InteractionContext context, ReentrantLock userLock, User user) {
        RelationExtractResult relationExtractResult = relationExtractor.execute(context);
        userLock.lock();
        user.setRelation(relationExtractResult.getRelation());
        user.setImpressions(relationExtractResult.getImpressions());
        user.setAttitude(relationExtractResult.getAttitude());
        user.updateRelationChange(relationExtractResult.getRelationChangeHistory());
        userLock.unlock();
    }

    private void runStaticExtractorAction(InteractionContext context, ReentrantLock userLock, User user) {
        HashMap<String, String> newStaticMemory = staticMemoryExtractor.execute(context);
        userLock.lock();
        user.setStaticMemory(newStaticMemory);
        userLock.unlock();
    }

    @Override
    protected String modelKey() {
        return "perceive_updater";
    }
}
