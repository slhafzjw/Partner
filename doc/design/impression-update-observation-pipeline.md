# Impression Update Observation Pipeline

## 背景

当前 `ImpressionUpdater` 已经接入 `AfterRolling`，并形成了第一版更新闭环：

```text
RollingResult
  -> ImpressionUpdateContext
  -> ImpressionUpdatePlanner
  -> ImpressionUpdatePlanValidator
  -> ImpressionUpdatePlanApplier
```

这一版验证了 rolling 后自动更新长期印象的主链路是可行的：Planner 生成更新计划，Validator 做安全校验，Applier 只执行 `CONFIRMED` 计划并通过 `CognitionCapability` mutation API 落地。

但当前 Planner 直接输出最终 `ImpressionUpdatePlan`，这会让 LLM 过早承担稳定身份和写入决策：

* 它既要从 rolling 证据中判断“观察到了什么”；
* 又要判断“应该更新哪个 known entity / 是否创建新 entity”；
* 还要直接产出 mutation patch。

其中后两者更适合由代码侧的聚合、实体解析、上下文补充和校验流程处理，不应该完全交给最初的 observation planner。

## 核心边界

新的设计目标是将“观察”和“更新”拆开：

```text
ObservationPlanner 负责从证据中抽取观察。
Aggregator 负责合并重复观察。
Resolver 负责将合并观察解析为针对具体 Entity 的更新计划。
Validator 负责安全确认。
Applier 负责落库。
```

也就是说，Observation 层不是“去掉 uuid 的 ImpressionUpdatePlan”。

Observation 层只表达：

```text
从 rolling 证据和 active entity 中，观察到了某个实体相关的信息。
```

它不表达：

```text
应该更新哪个长期实体；
应该创建哪个实体；
应该写入哪些 mutation patch。
```

`ImpressionPatch`、`FeaturePatch`、`RelationPatch`、`UpdateExistingStep`、`CreateEntityStep` 只应出现在 Resolver 之后产生的 `ImpressionUpdatePlan` 中。

## 目标流程

目标管线如下：

```text
RollingResult + ActiveEntityBatch
  -> ImpressionObservationPlanner
  -> ImpressionEntityObservation collection

All Observations + KnownEntityIdentity index
  -> ImpressionObservationAggregator
  -> Merged ImpressionEntityObservation collection

Merged Observations + related Entity snapshots
  -> ImpressionObservationResolver
  -> ImpressionUpdatePlan fragments

All ImpressionUpdatePlan fragments
  -> ImpressionUpdatePlanValidator
  -> ImpressionUpdatePlanApplier
```

其中：

* `ImpressionObservationPlanner` 只从 rolling evidence 和 active entity batch 中抽取观察；
* `ImpressionObservationAggregator` 结合轻量 known identity index 合并重复观察、归并 evidence、过滤弱观察；
* `ImpressionObservationResolver` 接收合并后的观察与相关完整 Entity snapshot，产出针对该 Entity 的 `ImpressionUpdatePlan` fragment；
* `ImpressionUpdatePlanValidator` 对所有 fragments 合并后的最终计划做全局校验；
* `ImpressionUpdatePlanApplier` 继续复用现有落库逻辑。

## Observation 模型

第一阶段只需要两个核心模型。

```kotlin
data class ImpressionEntityObservation @JvmOverloads constructor(
    val proposedSubject: String,
    val aliases: List<String> = emptyList(),
    val impressions: Map<String, Int> = emptyMap(),
    val features: Map<String, Int> = emptyMap(),
    val relations: Map<String, Map<String, Int>> = emptyMap(),
    val sourceActiveRuntimeIds: List<String> = emptyList(),
    val evidenceSnippets: List<String> = emptyList(),
    val reason: String? = null,
)
```

字段语义：

