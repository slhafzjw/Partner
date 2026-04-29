# MetaAction 与行动链建模

本文说明 Partner 行动系统中 `MetaAction`、`ExecutableAction` 与行动链的建模方式。

行动系统把“要做什么”拆成两个层级：

- `ExecutableAction` 表示一次完整任务，包含来源、原因、描述、状态、阶段、结果与执行历史。
- `MetaAction` 表示行动链中的最小可执行单元，描述一个具体行动程序及其调用参数、执行结果。

因此，一次行动不是一个扁平 tool call，而是一个可阶段化、可恢复、可纠偏、可调度的任务对象。

## 对象层级

```text
Action
  ├─ ExecutableAction
  │   ├─ ImmediateExecutableAction
  │   └─ SchedulableExecutableAction
  └─ StateAction

ExecutableAction
  ├─ tendency
  ├─ actionChain: Map<Int, List<MetaAction>>
  ├─ stageDescriptions: Map<Int, String>
  ├─ executingStage
  ├─ history
  └─ result
```

`Action` 是最上层的抽象，负责承载通用生命周期字段。`ExecutableAction` 表示真正包含行动链的任务。`StateAction` 不包含 `MetaAction` 链，而是用于定时或周期性触发状态更新、普通逻辑调用等系统行为。

## MetaAction

`MetaAction` 是行动链中的单一元素，封装调用外部行动程序所需的基本信息。

核心字段包括：

- `name`：行动名称，用于标识行动程序。
- `type`：行动程序类型，目前包括 `MCP`、`ORIGIN` 和 `BUILTIN`。
- `location`：行动所在位置。对 MCP 来说通常是 MCP client id；对 ORIGIN 来说是磁盘路径；对 BUILTIN 来说固定为 `builtin`。
- `launcher`：启动器或解释器，主要用于 `ORIGIN` 类型。
- `io`：是否偏 IO 密集，用于执行器选择线程池。
- `params`：执行前由参数提取器填充的调用参数。
- `result`：单个 `MetaAction` 的执行结果，包含 `WAITING`、`SUCCESS`、`FAILED` 三种状态。

`MetaAction` 的 `key` 由 `location::name` 组成。行动规划阶段只会引用真实存在的 action key，执行阶段再通过该 key 加载行动元信息、提取参数并提交给 runner。

## MetaAction 类型

当前实现中 `MetaAction.Type` 分三类：

- `MCP`：调用已注册的 MCP 工具，可以对应本地或远程服务。
- `ORIGIN`：临时生成或持久化的行动程序，通常需要启动器或解释器。
- `BUILTIN`：由本地内置注册表直接执行的行动。

这个类型划分让行动链只关心“要调用哪个行动单元”，不把执行通道细节硬编码进 planner。真正的路由由 action runner / capability 层负责。

## ExecutableAction

`ExecutableAction` 是一次完整行动任务的结构化容器。

核心字段包括：

- `uuid`：行动实例 id。
- `source`：行动来源，通常关联用户或触发源。
- `reason`：为什么要执行该行动。
- `description`：行动描述，用于人类可读说明和上下文反馈。
- `tendency`：上游 `ActionExtractor` 提取出的行动倾向。
- `status`：任务级状态。
- `actionChain`：阶段化行动链。
- `stageDescriptions`：每个阶段的执行目标说明。
- `executingStage`：当前执行阶段。
- `history`：每个阶段已经执行过的 `HistoryAction`。
- `result`：最终行动结果。

它把“行动为什么存在”“当前执行到哪里”“每一步做了什么”“最终结果是什么”放在同一个对象里，便于调度、恢复、纠偏和上下文反馈。

## 行动链结构

行动链使用 `Map<Int, List<MetaAction>>` 表示。

- `Int` 是阶段序号。
- 同一阶段中的 `List<MetaAction>` 表示该阶段下的一组行动单元。
- 阶段之间按序推进。
- 同一阶段内的多个 `MetaAction` 可以被执行器并发提交。

示意：

```text
Stage 1
  ├─ MetaAction A
  └─ MetaAction B

Stage 2
  └─ MetaAction C

Stage 3
  ├─ MetaAction D
  └─ MetaAction E
```

这种结构适合表达“先收集事实，再执行修改，最后验证结果”一类任务。规划阶段只负责形成阶段与行动单元，执行阶段负责提取参数、提交行动、收集结果并推进阶段。

## 阶段描述

`stageDescriptions` 为每个 stage 提供自然语言目标说明。它不是用户回复文案，也不是完整执行计划，而是给执行器和参数提取器使用的阶段目标。

例如：

```text
1 -> "读取当前配置文件"
2 -> "根据目标修改配置项"
3 -> "运行检查命令确认修改有效"
```

执行器在为某个 `MetaAction` 提取参数时，会把当前行动描述和当前阶段描述一起传给参数提取模块，帮助它生成更贴近阶段目标的参数。

## 依赖修正

行动评估结果中的 `primaryActionChain` 并不直接成为最终行动链。`ActionPlanner` 会在装配阶段做两类修正：

1. **顺序归一化**：把阶段序号修正为从 1 开始依次递增。
2. **前置依赖补齐**：读取 `MetaActionInfo.preActions` 和 `strictDependencies`，必要时把严格依赖的前置 action key 插入到当前阶段之前。

这意味着 LLM 评估结果可以表达主要行动链，但最终进入执行器的链会经过系统能力层校验和依赖补齐。若 action key 不存在、元信息加载失败或依赖无法处理，planner 会放弃构造该行动。

## 即时行动与计划行动

`ExecutableAction` 有两个主要实现：

- `ImmediateExecutableAction`：立即进入执行器。
- `SchedulableExecutableAction`：实现 `Schedulable`，先进入调度器，到期后再交给执行器。

二者共享同一套行动链结构。差异只在执行时机和生命周期维护：计划行动执行后会记录 `ScheduleHistory`，并重置阶段、参数和单步结果，以便周期性任务下次继续运行。

## StateAction

`StateAction` 也是 `Action`，同时实现 `Schedulable`，但它不包含 `MetaAction` 链。

它的作用是承载定时或周期性触发的系统逻辑，例如：

- 周期性状态更新。
- 一次性逻辑调用。
- 即时行动 watcher。

`StateAction.Trigger` 目前包括：

- `Update<T>`：对某个状态源执行更新函数。
- `Call`：执行普通逻辑调用。

执行器收到 `StateAction` 时不会走行动链，而是直接触发 `trigger.onTrigger()`，并把触发事件写入上下文。

## 建模边界

行动建模层只负责把行动表示清楚，不负责完成所有执行细节。

它不负责：

- 判断用户意图是否应该行动化。
- 选择所有参数值。
- 执行 MCP / BUILTIN / ORIGIN 行动。
- 判断执行是否成功。
- 生成最终用户回复。

这些分别属于行动提取、行动评估、参数提取、行动执行、反馈闭环和沟通模块。

## 设计取向

`MetaAction` 与行动链建模的核心目标是把“系统要做的事”变成结构化任务，而不是把一次行动压缩成不可追踪的工具调用。

这种设计带来几个好处：

- 可以表达多阶段任务。
- 可以为每个阶段保留目标描述。
- 可以记录每个阶段的执行历史。
- 可以在失败或结果不满足目标时插入纠偏行动。
- 可以把同一行动链用于立即执行或未来调度。
- 可以把行动状态写入 `ContextWorkspace`，供后续认知和沟通使用。
