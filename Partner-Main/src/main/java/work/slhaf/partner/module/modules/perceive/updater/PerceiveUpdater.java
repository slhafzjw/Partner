package work.slhaf.partner.module.modules.perceive.updater;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.submodule.perceive.PerceiveCapability;
import work.slhaf.partner.core.submodule.perceive.pojo.User;
import work.slhaf.partner.module.modules.perceive.updater.relation_extractor.RelationExtractor;
import work.slhaf.partner.module.modules.perceive.updater.relation_extractor.pojo.RelationExtractResult;
import work.slhaf.partner.module.modules.perceive.updater.static_extractor.StaticMemoryExtractor;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 感知更新，异步
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
@AgentModule(name = "perceive_updater", order = 8)
public class PerceiveUpdater extends AgentRunningModule<PartnerRunningFlowContext> {

    private static volatile PerceiveUpdater perceiveUpdater;

    @InjectCapability
    private PerceiveCapability perceiveCapability;
    @InjectCapability
    private CognationCapability cognationCapability;

    @InjectModule
    private RelationExtractor relationExtractor;
    @InjectModule
    private StaticMemoryExtractor staticMemoryExtractor;

    private InteractionThreadPoolExecutor executor;


    @Init
    public void init() {
        this.executor = InteractionThreadPoolExecutor.getInstance();
    }

    public void execute(PartnerRunningFlowContext context) throws IOException, ClassNotFoundException {
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

    private void runRelationExtractorAction(PartnerRunningFlowContext context, ReentrantLock userLock, User user) {
        RelationExtractResult relationExtractResult = relationExtractor.execute(context);
        userLock.lock();
        user.setRelation(relationExtractResult.getRelation());
        user.setImpressions(relationExtractResult.getImpressions());
        user.setAttitude(relationExtractResult.getAttitude());
        user.updateRelationChange(relationExtractResult.getRelationChangeHistory());
        userLock.unlock();
    }

    private void runStaticExtractorAction(PartnerRunningFlowContext context, ReentrantLock userLock, User user) {
        HashMap<String, String> newStaticMemory = staticMemoryExtractor.execute(context);
        userLock.lock();
        user.setStaticMemory(newStaticMemory);
        userLock.unlock();
    }

}
