package work.slhaf.partner.core.action;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.PhaserRecord;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.module.modules.action.interventor.entity.MetaIntervention;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

@Capability(value = "action")
public interface ActionCapability {

    void putAction(@NonNull ExecutableAction executableAction);

    Set<ExecutableAction> listActions(@Nullable ExecutableAction.Status status, @Nullable String source);

    List<ExecutableAction> popPendingAction(String userId);

    List<ExecutableAction> listPendingAction(String userId);

    void putPendingActions(String userId, ExecutableAction executableAction);

    List<String> selectTendencyCache(String input);

    void updateTendencyCache(CacheAdjustData data);

    ExecutorService getExecutor(ActionCore.ExecutorType type);

    PhaserRecord putPhaserRecord(Phaser phaser, ExecutableAction executableAction);

    void removePhaserRecord(Phaser phaser);

    List<PhaserRecord> listPhaserRecords();

    PhaserRecord getPhaserRecord(String tendency, String source);

    MetaAction loadMetaAction(@NonNull String actionKey);

    MetaActionInfo loadMetaActionInfo(@NonNull String actionKey);

    Map<String, MetaActionInfo> listAvailableMetaActions();

    boolean checkExists(String... actionKeys);

    RunnerClient runnerClient();

    void handleInterventions(List<MetaIntervention> interventions, ExecutableAction data);

}
