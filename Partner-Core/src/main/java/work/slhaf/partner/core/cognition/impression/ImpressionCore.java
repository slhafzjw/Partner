package work.slhaf.partner.core.cognition.impression;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.cognition.impression.search.*;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.framework.agent.state.State;
import work.slhaf.partner.framework.agent.state.StateSerializable;
import work.slhaf.partner.framework.agent.state.StateValue;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@CapabilityCore(value = "cognition")
public class ImpressionCore implements StateSerializable {

    /**
     * Keyed by entity uuid. Subject can be revised or merged later, so it should not be used as the stable key.
     */
    private final ConcurrentHashMap<String, Entity> knownEntitiesByUuid = new ConcurrentHashMap<>();
    private final ImpressionVectorIndex vectorIndex = new ImpressionVectorIndex();
    private final Set<ActiveEntity> activeEntities = new HashSet<>();
    private final ImpressionTextSearch textSearch = new SimpleTextSearch();

    private static final int TEXT_SEARCH_LIMIT = 20;
    private static final int ASSOCIATION_MATCH_LIMIT = 8;
    private static final double SUPPORTING_HIT_FACTOR = 0.3;
    private static final double ASSOCIATION_CONFIDENCE_DIVISOR = 5.0;

    /**
     * 根据新的 Input 召回相关的实体，如果实体已重复，则将输入追加到 ActiveEntity 的证据中。
     *
     * @param input 本次输入内容
     * @return 本次被召回的活跃实体（包括重复的实体）
     */
    @CapabilityMethod
    public Set<ActiveEntity> projectEntity(String input) {
        if (input == null || input.isBlank()) {
            return Set.of();
        }

        List<ImpressionSearchHit> textSearchHits = textSearch.search(input, TEXT_SEARCH_LIMIT);
        List<EntityAssociationMatch> associationMatches = aggregateMatches(textSearchHits, ASSOCIATION_MATCH_LIMIT);
        if (associationMatches.isEmpty()) {
            return Set.of();
        }

        Set<ActiveEntity> projected = new HashSet<>();
        for (EntityAssociationMatch match : associationMatches) {
            Optional<ActiveEntity> activeEntity = resolveActiveEntity(match.getTarget());
            if (activeEntity.isEmpty()) {
                continue;
            }

            ActiveEntity entity = activeEntity.get();
            entity.addEvidence(
                    input,
                    associationConfidence(match),
                    EntityEvidence.Source.USER_INPUT
            );
            refreshActiveEntityTextSearch(entity);
            projected.add(entity);
        }

        return projected;
    }

