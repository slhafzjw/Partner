# Runtime 输入与模块调度

本文说明外部输入如何经由 Gateway 进入 `AgentRuntime`，以及 `AgentRuntime` 如何按 `source` 聚合输入、去重、debounce 并调度 Running module。

## Gateway 与输入提交

Gateway 负责把外部输入转换成 `RunningFlowContext`，再提交给 `AgentRuntime`。`AgentRuntime` 不直接感知 WebSocket 等具体输入协议。

```mermaid
flowchart TD
    A["外部输入\nWebSocket / 其他 Gateway"] --> B["AgentGateway.receive(inputData)"]
    B --> C["parseRunningFlowContext(inputData)"]
    C --> D["AgentRuntime.submit(context)"]

    D --> E["按 context.source 聚合输入"]
    E --> E1["latestContextsBySource[source]\n旧 context 与新 context mergedWith"]
    E --> E2["sourceVersions[source] + 1"]
    E --> E3["sourceQueue 加入 source\n队列内 source 不重复"]

    E3 --> F{"当前是否正在执行同一 source?"}
    F -->|是| G["currentExecutingContext.status.interrupted = true"]
    F -->|否| H["发送 wakeSignal"]

    G --> H
    H --> I["AgentRuntime 后台协程消费 wakeSignal"]
    I --> J["drainQueue()"]
    J --> K["executeSource(source)"]
```

## 模块运行时

`AgentRuntime` 采用按 `source` 聚合的异步执行模型。同一个 `source` 的连续输入会合并为最新的 `RunningFlowContext`；如果新输入到达时该 `source` 正在执行，当前上下文会被标记为 interrupted，当前轮结束后 runtime 会从 Running module 链头重新执行最新合并后的上下文。

```mermaid
flowchart TD
    A["AgentRuntime 后台协程"] --> B["收到 wakeSignal"]
    B --> C["drainQueue()"]
    C --> D["取 sourceQueue.firstOrNull()"]
    D --> E{"是否存在 source?"}
    E -->|否| Z["返回，等待下一次 wakeSignal"]
    E -->|是| F["executeSource(source)"]

    F --> G["awaitDebouncedExecution(source)"]
    G --> G1{"debounceWindow > 0?"}
    G1 -->|是| G2["等待 debounceWindow"]
    G2 --> G3{"sourceVersions 是否变化?"}
    G3 -->|变化| G2
    G3 -->|稳定| H["构造 SourceExecution(context, version)"]
    G1 -->|否| H

    H --> I["标记 currentExecutingSource/context"]
    I --> J["executeTurn(context)"]

    J --> K{"runningModules 是否为空?"}
    K -->|是| L["refreshRunningModules()"]
    K -->|否| M["按 order 遍历 Running modules"]
    L --> M

    M --> N["过滤 maskedModules"]
    N --> O["按 order 分组并排序"]
    O --> P["同一 order 内 coroutineScope + async 并发执行"]
    P --> Q["module.execute(runningFlowContext)"]
    Q --> R{"context.status.interrupted?"}

    R -->|是| S["本轮中断"]
    R -->|否| T["本轮完成"]

    S --> U["检查 latest version"]
    T --> U

    U --> V{"执行期间是否有新输入或版本变化?"}
    V -->|是| W["保留 source\n从链头重新执行最新 merged context"]
    W --> G

    V -->|否| X["清理 latestContextsBySource/sourceQueue/sourceVersions"]
    X --> Z
```

Running module 的执行顺序由 `module.order()` 决定。order 较小的组先执行，同一 order 内的模块并发执行。

```mermaid
flowchart LR
    A["Running modules from AgentContext.modules"] --> B["过滤 maskedModules"]
    B --> C["按 module.order() 分组"]
    C --> D["order 小的组先执行"]

    D --> E1["order = 10"]
    D --> E2["order = 20"]
    D --> E3["order = 30"]

    E1 --> F1["同组模块并发执行"]
    F1 --> E2
    E2 --> F2["同组模块并发执行"]
    F2 --> E3
    E3 --> F3["同组模块并发执行"]

    F3 --> G["所有 order 执行完成"]
```
