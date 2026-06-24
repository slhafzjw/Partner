# Impression Update Observation Pipeline / 印象更新观察管线设计草案

## 背景

当前 `ImpressionUpdater` 已经接入 `AfterRolling`，并形成了第一版更新闭环：

```text
RollingResult
  -> ImpressionUpdateContext
  -> ImpressionUpdatePlanner
  -> ImpressionUpdatePlanValidator
  -> ImpressionUpdatePlanApplier
```

这一版验证了 rolling 后自动更新 Impression 的主链路是可行的：Planner 生成计划，Validator 做基础校验，Applier 只接受 `CONFIRMED` 计划并通过 `CognitionCapability` mutation API 落地。

但当前 Planner 直接输出最终 mutation plan：

```text
UpdateExistingStep(entityUuid, patch)
CreateEntityStep(subject, patches...)
```

这会让 LLM 过早承担稳定身份决策：它既要判断“这次 rolling 里出现了什么实体观察”，又要判断“该更新哪个 known entity / 是否创建新 entity”。后者更适合由代码侧 identity resolver 和 validator 处理，不应该完全交给模型。

## 核心问题

当前方案主要有三个隐患。

### 1. Planner 不应直接决定 known entity uuid

模型可以从证据中抽取实体观察，但它不适合直接决定稳定存储层 uuid。

即使 prompt 给了候选 entity，模型仍可能：

- 编造不存在的 uuid；
- 选择错误的 uuid；
- 把同一个实体拆成多个新实体；
- 对 subject / alias 相近的实体做过早合并。

因此，Planner 的输出应从“最终更新计划”降级为“初始观察计划”。

### 2. KnownEntities 不宜提前整体塞给 Planner

全量 known entity 可能随长期使用不断增长。若每次 rolling 后把所有 known entity 的 subject、alias、impression、feature 都塞给 Planner，会导致：

- 上下文压力随实体数量增长；
- 无关实体污染判断；
- 成本和延迟不稳定；
- 模型在大量候选中发生错误关联。

但是，完全只看少数候选也可能漏掉“其实应更新某个已知实体”的场景。

因此，较合适的边界是：

> Planner 不看全量 known entities；后续 resolver 可以使用轻量的 known entity identity index。

这里的 identity index 只包含确定性身份信息，例如 uuid、subject、alias，而不包含完整 impressions/features/relations。

### 3. 单批 max candidates 不应变成语义丢弃

简单地设置 `maxKnownCandidates` 后截断，会让维护语义变成“只维护前 N 个实体”。

这不适合 Impression 这类长期维护模块。更合理的是：

```text
batch size 是模型上下文预算，不是语义覆盖上限。
```

也就是说，可以把 active entities 分批交给 Planner 提取观察，但最后应聚合所有 batch 的观察，再统一决定更新或创建。

## 目标形态

目标是把 ImpressionUpdater 拆成“观察抽取”和“身份决策/落库”两层：

```text
RollingResult
  -> ActiveEntity batches
  -> Observation Planner per batch
  -> Observation Aggregate
  -> Identity Resolver
  -> Final Plan Builder
  -> Context-aware Validator
  -> Applier
```

其中：

- Planner 只负责提取观察，不直接输出最终 mutation；
- Aggregate 汇总、去重、合并不同 batch 的观察；
- Resolver 使用 known entity identity index 决定 update existing 还是 create entity；
- FinalPlanBuilder 生成现有 `ImpressionUpdatePlan`；
- Validator 做全局安全校验；
- Applier 继续负责 confirmed plan 落地。

## 数据模型建议

### ImpressionObservationPlan

Planner 输出不再是 `ImpressionUpdatePlan`，而是观察层计划：

```kotlin
data class ImpressionObservationPlan(
    val observations: List<ImpressionEntityObservation>,
    val status: ObservationPlanStatus = ObservationPlanStatus.PREPARED,
    val reason: String? = null,
)

enum class ObservationPlanStatus {
    PREPARED,
    REJECTED,
}
```