    /**
     * 列出当前已存在的 ActiveEntity 以及对应的 Entity。ActiveEntity 返回快照，Entity 返回当前已知实体引用。
     *
     * 注意：外部模块不要直接修改返回的 Entity，否则文本索引 / 向量索引不会刷新。
     * Impression 更新应走 updateEntity* 系列接口。
     *
     * @return ActiveEntity 快照与已绑定 Entity 的映射
     */
    @CapabilityMethod
    public Map<ActiveEntity, Entity> showEntities() {
        Map<ActiveEntity, Entity> result = new LinkedHashMap<>();
        List<ActiveEntity> entities;
        synchronized (activeEntities) {
            entities = activeEntities.stream()
                    .sorted(Comparator
                            .comparing(ActiveEntity::getLastMentionedAt)
                            .reversed()
                            .thenComparing(ActiveEntity::getRuntimeId))
                    .toList();
        }

        for (ActiveEntity activeEntity : entities) {
            Entity boundEntity = Optional.ofNullable(activeEntity.getBoundEntityUuid())
                    .map(knownEntitiesByUuid::get)
                    .orElse(null);
            result.put(activeEntity.snapshot(), boundEntity);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Create a new known entity and make it visible to recall/update indexes immediately.
     */
    @CapabilityMethod
    public String createEntity(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }

        Entity entity = new Entity(UUID.randomUUID().toString(), subject.trim());
        entity.register();
        knownEntitiesByUuid.put(entity.getUuid(), entity);
        refreshKnownEntityIndexes(entity);
        return entity.getUuid();
    }

    /**
     * Look up a known entity by stable uuid.
     */
    @CapabilityMethod
    public Entity getEntity(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        return knownEntitiesByUuid.get(uuid);
    }

    /**
     * Activate a known entity and return a detached snapshot for external consumers.
     */
    @CapabilityMethod
    public ActiveEntity activateKnownEntity(String entityUuid) {
        return activateKnownEntityLive(entityUuid)
                .map(ActiveEntity::snapshot)
                .orElse(null);
    }

    /**
     * Bind a runtime active entity to a known entity.
     * This keeps the active entity in current context while giving later updates a stable storage target.
     */
    @CapabilityMethod
    public boolean bindActiveEntity(String runtimeId, String entityUuid) {
        if (runtimeId == null || runtimeId.isBlank() || entityUuid == null || entityUuid.isBlank()) {
            return false;
        }

        Entity entity = knownEntitiesByUuid.get(entityUuid);
        if (entity == null) {
            return false;
        }

        Optional<ActiveEntity> activeEntity = findActiveEntityByRuntimeId(runtimeId);
        if (activeEntity.isEmpty()) {
            return false;
        }

        ActiveEntity active = activeEntity.get();
        active.bindEntity(entityUuid);
        active.updateSubject(entity.getSubject());
        refreshActiveEntityTextSearch(active);
        return true;
    }

    /**
     * Rename the canonical subject of a known entity and optionally keep its previous subject as a historical alias.
     */
    @CapabilityMethod
    public boolean renameEntitySubject(String entityUuid, String newSubject, boolean keepOldSubjectAsAlias) {
        Entity entity = knownEntitiesByUuid.get(entityUuid);
        if (entity == null || newSubject == null || newSubject.isBlank()) {
            return false;
        }

        boolean renamed = entity.renameSubject(newSubject.trim(), keepOldSubjectAsAlias);
        if (!renamed) {
            return false;
        }

        refreshKnownEntityIndexes(entity);
        syncBoundActiveEntitySubjects(entity);
        return true;
    }

    /**
     * Add an alias or mention form for a known entity and refresh search indexes.
     */
    @CapabilityMethod
    public boolean addEntityAlias(String entityUuid, String alias, boolean deprecated) {
        Entity entity = knownEntitiesByUuid.get(entityUuid);
        if (entity == null || alias == null || alias.isBlank()) {
            return false;
        }

        boolean added = entity.addAlias(alias.trim(), deprecated);
        if (!added) {
            return false;
        }

        refreshKnownEntityIndexes(entity);
        return true;
    }

    /**
     * Update a known entity impression through the core so text/vector indexes stay consistent.
     * newImpression can be null or blank to update the existing impression in place.
     */
    @CapabilityMethod
    public boolean updateEntityImpression(
            String entityUuid,
            String impression,
            String newImpression,
            double confidence
    ) {
        Entity entity = knownEntitiesByUuid.get(entityUuid);
        if (entity == null || impression == null || impression.isBlank()) {
            return false;
        }

        entity.updateImpression(
                impression.trim(),
                normalizeNullableText(newImpression),
                confidence
        );
        refreshKnownEntityIndexes(entity);
        return true;
    }

    /**
     * Update a known entity feature through the core so text/vector indexes stay consistent.
     * newFeature can be null or blank to update the existing feature in place.
     */
    @CapabilityMethod
    public boolean updateEntityFeature(
            String entityUuid,
            String feature,
            String newFeature,
            double confidence
    ) {
        Entity entity = knownEntitiesByUuid.get(entityUuid);
        if (entity == null || feature == null || feature.isBlank()) {
            return false;
        }

        entity.updateFeature(
                feature.trim(),
                normalizeNullableText(newFeature),
                confidence
        );
        refreshKnownEntityIndexes(entity);
        return true;
    }

    /**
     * Update a known entity relation through the core so search documents reflect the changed relation.
     */
    @CapabilityMethod
    public boolean updateEntityRelation(
            String entityUuid,
            String target,
            String relation,
            double strength
    ) {
        Entity entity = knownEntitiesByUuid.get(entityUuid);
        if (entity == null || target == null || target.isBlank() || relation == null || relation.isBlank()) {
            return false;
        }

        entity.updateRelation(target.trim(), relation.trim(), strength);
        refreshKnownEntityIndexes(entity);
        return true;
    }

    /**
     * Normalize optional replacement text used by update methods.
     */
    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<EntityAssociationMatch> aggregateMatches(
            List<ImpressionSearchHit> hits,
            int limit
    ) {
        if (hits == null || hits.isEmpty() || limit <= 0) {
            return List.of();
        }

        return hits.stream()
                .collect(Collectors.groupingBy(
                        hit -> hit.getDocument().getTarget(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<ImpressionSearchHit> sortedHits = entry.getValue()
                            .stream()
                            .sorted(Comparator
                                    .comparingDouble(ImpressionSearchHit::getScore)
                                    .reversed()
                                    .thenComparing(hit -> hit.getDocument().getId()))
                            .toList();
                    return new EntityAssociationMatch(
                            entry.getKey(),
                            aggregateScore(sortedHits),
                            sortedHits
                    );
                })
                .sorted(Comparator
                        .comparingDouble(EntityAssociationMatch::getScore)
                        .reversed()
                        .thenComparing(match -> match.getTarget().getType().name())
                        .thenComparing(match -> match.getTarget().getId()))
                .limit(limit)
                .toList();
    }

    private double aggregateScore(List<ImpressionSearchHit> sortedHits) {
        if (sortedHits.isEmpty()) {
            return 0.0;
        }

        double bestHitScore = sortedHits.getFirst().getScore();
        double supportingScore = sortedHits.stream()
                .skip(1)
                .limit(2)
                .mapToDouble(hit -> hit.getScore() * SUPPORTING_HIT_FACTOR)
                .sum();
        return bestHitScore + supportingScore;
    }

    private Optional<ActiveEntity> resolveActiveEntity(ImpressionSearchTarget target) {
        return switch (target.getType()) {
            case ACTIVE_ENTITY -> findActiveEntityByRuntimeId(target.getId());
            case ENTITY -> activateKnownEntityLive(target.getId());
        };
    }

    private Optional<ActiveEntity> findActiveEntityByRuntimeId(String runtimeId) {
        synchronized (activeEntities) {
            return activeEntities.stream()
                    .filter(activeEntity -> activeEntity.getRuntimeId().equals(runtimeId))
                    .findFirst();
        }
    }

    private Optional<ActiveEntity> findActiveEntityByBoundEntityUuid(String uuid) {
        synchronized (activeEntities) {
            return activeEntities.stream()
                    .filter(activeEntity -> uuid.equals(activeEntity.getBoundEntityUuid()))
                    .findFirst();
        }
    }

    private Optional<ActiveEntity> activateKnownEntityLive(String uuid) {
        Entity knownEntity = knownEntitiesByUuid.get(uuid);
        if (knownEntity == null) {
            return Optional.empty();
        }

        Optional<ActiveEntity> existing = findActiveEntityByBoundEntityUuid(uuid);
        if (existing.isPresent()) {
            return existing;
        }

        ActiveEntity activeEntity = new ActiveEntity();
        activeEntity.updateSubject(knownEntity.getSubject());
        activeEntity.bindEntity(uuid);

        synchronized (activeEntities) {
            activeEntities.add(activeEntity);
        }
        refreshActiveEntityTextSearch(activeEntity);
        return Optional.of(activeEntity);
    }

    private double associationConfidence(EntityAssociationMatch match) {
        double normalized = match.getScore() / ASSOCIATION_CONFIDENCE_DIVISOR;
        return Math.clamp(normalized, 0.05, 1.0);
    }

    private void refreshActiveEntityTextSearch(ActiveEntity activeEntity) {
        ImpressionSearchTarget target = new ImpressionSearchTarget(
                ImpressionSearchTarget.Type.ACTIVE_ENTITY,
                activeEntity.getRuntimeId()
        );
        textSearch.removeByTarget(target);
        for (ImpressionSearchDocument document : ImpressionSearchDocuments.INSTANCE.fromActiveEntity(activeEntity)) {
            textSearch.upsert(document);
        }
    }

    /**
     * Refresh every index derived from a known entity after mutation.
     */
    private void refreshKnownEntityIndexes(Entity entity) {
        vectorIndex.sync(entity);
        refreshKnownEntityTextSearch(entity);
    }

    private void syncBoundActiveEntitySubjects(Entity entity) {
        List<ActiveEntity> boundEntities;
        synchronized (activeEntities) {
            boundEntities = activeEntities.stream()
                    .filter(activeEntity -> entity.getUuid().equals(activeEntity.getBoundEntityUuid()))
                    .toList();
        }

        boundEntities.forEach(activeEntity -> {
            activeEntity.updateSubject(entity.getSubject());
            refreshActiveEntityTextSearch(activeEntity);
        });
    }

    /**
     * Replace text-search documents for one known entity.
     */
    private void refreshKnownEntityTextSearch(Entity entity) {
        ImpressionSearchTarget target = new ImpressionSearchTarget(
                ImpressionSearchTarget.Type.ENTITY,
                entity.getUuid()
        );
        textSearch.removeByTarget(target);
        for (ImpressionSearchDocument document : ImpressionSearchDocuments.INSTANCE.fromEntity(entity)) {
            textSearch.upsert(document);
        }
    }

    private void rebuildTextSearch() {
        List<ImpressionSearchDocument> documents = new ArrayList<>();
        knownEntitiesByUuid.values().forEach(entity ->
                documents.addAll(ImpressionSearchDocuments.INSTANCE.fromEntity(entity))
        );
        synchronized (activeEntities) {
            activeEntities.forEach(activeEntity ->
                    documents.addAll(ImpressionSearchDocuments.INSTANCE.fromActiveEntity(activeEntity))
            );
        }
        textSearch.rebuild(documents);
    }

    @Override
    public @NotNull Path statePath() {
        return Path.of("core", "impression.json");
    }

    @Override
    public void load(@NotNull JSONObject state) {
        JSONArray entityArray = state.getJSONArray("entities");
        if (entityArray == null) {
            return;
        }

        knownEntitiesByUuid.clear();
        for (int i = 0; i < entityArray.size(); i++) {
            JSONObject entityObject = entityArray.getJSONObject(i);
            if (entityObject == null) {
                continue;
            }

            String uuid = entityObject.getString("uuid");
            String subject = entityObject.getString("subject");
            if (uuid == null || uuid.isBlank() || subject == null || subject.isBlank()) {
                continue;
            }

            Entity entity = new Entity(uuid, subject);
            entity.register();
            entity.load();
            vectorIndex.sync(entity);
            knownEntitiesByUuid.put(uuid, entity);
        }
        rebuildTextSearch();
    }

    @Override
    public @NotNull State convert() {
        State state = new State();

        List<StateValue.Obj> entities = knownEntitiesByUuid.values().stream()
                .map(entity -> StateValue.obj(Map.of(
                        "uuid", entity.getUuid(),
                        "subject", entity.getSubject()
                )))
                .toList();

        state.append("entities", StateValue.arr(entities));
        return state;
    }
}
