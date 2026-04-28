# 关闭流程

本文说明 Partner 在 JVM 终止时的关闭顺序。

关闭阶段由 `AgentContext` 安装到 JVM 的 shutdown hook 统一触发。生命周期 Hook 与注解式 `@Shutdown` Hook 是两套机制：前者用于框架级收尾，如关闭 Gateway、保存状态、关闭配置监听；后者用于模块、额外组件与 Capability Core 自己声明的关闭逻辑。

当前关闭顺序为：

1. `preShutdownHooks`
2. `RUNNING`
3. `ADDITIONAL`
4. `STANDALONE`
5. `SUB`
6. `CAPABILITY`
7. `postShutdownHooks`

```mermaid
flowchart TD
    A["JVM 收到终止信号"] --> B["Runtime.addShutdownHook 触发"]
    B --> C["AgentContext.computeInstances()"]

    C --> C1["收集 Running module 实例"]
    C --> C2["收集 Standalone module 实例"]
    C --> C3["收集 Sub module 实例"]
    C --> C4["收集 additional component 实例"]
    C --> C5["收集 capability core 实例"]

    C1 --> D["执行 preShutdownHooks"]
    C2 --> D
    C3 --> D
    C4 --> D
    C5 --> D

    D --> D1["AgentGatewayRegistry.close()"]
    D1 --> D2["停止所有 running gateway channel"]
    D2 --> D3["AgentRuntime.unregisterResponseChannel(channel)"]
    D3 --> D4["默认响应通道恢复为 LogChannel"]

    D --> E["执行 @Shutdown: RUNNING"]
    E --> F["执行 @Shutdown: ADDITIONAL"]
    F --> G["执行 @Shutdown: STANDALONE"]
    G --> H["执行 @Shutdown: SUB"]
    H --> I["执行 @Shutdown: CAPABILITY"]

    I --> J["执行 postShutdownHooks"]
    J --> J1["StateCenter.save()"]
    J1 --> J2["TraceSinkRegistry.close()"]
    J2 --> J3["ConfigCenter.close()"]
    J3 --> K["关闭流程结束"]
```
