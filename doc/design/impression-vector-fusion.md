# Impression Vector Fusion Plan

## Context

Current `ImpressionCore.projectEntity` already connects text recall to active entity projection:

```text
input
-> SimpleTextSearch.search(input)
-> group document hits by ImpressionSearchTarget
-> aggregate into EntityAssociationMatch
-> resolve ACTIVE_ENTITY or ENTITY target
-> append EntityEvidence
-> refresh active entity text-search documents
```

This gives the Impression module a first explainable recall path. Vector recall should not replace this path. It should become another recall signal that is fused with text recall before projection.

## Why not implement vector fusion immediately

Vector fusion is a recall-source enhancement, not the next foundation step.

Before adding more recall sources, the module still needs a clearer organization pipeline:

- how an unmatched input becomes a new `ActiveEntity`;
- how runtime evidence is accumulated, merged, or decayed;
- how an `ActiveEntity` is rolled into a long-term `Entity`;
- how extracted features and impressions update known entities;
- when `textSearch` and `vectorIndex` are refreshed after entity updates.

Unmatched entity creation and `ActiveEntity` rolling are closely related: both decide how temporary evidence becomes a stable entity-level impression. They should be considered as one organization chain rather than two unrelated features.

## Target shape

Future `projectEntity` should have this shape:

```text
input
-> text recall signals
-> vector recall signals
-> normalize scores
-> fuse signals by ImpressionSearchTarget
-> resolve or create ActiveEntity
-> append evidence
-> refresh runtime indexes
```

The later half should stay shared. Text recall, vector recall, relation recall, and recency recall should all produce association signals. Projection should not depend on which recall source produced a match.

## First vector scope

The first vector implementation should only recall long-term `ENTITY` targets.

Reason:

- `ImpressionVectorIndex` already syncs known `Entity` data.
- Known entities have relatively stable features and impressions.
- Active entity evidence changes frequently; embedding every new evidence item would add update cost and lifecycle complexity too early.

So the first vector target should be:

```text
Entity feature / impression vector
-> ImpressionSearchTarget(Type.ENTITY, entityUuid)
```

Later, after the active entity organization chain is stable, active evidence vectors can be added as:

```text
ActiveEntity evidence / projected feature / projected impression vector
-> ImpressionSearchTarget(Type.ACTIVE_ENTITY, runtimeId)
```

## Signal model

`EntityAssociationMatch` is currently text-oriented because it stores `List<ImpressionSearchHit>`.

For fusion, introduce a source-neutral signal model:

```kotlin
data class EntityAssociationSignal(
    val target: ImpressionSearchTarget,
    val source: Source,
    val score: Double,
    val reason: String,
    val textHit: ImpressionSearchHit? = null,
    val vectorHit: ImpressionVectorHit? = null,
) {
    enum class Source {
        TEXT,
        VECTOR,
        RELATION,
        RECENCY
    }
}
```

Then change or extend `EntityAssociationMatch` toward:

```kotlin
data class EntityAssociationMatch(
    val target: ImpressionSearchTarget,
    val score: Double,
    val signals: List<EntityAssociationSignal> = emptyList(),
)
```

This keeps fusion explainable. A match can still tell the model or logs why an entity was recalled.

## Score normalization

Text search score and vector similarity should not be added directly.

Text search currently produces an internal score based on token hits, coverage, exact phrase bonus, field bonus, and document weight. Vector search is usually cosine-like similarity. Normalize both into association-strength-like values before fusion.

Possible first normalization:

```text
textScore01 = clamp(textScore / 5.0, 0.0, 1.0)

vectorScore01 =
  similarity < 0.55 -> 0.0
  otherwise -> clamp((similarity - 0.55) / 0.35, 0.0, 1.0)
```

The constants are placeholders. They should be tuned with tests and logs.

## Fusion rule

Use strong-hit priority with multi-source support, not simple averaging.

A first rule can be:

```text
targetScore =
  max(bestTextScore, bestVectorScore * 0.9)
  + sameTargetCrossSourceBonus
  + supportingSignalBonus
```

Suggested behavior:

- direct subject or phrase text match should beat vague vector similarity;
- vector recall should recover semantically related entities when text recall is weak or empty;
- if text and vector both hit the same target, the target should receive a small confidence boost;
- long documents or many weak signals should not dominate a single strong subject/evidence hit.

## Execution strategy

First implementation can be conservative:

```text
always run TextSearch
run VectorSearch only when:
  - text recall is empty; or
  - top text match confidence is low; or
  - input is long and semantic rather than name-like
```

If the embedding model is local and cheap enough, this can later become parallel text + vector recall.

## Implementation phases

### Phase 1: organization chain first

Implement before vector fusion:

- unmatched input -> new `ActiveEntity` candidate;
- active evidence update and dedup/merge rules;
- active entity rolling into known `Entity`;
- known entity feature/impression update;
- index refresh after entity updates.

### Phase 2: signal abstraction

Introduce `EntityAssociationSignal` and make text hits convert into signals.

Keep current behavior equivalent after refactor.

### Phase 3: long-term entity vector recall

Add vector recall only for known `Entity` targets:

```text
input embedding
-> ImpressionVectorIndex.search(...)
-> vector hits
-> EntityAssociationSignal(source = VECTOR)
-> fuse with text signals
```

### Phase 4: active entity vector recall

Only after active entity lifecycle is stable:

- vectorize active evidence or projected features;
- update active vector index when evidence changes;
- fuse `ACTIVE_ENTITY` vector hits with text hits.

## Non-goals for first vector pass

Do not start with:

- vectorizing every raw evidence item immediately;
- replacing text search ranking;
- using vector score as direct `associationConfidence` without normalization;
- adding opaque fusion that cannot explain why an entity was recalled;
- expanding `projectEntity` into a large source-specific method.

The intended direction is: multiple recall sources produce explainable signals, then `ImpressionCore` performs one shared entity projection flow.
