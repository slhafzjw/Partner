package work.slhaf.partner.core.cognition;

import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.context.ContextWorkspace;
import work.slhaf.partner.core.cognition.impression.ActiveEntity;
import work.slhaf.partner.core.cognition.impression.Entity;
import work.slhaf.partner.framework.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

@Capability("cognition")
public interface CognitionCapability {

    void initiateTurn(String input, String target, String... skippedModules);

    ContextWorkspace contextWorkspace();

    List<Message> getChatMessages();

    List<Message> snapshotChatMessages();

    void rollChatMessagesWithSnapshot(int snapshotSize, int retainDivisor);

    void refreshRecentChatMessagesContext();

    Element messageNotesElement();

    Lock getMessageLock();

    /**
     * Project user input onto known or currently active entities and append the input as runtime evidence.
     */
    Set<ActiveEntity> projectEntity(String input);

    /**
     * Return current active entities with their bound known entities when available.
     * ActiveEntity values are snapshots; Entity values are live known-entity references and should be updated through this capability.
     */
    Map<ActiveEntity, Entity> showEntities();

    /**
     * Create and register a new known entity by subject, then refresh search indexes for it.
     */
    String createEntity(String subject);

    /**
     * Return a known entity by uuid, or null when it does not exist.
     */
    Entity getEntity(String uuid);

    /**
     * Activate a known entity into the runtime context and return a detached active-entity snapshot.
     */
    ActiveEntity activateKnownEntity(String entityUuid);

    /**
     * Bind an active runtime entity to a known entity and refresh the active-entity search document.
     */
    boolean bindActiveEntity(String runtimeId, String entityUuid);

    /**
     * Rename the canonical subject of a known entity and refresh entity/active-entity indexes.
     */
    boolean renameEntitySubject(String entityUuid, String newSubject, boolean keepOldSubjectAsAlias);

    /**
     * Add an alias or mention form for a known entity and refresh entity indexes.
     */
    boolean addEntityAlias(String entityUuid, String alias, boolean deprecated);

    /**
     * Add or replace an impression on a known entity and refresh all entity indexes.
     */
    boolean updateEntityImpression(String entityUuid, String impression, String newImpression, double confidence);

    /**
     * Add or replace a stable feature on a known entity and refresh all entity indexes.
     */
    boolean updateEntityFeature(String entityUuid, String feature, String newFeature, double confidence);

    /**
     * Add or update a relation from one known entity to another target and refresh all entity indexes.
     */
    boolean updateEntityRelation(String entityUuid, String target, String relation, double strength);

}
