# First Encounter Module / 初见模块设计草案

## 背景

Partner 当前已经不是“不能跑”的项目，但用户面对一个新的 agent 时，仍然会有明显的启动成本。

这个启动成本不完全来自工程状态，而来自互动预期的不确定：

- 不知道该怎么和它说话；
- 不知道它知道什么、不知道什么；
- 不知道它会不会误解用户；
- 不知道它能不能被纠正；
- 不知道纠正之后会不会真正改变后续行为。

因此，Partner 需要一个“初见模块”。

它解决的不是程序启动问题，而是关系和预期建立问题。

## 定位

初见模块不应该只是 `InitModule`。

`InitModule` 更像加载配置、初始化资源、检查运行状态；而初见模块面对的是用户第一次或重新面对 Partner 时的交互问题。

因此，代码层可以命名为：

```text
FirstEncounterModule
```

产品/概念层称为：

```text
初见模块
```

它的职责是：

> 在新用户、长时间未使用、上下文断裂、版本升级，或用户主动询问“你现在知道我什么”时，组织一次清醒、温和、可校准的开场。

## 与 Impression 模块的关系

初见模块应当依托 Impression，但不属于 ImpressionCore。

边界如下：

```text
ImpressionCore
  负责存储、召回、更新关于用户、agent 自身、关系契约、项目上下文等印象。

FirstEncounterModule
  负责判断是否进入初见/重逢模式，并将召回的印象组织成本轮对话可用的 EncounterFrame。

EncounterState
  负责记录初见流程是否已经完成，以及哪些环节已经向用户公开。
```

也就是说：

> Impression 负责“我对你有什么印象”。
> FirstEncounterModule 负责“第一次见面时，我该如何使用这些印象”。

不应把开场策略、纠错协议、对话引导逻辑直接塞进 ImpressionCore，否则记忆模块会被迫承担表达和流程控制职责。

## 触发场景

初见模块可以在以下场景触发：

- 新用户第一次进入；
- 当前 session 没有足够上下文；
- 长时间未使用后重新进入；
- Partner 发生较大版本升级；
- Impression 召回结果置信度较低；
- 用户主动询问：
  - “你知道我什么？”
  - “你现在能做什么？”
  - “我该怎么和你说话？”
  - “你是不是还记得之前的事？”
- 系统检测到当前对话存在明显预期不稳定，例如用户多次纠正 agent 的语气、事实或任务边界。

## 核心流程

推荐流程：

```text
User Input
  ↓
InteractionHub
  ↓
EncounterDetector
  ↓
ImpressionRecaller
  ↓
FirstEncounterModule
  ↓
EncounterFrame
  ↓
PromptContributor / AppendPrompt
  ↓
CoreModel Reply
  ↓
ImpressionUpdater
```

其中：

1. `EncounterDetector` 判断是否需要进入初见/重逢模式；
2. `ImpressionRecaller` 召回相关印象；
3. `FirstEncounterModule` 将召回结果整理成 EncounterFrame；
4. `PromptContributor` 将 EncounterFrame 注入模型上下文；
5. 对话结束后，`ImpressionUpdater` 根据用户反馈更新印象。

## EncounterFrame

`EncounterFrame` 是初见模块的核心输出。它不是长期记忆，而是本轮对话使用的临时认知框架。

示例结构：

```kotlin
data class EncounterFrame(
    val mode: EncounterMode,
    val knownAboutUser: List<ImpressionProjection>,
    val knownAboutSelf: List<ImpressionProjection>,
    val knownAboutRelationship: List<ImpressionProjection>,
    val uncertainty: List<String>,
    val correctionProtocol: CorrectionProtocol,
    val openingStrategy: OpeningStrategy
)
```

其中：

- `mode`：当前是初见、重逢、版本升级后再介绍，还是用户主动询问；
- `knownAboutUser`：关于用户的可靠印象；
- `knownAboutSelf`：Partner 对自身能力和边界的描述；
- `knownAboutRelationship`：关于互动方式、纠错方式、语气偏好等印象；
- `uncertainty`：当前不能确定的部分；
- `correctionProtocol`：用户如何纠正 Partner；
- `openingStrategy`：本次开场应采用的表达策略。