### ImpressionEntityObservation

实体观察不绑定 known entity uuid，只表达“从证据中看到了什么”：

```kotlin
data class ImpressionEntityObservation(
    val proposedSubject: String,
    val aliases: List<String> = emptyList(),
    val impressions: List<ImpressionPatch> = emptyList(),
    val features: List<FeaturePatch> = emptyList(),
    val relations: List<RelationPatch> = emptyList(),
    val sourceActiveRuntimeIds: List<String> = emptyList(),
    val evidenceSnippets: List<String> = emptyList(),
    val confidence: Double = 1.0,
    val reason: String? = null,
)
```

设计重点：

- `proposedSubject` 是观察层身份名，不一定是最终 canonical subject；
- `sourceActiveRuntimeIds` 记录观察来自哪些 active entity；
- `evidenceSnippets` 保存可审计证据片段；
- `confidence` 表示观察可靠度，不是最终落库许可；
- 观察层允许重复，后续 Aggregate 负责合并。

### KnownEntityIdentity

Resolver 使用轻量身份索引，而不是完整实体上下文：

```kotlin
data class KnownEntityIdentity(
    val entityUuid: String,
    val subject: String,
    val aliases: List<String> = emptyList(),
)
```

后续可能需要在 `CognitionCapability` 增加只读 API：

```java
List<KnownEntityIdentity> showKnownEntityIdentities();
```

这个 API 不暴露 impressions/features/relations，也不允许外部直接修改 entity，只用于 deterministic identity resolution。

## 执行流程

### 1. 构建 active entity batches

从 `cognitionCapability.showEntities()` 获取当前 active entities 及其 bound known entity。

按照 `lastMentionedAt` 或 runtime 顺序划分 batch：

```text
activeEntities
  -> batch 1
  -> batch 2
  -> batch 3
```

这里的 batch size 只是上下文预算，例如每批 8 或 12 个 active entity。它不表示只处理前 N 个，而是所有 active entity 都会被覆盖。

每个 batch 与同一份 `RollingResult` 组成 observation context：

```text
RollingResult + ActiveEntityBatch
  -> ImpressionObservationPlanner
```

### 2. Planner 只产初始观察

Planner 的职责变成：

> 根据本次 rolling 证据和当前 batch 的 active entities，提取可能需要进入长期印象系统的实体观察。

Planner 不允许：

- 输出 known entity uuid；
- 直接决定 update existing / create entity；
- 做实体合并；
- 访问或推断全量 known entity 状态。

它只输出 `ImpressionObservationPlan`。

### 3. Aggregate 汇总所有 batch observation

Aggregate 收集所有 batch 的观察：

```text
ObservationPlan(batch1)
ObservationPlan(batch2)
ObservationPlan(batch3)
  -> ImpressionObservationAggregate
```

Aggregate 负责：

- 丢弃 `REJECTED` 或空 observation；
- normalize subject / alias；
- 合并相同 normalized subject 的观察；
- 合并 alias 重叠的观察；
- 合并 evidenceHash 或 sourceActiveRuntimeIds 高度重合的观察；
- 去掉低置信、无证据、模板化、过长的 patch；
- 生成去重后的独立实体观察集合。

这一阶段仍不落库。

### 4. Identity Resolver 决定更新还是创建

Resolver 使用 `KnownEntityIdentityIndex`，只根据 subject / alias 做轻量身份匹配。

规则第一版可以保守：

```text
exact normalized subject match
  -> update existing

exact normalized alias match
  -> update existing

multiple matched entities
  -> ambiguous, reject / postpone

no match + observation evidence sufficient
  -> create entity

no match + evidence weak
  -> reject / postpone
```

Resolver 不做复杂语义合并，不调用 LLM，不根据 impression 内容强行判断两个实体是否相同。

### 5. FinalPlanBuilder 生成现有 mutation plan

