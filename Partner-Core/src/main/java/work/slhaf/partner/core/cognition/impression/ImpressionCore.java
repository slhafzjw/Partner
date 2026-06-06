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

    @CapabilityMethod
    public void updateRelation() {
    }

    @CapabilityMethod
    public void updateImpression() {
    }

    @CapabilityMethod
    public void showImpressions() {
    }

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
            case ENTITY -> activateKnownEntity(target.getId());
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

    private Optional<ActiveEntity> activateKnownEntity(String uuid) {
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