## Impression Subject 建议

为了支持初见模块，Impression 可以支持一些特殊 subject：

```text
user
agent_self
relationship_contract
interaction_preference
project_context
```

例如：

```text
user:
- 用户偏好技术回答直接，不喜欢客服腔。
- 用户面对陌生 agent 时会在意互动预期是否稳定。
- 用户更容易接受从一个小切口开始推进。

agent_self:
- Partner 当前不是完全成熟的 agent。
- Partner 应公开自己的已知、未知和不确定。
- Partner 不应该在缺少依据时假装熟悉用户。

relationship_contract:
- 用户可以直接纠正 Partner。
- Partner 需要区分事实错误、语气偏差、理解偏差和任务边界偏差。
- 纠正应作为后续 impression 更新的重要信号。
```

## 初见开场策略

初见模块不应一上来问很多问题，也不应假装已经充分了解用户。

更合适的开场结构是：

```text
我现在对你还没有足够稳定的了解。

我会先说明：
- 我目前知道什么；
- 我不知道什么；
- 你可以怎么纠正我；
- 我会如何处理这些纠正。

接下来我们可以从一个很小的任务开始。
```

在 prompt 中可组织为：

```text
你正在与用户进行初见/重逢式对话。

你目前可靠知道：
- 用户希望技术讨论直接、少废话；
- 用户对陌生 agent 的互动预期尚未建立；
- 用户不喜欢 agent 在缺少依据时假装熟悉。

你应该主动说明：
- 你知道什么；
- 你不知道什么；
- 用户可以如何纠正你；
- 你会如何处理纠正。

不要一次性问很多问题。
不要假装亲近。
先从一个很小的任务或对话入口开始。
```

## EncounterState

初见模块需要少量流程状态，但这些状态不一定属于 Impression。

示例：

```kotlin
data class EncounterState(
    val hasIntroducedSelf: Boolean,
    val hasShownKnownUnknown: Boolean,
    val hasExplainedCorrectionProtocol: Boolean,
    val firstEncounterCompleted: Boolean,
    val lastEncounterVersion: String?
)
```

这些状态表示流程是否完成，而不是关于用户的长期印象。

真正应该进入 Impression 的，是对用户、关系、互动方式的理解，例如：

```text
用户面对新的 agent 时，会担心互动预期不稳定。
用户希望 agent 明确边界，而不是一上来装熟。
用户能接受通过纠正来校准 agent。
```

## 最小实现方案

第一版可以很轻，不需要完整工程化。

建议步骤：

1. 新增 `FirstEncounterPromptContributor`；
2. 新增 `EncounterDetector`，先用简单规则判断是否触发；
3. 从 `ImpressionRecaller` 召回 `user`、`agent_self`、`relationship_contract`、`interaction_preference`、`project_context` 相关印象；
4. 生成一个简化版 `EncounterFrame`；
5. 将 EncounterFrame 注入 AppendPrompt；
6. 用户纠正后，将纠正内容作为 evidence 交给 ImpressionUpdater。

第一版不需要复杂策略模型，规则足够：

```text
新 session + 低熟悉度 → 初见模式
长时间未使用 + 有历史 impression → 重逢模式
用户主动询问已知/未知 → 自我公开模式
多次纠正 → 关系校准模式
```

## 不做什么

初见模块第一版不做以下内容：

- 不做完整 onboarding 表单；
- 不一次性询问大量偏好；
- 不把用户画像写死；
- 不假装已经理解用户；
- 不替代 ImpressionCore；
- 不直接负责长期记忆写入；
- 不在每轮对话中重复自我介绍。

它只负责在关系尚未稳定时，提供一个清醒、可纠正、可继续的开场。

## 价值

初见模块的价值不只是“第一次使用体验更好”。

它实际上补上了 Partner 作为 agent 的一个关键能力：

> 在上下文断裂、长期未见、版本变化或记忆不确定时，仍然能让用户知道该如何继续与它相处。

这使 Partner 不只是一个能运行的程序，而是一个能够建立互动预期、暴露不确定性、接受校准，并逐步形成稳定关系的 agent。