* `proposedSubject`：观察层实体名，不是最终 canonical subject；
* `aliases`：证据中出现的别名或称呼；
* `impressions`：观察到的长期印象文本，`Int` 表示观察权重或聚合计数；
* `features`：观察到的特征文本，`Int` 表示观察权重或聚合计数；
* `relations`：`target -> relation text -> weight/count`；
* `sourceActiveRuntimeIds`：观察来自哪些 active runtime entity；
* `evidenceSnippets`：支持该观察的证据片段；
* `reason`：Planner 或聚合阶段给出的简短理由，仅用于审计，不作为执行许可。

```kotlin
data class KnownEntityIdentity @JvmOverloads constructor(
    val entityUuid: String,
    val subject: String,
    val aliases: List<String> = emptyList(),
)
```

`KnownEntityIdentity` 是轻量身份索引，只包含稳定身份字段，不携带 impressions、features、relations 等完整语义内容。

它主要用于 Aggregator 判断不同 observation 是否可能指向同一 known entity，避免仅靠 `proposedSubject` 做粗糙合并。

真正生成 patch 时，需要在聚合完成后取得相关完整 Entity snapshot，再由 Resolver 结合 Entity 当前状态制定更新计划。

## Batch 语义

Active entities 可以按上下文预算分批交给 ObservationPlanner：

```text
RollingResult + ActiveEntityBatch
  -> ImpressionEntityObservation collection
```

这里的 batch size 只是上下文预算，不是语义覆盖上限。

所有 batch 的 observations 必须先聚合，再进入 Resolver 阶段。

不允许：

```text
batch observation -> batch update plan -> batch apply
```

因为这样会让更新顺序影响长期状态，并且难以做全局冲突校验。

允许：

```text
observation batch 1 -> observations
observation batch 2 -> observations
observation batch 3 -> observations

all observations -> aggregate -> resolve -> plan fragments
all plan fragments -> global validation -> apply
```

Resolver 阶段如果上下文较大，也可以按合并后的 observation / related entity 分批产出 `ImpressionUpdatePlan` fragments。

但所有 fragments 仍应统一汇总后再由 Validator 校验和确认，不应分批直接落库。

## Aggregator

Aggregator 的输入是所有 ObservationPlanner 产出的 `ImpressionEntityObservation`，以及轻量 `KnownEntityIdentity` index。

Aggregator 负责：

* 丢弃空 observation；
* normalize subject / alias；
* 合并明显重复的 observation；
* 合并同一 active runtime entity 来源的重复观察；
* 借助 known subject / alias 合并指向同一 known entity 的观察；
* 合并 impression / feature / relation 的计数；
* 合并 evidence snippets 与 source active runtime ids；
* 过滤无证据、过弱、模板化或过长的观察。

Aggregator 不生成 `ImpressionUpdatePlan`，也不落库。

它的输出仍然是合并后的 observation collection。

## Resolver

Resolver 的输入是：

```text
Merged ImpressionEntityObservation
  + related Entity snapshots
```

Resolver 的职责是将合并后的观察解析成针对具体 Entity 的更新计划。

它可以根据完整 Entity 当前状态判断：

* 观察到的 impression 是否已经存在；
* 观察到的 feature 是否重复或需要更新；
* 观察到的 relation target 是否能解析；
* 观察是否足够强，可以创建新 entity；
* 观察是否 ambiguous，需要 reject / postpone；
* 观察是否只适合保留为 evidence，不进入 mutation plan。

Resolver 输出的是 `ImpressionUpdatePlan` fragment，状态应保持为 `PREPARED`。

Resolver 不直接确认计划，不直接落库。

## UpdatePlan 复用

新管线继续复用现有 mutation plan：

* `ImpressionUpdatePlan`
* `UpdateExistingStep`
* `CreateEntityStep`
* `ImpressionPatch`
* `FeaturePatch`
* `AliasPatch`
* `SubjectPatch`
* `RelationPatch`
* `ImpressionUpdatePlanValidator`
* `ImpressionUpdatePlanApplier`

