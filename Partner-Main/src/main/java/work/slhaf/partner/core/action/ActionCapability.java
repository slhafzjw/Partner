package work.slhaf.partner.core.action;

import lombok.NonNull;
import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

@Capability(value = "action")
public interface ActionCapability {
    void putPreparedAction(String uuid, ActionData actionData);

    List<ActionData> popPendingAction(String userId);

    List<ActionData> listPreparedAction(String userId);

    List<ActionData> listPendingAction(String userId);

    void putPendingActions(String userId, ActionData actionData);

    List<String> selectTendencyCache(String input);

    void updateTendencyCache(CacheAdjustData data);

    ExecutorService getExecutor(ActionCore.ExecutorType type);

    Set<String> getExistedMetaActions();

    void putPhaserRecord(Phaser phaser, ActionData actionData);

    void removePhaserRecord(Phaser phaser);

    List<ActionCore.PhaserRecord> listPhaserRecords();

    ActionCore.PhaserRecord getPhaserRecord(String tendency, String source);

    MetaAction loadMetaAction(@NonNull String actionKey);

    MetaActionInfo loadMetaActionInfo(@NonNull String actionKey);

    boolean checkExists(String... actionKeys);

    void execute(MetaAction metaAction);
}
