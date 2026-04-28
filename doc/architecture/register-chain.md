# Agent 注册链

本文说明 `AgentRegisterFactory.launch(packageName)` 内部的注册链。注册链的职责是基于应用包与外部模块目录扫描结果，完成组件、模块、Capability、生命周期方法和关闭方法的注册。

`AgentRegisterContext` 是注册链上下文。它持有 `Reflections` 扫描器，以及供各阶段读写的 `ComponentFactoryContext`、`CapabilityFactoryContext` 和全局 `AgentContext`。

```mermaid
flowchart TD
    A["AgentRegisterFactory.launch(packageName)"] --> B["packageNameToURL(packageName)"]
    B --> C["创建 AgentRegisterContext"]

    subgraph CTX["AgentRegisterContext"]
        direction TB
        C1["reflections<br/>扫描字段 / 方法 / 类型注解 / 子类"]
        C2["componentFactoryContext<br/>缓存 @Init 扫描结果"]
        C3["capabilityFactoryContext<br/>缓存 Capability 扫描结果"]
        C4["agentContext<br/>注册运行时组件"]
    end

    C --> CTX

    CTX --> F1["ComponentAnnotationValidatorFactory"]
    F1 --> F2["ComponentRegisterFactory"]
    F2 --> F3["ComponentInjectorFactory"]
    F3 --> F4["CapabilityAnnotationValidatorFactory"]
    F4 --> F5["CapabilityRegisterFactory"]
    F5 --> F6["CapabilityInjectorFactory"]
    F6 --> F7["ComponentInitHookExecutorFactory"]
    F7 --> F8["ShutdownHookCollectorFactory"]
    F8 --> Z["注册链完成"]

    subgraph S1["ComponentAnnotationValidatorFactory"]
        direction TB
        S1A["校验 @Init"]
        S1B["校验 @InjectModule"]
        S1C["将 @Init 方法缓存到 componentFactoryContext"]
        S1A --> S1B --> S1C
    end

    subgraph S2["ComponentRegisterFactory"]
        direction TB
        S2A["扫描 @AgentComponent"]
        S2B["反射调用无参构造器实例化"]
        S2C{"是否 AbstractAgentModule?"}
        S2D["注册到 AgentContext.modules"]
        S2E["注册到 AgentContext.additionalComponents"]
        S2F{"模块类型"}
        S2G["ModuleContextData.Running<br/>order / modelInfo / launchTime"]
        S2H["ModuleContextData.Sub<br/>injectTarget / modelInfo / launchTime"]
        S2I["ModuleContextData.Standalone<br/>injectTarget / modelInfo / launchTime"]

        S2A --> S2B --> S2C
        S2C -->|是| S2D --> S2F
        S2C -->|否| S2E
        S2F -->|Running| S2G
        S2F -->|Sub| S2H
        S2F -->|Standalone| S2I
    end

    subgraph S3["ComponentInjectorFactory"]
        direction TB
        S3A["读取 AgentContext.modules"]
        S3B["按 Running / Sub / Standalone 分类"]
        S3C["处理 @InjectModule"]
        S3D["记录 injectTarget"]
        S3A --> S3B --> S3C --> S3D
    end

    subgraph S4["CapabilityAnnotationValidatorFactory"]
        direction TB
        S4A["扫描 @CapabilityCore"]
        S4B["扫描 @Capability"]
        S4C["扫描 @CapabilityMethod"]
        S4D["校验 Capability value 唯一"]
        S4E["校验 CapabilityMethod 位置与唯一实现"]
        S4F["校验 @InjectCapability"]
        S4G["写入 capabilityFactoryContext"]
        S4A --> S4D
        S4B --> S4D
        S4C --> S4E
        S4D --> S4E --> S4F --> S4G
    end

    subgraph S5["CapabilityRegisterFactory"]
        direction TB
        S5A["读取 capabilityFactoryContext"]
        S5B["实例化 CapabilityCore"]
        S5C["构建方法路由表"]
        S5D["为 @Capability 接口创建动态代理"]
        S5E["注册到 AgentContext.capabilities"]
        S5A --> S5B --> S5C --> S5D --> S5E
    end

    subgraph S6["CapabilityInjectorFactory"]
        direction TB
        S6A["读取 modules + additionalComponents"]
        S6B["读取 AgentContext.capabilities"]
        S6C["处理 @InjectCapability 字段"]
        S6A --> S6C
        S6B --> S6C
    end

    subgraph S7["ComponentInitHookExecutorFactory"]
        direction TB
        S7A["读取 componentFactoryContext 中的 @Init 方法"]
        S7B["目标为 modules + additionalComponents"]
        S7C["按 @Init.order 升序执行"]
        S7A --> S7C
        S7B --> S7C
    end

    subgraph S8["ShutdownHookCollectorFactory"]
        direction TB
        S8A["扫描 @Shutdown 方法"]
        S8B["校验位置与参数"]
        S8C["AgentContext.addShutdownHook(method, order)"]
        S8A --> S8B --> S8C
    end

    F1 -.-> S1
    F2 -.-> S2
    F3 -.-> S3
    F4 -.-> S4
    F5 -.-> S5
    F6 -.-> S6
    F7 -.-> S7
    F8 -.-> S8
```

## AgentContext

`AgentContext` 是注册链的主要产物。它不是单轮对话上下文，而是运行时组件注册结果和关闭逻辑的集中容器。

```mermaid
flowchart TD
    A["AgentContext"] --> B["modules"]
    A --> C["capabilities"]
    A --> D["additionalComponents"]
    A --> E["metadata"]
    A --> F["shutdownHooks"]
    A --> G["preShutdownHooks"]
    A --> H["postShutdownHooks"]

    B --> B1["ModuleContextData.Running"]
    B --> B2["ModuleContextData.Sub"]
    B --> B3["ModuleContextData.Standalone"]

    C --> C1["CapabilityImplementation"]
    C1 --> C2["capability instance"]
    C1 --> C3["capability cores"]
    C1 --> C4["capability methods"]

    F --> F1["RUNNING"]
    F --> F2["ADDITIONAL"]
    F --> F3["STANDALONE"]
    F --> F4["SUB"]
    F --> F5["CAPABILITY"]
```
