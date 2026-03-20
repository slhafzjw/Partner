package work.slhaf.partner.core.action;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;
import work.slhaf.partner.core.action.entity.intervention.MetaIntervention;
import work.slhaf.partner.core.action.runner.RunnerClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

@Capability(value = "action")
public interface ActionCapability {

    void putAction(@NonNull ExecutableAction executableAction);

    Set<ExecutableAction> listActions(@Nullable ExecutableAction.Status status, @Nullable String source);

    PendingActionRecord createPendingAction(String userId, ExecutableAction executableAction, long ttlMillis, long reminderBeforeMillis);

    List<PendingActionRecord> listActivePendingActions(String userId);

    PendingActionRecord resolvePendingDecision(String userId, String pendingId, PendingActionRecord.Decision decision, String reason);

    boolean markPendingReminded(String pendingId);

    PendingActionRecord expirePendingIfWaiting(String pendingId);

    void bindPendingLifecycleActions(String pendingId, StateAction reminderAction, StateAction expireAction);

    void cancelPendingLifecycleActions(String pendingId);

    List<String> selectTendencyCache(String input);

    void updateTendencyCache(CacheAdjustData data);

    ExecutorService getExecutor(ActionCore.ExecutorType type);

    PhaserRecord putPhaserRecord(Phaser phaser, ExecutableAction executableAction);

    void removePhaserRecord(Phaser phaser);

    List<PhaserRecord> listPhaserRecords();

    PhaserRecord getPhaserRecord(String tendency, String source);

    MetaAction loadMetaAction(@NonNull String actionKey);

    MetaActionInfo loadMetaActionInfo(@NonNull String actionKey);

    void registerMetaActions(@NonNull Map<String, MetaActionInfo> metaActions);

    Map<String, MetaActionInfo> listAvailableMetaActions();

    boolean checkExists(String... actionKeys);

    RunnerClient runnerClient();

    void handleInterventions(List<MetaIntervention> interventions, ExecutableAction data);

}