也就是说，新管线不推翻现有 `ImpressionUpdatePlan`，而是在它前面增加 observation / aggregation / resolution 层。

旧链路的问题不是 `ImpressionUpdatePlan` 本身，而是 Planner 太早产出了它。

新的边界是：

```text
ObservationPlanner 不产 UpdatePlan。
Resolver 才产 UpdatePlan。
Validator 才确认 UpdatePlan。
Applier 才执行 UpdatePlan。
```

## Validator

Validator 从基础结构校验升级为 context-aware 校验。

它接收所有 Resolver 产出的 plan fragments 汇总后的最终 `ImpressionUpdatePlan`，并做全局校验：

* `UpdateExistingStep.entityUuid` 必须存在；
* `CreateEntityStep.subject` 不应与 known subject / alias 冲突；
* relation target 必须能解析到已知实体或本批新建实体；
* patch 文本必须非空且长度有限；
* ambiguous / postponed observation 不允许进入落库计划；
* final plan 不允许空 step；
* LLM / Resolver 只能产 `PREPARED`，不能直接产 `CONFIRMED`。

Validator 通过后，由代码侧构造 `CONFIRMED` plan，再交给 Applier。

## Applier

Applier 继续只执行 `CONFIRMED` 的 `ImpressionUpdatePlan`。

落库仍通过 `CognitionCapability` mutation API 执行，不绕过 `ImpressionCore`。

如果最终计划较大，可以在 Applier 内部按资源或事务边界分批执行；但这只是执行层 batch，不应影响前面的 observation、aggregation、resolution 和 validation 语义。

## 分阶段落地

### Phase 1：观察模型

* 新增 `ImpressionEntityObservation`；
* 新增 `KnownEntityIdentity`；
* 保留现有 `ImpressionUpdatePlan` / Patch / Applier；
* 不改主链路。

### Phase 2：ObservationPlanner

* 新增 `ImpressionObservationPlanner`；
* 输入为 `RollingResult + ActiveEntityBatch`；
* 输出 `ImpressionEntityObservation` collection；
* Planner 不输出 known entity uuid；
* Planner 不决定 update/create；
* Planner 不输出 mutation patch。

### Phase 3：Aggregator

* 聚合所有 batch observations；
* 使用 `KnownEntityIdentity` 辅助 subject / alias 归并；
* 输出合并后的 observation collection；
* 不生成 `ImpressionUpdatePlan`。

### Phase 4：Resolver

* 根据合并后的 observation 找到相关 Entity snapshot；
* 结合 Entity 当前状态生成 `ImpressionUpdatePlan` fragments；
* fragment 状态保持 `PREPARED`；
* 不确认、不落库。

### Phase 5：Validator 升级

* 汇总所有 plan fragments；
* 做 context-aware / entity-aware / relation-aware 校验；
* 校验通过后由代码侧构造 `CONFIRMED` final plan。

### Phase 6：替换主流程

最终将 `ImpressionUpdater.consume()` 改为：

```text
buildObservationContexts(result)
  -> planner per active entity batch
  -> aggregate observations
  -> load related entity snapshots
  -> resolve to update plan fragments
  -> merge fragments
  -> validate
  -> confirm
  -> apply
```

## 非目标

第一版不做：

* 向量召回；
* 全库 impressions / features 语义扫描；
* LLM 实体合并；
* 多实体复杂冲突解决；
* 自动删除或降权旧 impression；
* 基于弱证据的大规模新实体创建；
* batch 级直接 apply。

这些应在 observation pipeline 稳定后单独设计。

## 当前结论

本设计的核心是：

```text
Observation 不是 UpdatePlan。
Observation 只记录证据化观察。
UpdatePlan 在合并观察并取得相关 Entity 后生成。
Applier 只执行经过 Validator 确认的最终计划。
```

这样可以保留 LLM 对自然语言证据的抽取能力，同时避免让最初的 Planner 直接承担长期实体身份和数据库 mutation 决策。