Identity resolution 完成后，才生成真正的 `ImpressionUpdatePlan`：

```text
ResolvedObservation(update entityUuid)
  -> UpdateExistingStep(entityUuid, patch)

ResolvedObservation(create subject)
  -> CreateEntityStep(subject, patches...)
```

这样可以复用现有 `ImpressionUpdatePlanApplier`，不需要把落库 API 全部推翻。

### 6. Validator 做全局校验

Validator 应从基础语法校验升级为 context-aware / identity-aware 校验：

- `UpdateExistingStep.entityUuid` 必须存在；
- `CreateEntityStep.subject` 不能与 known subject / alias 重复；
- `RelationPatch.target` 必须能解析到已知 entity 或本批即将创建的 entity；
- patch 文本必须非空且长度有限；
- `confidence` / `strength` 必须是有限数值并落在合法范围；
- ambiguous resolution 不允许落库；
- final plan 不允许空 step。

### 7. Applier 执行 confirmed plan

Applier 仍只接受 `CONFIRMED` 的 `ImpressionUpdatePlan`，并通过 `CognitionCapability` mutation API 执行。

如果 final plan 较大，应用阶段可以分批执行，但这只是资源与事务层面的 batch，不再影响身份决策。

也就是说：

```text
可以 batch apply finalized steps。
不可以 batch planner -> batch apply。
```

## 与当前实现的关系

当前代码中的 `ImpressionUpdatePlan`、`ImpressionUpdatePlanApplier` 可以保留。

需要调整的是 Planner 前后链路：

```text
当前：
ImpressionUpdatePlanner -> ImpressionUpdatePlan -> Validator -> Applier

目标：
ImpressionObservationPlanner -> ImpressionObservationPlan
  -> ImpressionObservationAggregator
  -> ImpressionIdentityResolver
  -> ImpressionUpdatePlanBuilder
  -> ImpressionUpdatePlanValidator
  -> ImpressionUpdatePlanApplier
```

第一阶段可以不删除当前 Planner，而是先新增 observation pipeline，再逐步迁移 `ImpressionUpdater.consume()`。

## 分阶段落地建议

### Phase 1：写入观察模型与文档

- 新增 `ImpressionObservationPlan`；
- 新增 `ImpressionEntityObservation`；
- 新增 `KnownEntityIdentity`；
- 保留现有 update plan 和 applier；
- 不改主链路。

### Phase 2：ObservationPlanner 替代直接 update planner

- 新增 `ImpressionObservationPlanner`；
- 每批 active entity + rolling result 生成 observation plan；
- Planner prompt 明确不输出 uuid、不决定 update/create。

### Phase 3：Aggregate / Resolver / PlanBuilder

- 聚合所有 batch observations；
- 使用 known identity index 做 subject / alias 级 resolution；
- 转成最终 `ImpressionUpdatePlan`。

### Phase 4：Validator 升级

- Validator 改为接收 final plan + resolution context；
- 增加 uuid、重复 subject/alias、relation target、patch 边界检查。

### Phase 5：替换 `ImpressionUpdater.consume()` 主流程

将主流程改为：

```text
buildObservationContexts(result)
  -> planner per batch
  -> aggregate
  -> resolve
  -> build final plan
  -> validate
  -> confirm
  -> apply
```

## 非目标

第一版不做：

- 向量召回；
- 全库 impressions/features 语义扫描；
- LLM 实体合并；
- 多实体复杂冲突解决；
- 自动删除或降权旧 impression；
- 基于弱证据的大规模新实体创建。

这些应在 observation pipeline 稳定后单独设计。

## 当前结论

本设计的核心边界是：

```text
Planner 负责观察。
Aggregate 负责去重与合并。
Resolver 负责身份决策。
Validator 负责安全确认。
Applier 负责落库。
```

这样可以保留 LLM 对自然语言证据的抽取能力，同时避免让它直接承担稳定实体身份和数据库 mutation 决策。
