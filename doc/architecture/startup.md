# Agent 启动流程

本文说明 `Agent.newAgent(appClass).launch()` 的外层启动顺序。

`Agent.launch()` 负责拉起基础单例、注册配置参与者、注册 Gateway、安装关闭钩子、添加外部模块目录，并启动注册工厂。组件扫描、模块实例化和能力注册的细节见 [注册链](register-chain.md)。

```mermaid
flowchart TD
    A["Agent.newAgent(appClass).launch()"] --> B["加载基础单例<br/>ConfigCenter / StateCenter"]

    B --> C["注册 ExceptionReporter"]

    C --> D

    subgraph D["注册 Configurable"]
        direction TB
        D1["LogAdviceProvider.register()"]
        D2["ModelRuntimeRegistry.register()"]
        D3["AgentGatewayRegistry.register()"]
        D4["应用传入的 Configurable.register()"]
    end

    D --> E["注册 AgentGatewayRegistration"]

    E --> F

    subgraph F["注册关闭钩子"]
        direction TB
        F1["preShutdown<br/>AgentGatewayRegistry.close()"]
        F2["preShutdown hooks"]
        F3["postShutdown<br/>StateCenter.save()"]
        F4["postShutdown<br/>TraceSinkRegistry.close()"]
        F5["postShutdown<br/>ConfigCenter.close()"]
        F6["postShutdown hooks"]
        F1 --> F2
        F3 --> F4 --> F5 --> F6
    end

    F --> G["添加外部模块扫描目录<br/>resources/module"]
    G --> H["启动注册工厂<br/>AgentRegisterFactory.launch(application package)"]

    H --> I

    subgraph I["初始化配置系统"]
        direction TB
        I1["ConfigCenter.initAll()<br/>初始化已注册的 Configurable"]
        I2["触发各 Configurable.init(config, json)"]
        I3["ConfigCenter.start()<br/>启动配置监听"]
        I1 --> I2 --> I3
    end

    I --> K["Agent 启动完成"]
```

Gateway 的实际 `launch()` 不在 `AgentGatewayRegistration.register()` 中直接发生。Gateway registration 只把可用通道注册到 `AgentGatewayRegistry`；真正的通道创建、启动和默认响应通道设置发生在 `ConfigCenter.initAll()` 阶段，由 `AgentGatewayRegistry.init()` 根据 `gateway.json` 执行。
