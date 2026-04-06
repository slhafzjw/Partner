package work.slhaf.partner.core.action;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.intervention.MetaIntervention;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.framework.agent.factory.capability.annotation.Capability;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@Capability(value = "action")
public interface ActionCapability {

    void putAction(@NonNull ExecutableAction executableAction);

    Set<ExecutableAction> listActions(@Nullable ExecutableAction.Status status, @Nullable String source);

    ExecutorService getExecutor(ActionCore.ExecutorType type);

    MetaAction loadMetaAction(@NonNull String actionKey);

    MetaActionInfo loadMetaActionInfo(@NonNull String actionKey);

    void registerMetaActions(@NonNull Map<String, MetaActionInfo> metaActions);

    Map<String, MetaActionInfo> listAvailableMetaActions();

    boolean checkExists(String... actionKeys);

    RunnerClient runnerClient();

    void handleInterventions(List<MetaIntervention> interventions, ExecutableAction data);

}
