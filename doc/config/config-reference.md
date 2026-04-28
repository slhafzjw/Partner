# 配置项说明

本文介绍 Partner 当前已注册配置文件的路径、字段含义、默认值、是否必须以及示例。

配置中心的注册、初始化和热重载机制见 [配置中心](configuration.md)。

## 目录

Partner 的工作目录由 `PARTNER_HOME` 环境变量决定：

- 如果设置了 `PARTNER_HOME`，则使用该路径作为工作目录。
- 如果未设置，则默认使用用户目录下的 `.partner`。

配置中心会在工作目录下创建以下目录：

| 目录           | 含义                                  |
|--------------|-------------------------------------|
| `config/`    | 配置文件目录，`ConfigCenter` 监听该目录。        |
| `workspace/` | 运行时工作区目录。                           |
| `state/`     | 状态持久化目录。                            |
| `resources/` | 外部资源目录，例如外部模块目录 `resources/module`。 |

所有配置文件路径都相对于 `config/` 目录声明。例如 `runtime.json` 实际对应：

```text
${PARTNER_HOME}/config/runtime.json
```

如果 `PARTNER_HOME` 未设置，则对应：

```text
~/.partner/config/runtime.json
```

## AgentGateway

| 项目      | 内容                           |
|---------|------------------------------|
| 配置文件    | `gateway.json`               |
| 注册方     | `AgentGatewayRegistry`       |
| 是否必须    | 必须。当前没有默认配置。                 |
| 是否支持热重载 | 支持。修改后会重新 reconcile channel。 |

`gateway.json` 用于声明启用哪些 Gateway channel，以及默认响应通道。当前 Partner-Core 默认注册了 `websocket_channel`。

```json
{
  "default_channel": "websocket_channel",
  "channels": [
    {
      "channel_name": "websocket_channel",
      "params": {
        "hostname": "127.0.0.1",
        "port": "29600",
        "heartbeat_interval": "10000"
      }
    }
  ]
}
```

字段说明：

| 字段                        | 必填 | 含义                                                     |
|---------------------------|----|--------------------------------------------------------|
| `default_channel`         | 是  | 默认响应通道。必须出现在 `channels` 列表中。                           |
| `channels`                | 是  | 要启用的通道列表，不能为空。                                         |
| `channels[].channel_name` | 是  | 通道名称，必须对应已经注册的 `AgentGatewayRegistration.channelName`。 |
| `channels[].params`       | 否  | 通道参数，按具体 Gateway 解释。                                   |

`websocket_channel` 支持的参数：

| 参数                   | 默认值         | 含义                     |
|----------------------|-------------|------------------------|
| `hostname`           | `127.0.0.1` | WebSocket 服务监听地址。      |
| `port`               | `29600`     | WebSocket 服务端口，必须大于 0。 |
| `heartbeat_interval` | `10000`     | 心跳间隔，单位为毫秒，必须大于 0。     |

## ModelRuntime

| 项目      | 内容                                                                                                     |
|---------|--------------------------------------------------------------------------------------------------------|
| 配置文件    | `model.json`                                                                                           |
| 注册方     | `ModelRuntimeRegistry`                                                                                 |
| 是否必须    | 通常必须。若同时提供 `PARTNER_DEFAULT_BASE_URL`、`PARTNER_DEFAULT_API_KEY`、`PARTNER_DEFAULT_MODEL`，可使用环境变量生成默认配置。 |
| 是否支持热重载 | 支持。修改后会重建 provider 映射，失败时回滚到旧配置。                                                                       |

`model.json` 用于声明模型供应商，以及特定模块使用哪个 provider。当前支持的 provider 类型为 `OPENAI_COMPATIBLE`。

```json
{
  "providerConfigSet": [
    {
      "name": "default",
      "type": "OPENAI_COMPATIBLE",
      "defaultModel": "gpt-4.1-mini",
      "baseUrl": "https://api.example.com/v1",
      "apiKey": "example-api-key"
    }
  ],
  "runtimeConfigSet": [
    {
      "modelKey": "communication_producer",
      "providerName": "default",
      "override": {
        "model": "gpt-4.1",
        "temperature": 0.7,
        "topP": 1.0,
        "maxTokens": 2048,
        "extras": {}
      }
    }
  ]
}
```

字段说明：

| 字段                                 | 必填 | 含义                                                     |
|------------------------------------|----|--------------------------------------------------------|
| `providerConfigSet`                | 是  | 基础 provider 集合。必须包含名为 `default` 的 provider。            |
| `providerConfigSet[].name`         | 是  | provider 名称。                                           |
| `providerConfigSet[].type`         | 是  | provider 类型。当前支持 `OPENAI_COMPATIBLE`。                  |
| `providerConfigSet[].defaultModel` | 是  | 该 provider 的默认模型。                                      |
| `providerConfigSet[].baseUrl`      | 是  | OpenAI-compatible API base URL。                        |
| `providerConfigSet[].apiKey`       | 是  | API key。                                               |
| `runtimeConfigSet`                 | 是  | 模块级 provider 配置集合。可以为空数组。                              |
| `runtimeConfigSet[].modelKey`      | 是  | 模块使用的模型 key。实现 `ActivateModel` 的模块通过该 key 解析 provider。 |
| `runtimeConfigSet[].providerName`  | 是  | 要 fork 的基础 provider 名称。                                |
| `runtimeConfigSet[].override`      | 否  | 模块级覆写配置。                                               |
| `override.model`                   | 是  | 覆写模型名称。当前结构中该字段不可为空。                                   |
| `override.temperature`             | 否  | 覆写 temperature。                                        |
| `override.topP`                    | 否  | 覆写 top_p。                                              |
| `override.maxTokens`               | 否  | 覆写 max_tokens。                                         |
| `override.extras`                  | 否  | 额外 provider 参数。                                        |

如果某个 `modelKey` 没有出现在 `runtimeConfigSet` 中，则会使用名为 `default` 的基础 provider。

## AgentRuntime

| 项目      | 内容                                             |
|---------|------------------------------------------------|
| 配置文件    | `runtime.json`                                 |
| 注册方     | `AgentRuntime`                                 |
| 是否必须    | 否。存在默认配置。                                      |
| 默认配置    | `masked_modules = []`，`debounce_window = 300`。 |
| 是否支持热重载 | 支持。修改后会刷新运行模块分组。                               |

`runtime.json` 用于控制 Running module 的运行时行为。

```json
{
  "masked_modules": [],
  "debounce_window": 300
}
```

字段说明：

| 字段                | 必填 | 含义                                        |
|-------------------|----|-------------------------------------------|
| `masked_modules`  | 是  | 运行时屏蔽的模块名集合。被屏蔽模块不会进入 Running module 调度链。 |
| `debounce_window` | 是  | 输入后的等待窗口，单位为毫秒。同一 source 的连续输入会在该窗口内合并。   |

## LogAdvice

| 项目      | 内容                    |
|---------|-----------------------|
| 配置文件    | `advice_logging.json` |
| 注册方     | `LogAdviceProvider`   |
| 是否必须    | 否。存在默认配置。             |
| 默认配置    | `logLevel = NONE`。    |
| 是否支持热重载 | 支持。                   |

`advice_logging.json` 用于控制框架内 `LogAdvice` 的日志详细程度。

```json
{
  "logLevel": "NONE"
}
```

字段说明：

| 字段         | 必填 | 可选值                            | 含义                                                             |
|------------|----|--------------------------------|----------------------------------------------------------------|
| `logLevel` | 是  | `NONE` / `ABSTRACT` / `DETAIL` | `NONE` 不记录 advice 日志；`ABSTRACT` 记录摘要；`DETAIL` 记录更详细的输入输出或异常信息。 |

## ExecutionPolicy

| 项目      | 内容                                                         |
|---------|------------------------------------------------------------|
| 配置文件    | `action/runner_policy.json`                                |
| 注册方     | `ExecutionPolicyRegistry`                                  |
| 是否必须    | 否。存在默认配置。                                                  |
| 默认配置    | `provider = direct`，`mode = DIRECT`，`net = ENABLE`，继承环境变量。 |
| 是否支持热重载 | 支持。                                                        |

`runner_policy.json` 用于控制 Action Runner 执行命令时的执行策略。

```json
{
  "provider": "direct",
  "mode": "DIRECT",
  "net": "ENABLE",
  "inheritEnv": true,
  "env": {},
  "workingDirectory": null,
  "readOnlyPaths": [],
  "writablePaths": []
}
```

字段说明：

| 字段                 | 必填 | 含义                                         |
|--------------------|----|--------------------------------------------|
| `provider`         | 是  | 执行策略 provider 名称。当前默认 provider 为 `direct`。 |
| `mode`             | 是  | 执行模式，可选 `DIRECT` / `SANDBOX`。              |
| `net`              | 是  | 网络策略，可选 `ENABLE` / `DISABLE`。              |
| `inheritEnv`       | 是  | 是否继承当前进程环境变量。                              |
| `env`              | 是  | 追加或覆盖的环境变量。                                |
| `workingDirectory` | 否  | 命令工作目录。                                    |
| `readOnlyPaths`    | 是  | 只读路径集合，主要供 sandbox 类 provider 使用。          |
| `writablePaths`    | 是  | 可写路径集合，主要供 sandbox 类 provider 使用。          |

当前默认 `direct` provider 主要使用命令、工作目录和环境变量；`mode`、`net`、路径隔离字段是否生效取决于具体 provider 实现。

## VectorClient

| 项目      | 内容                                               |
|---------|--------------------------------------------------|
| 配置文件    | `vector.json`                                    |
| 注册方     | `VectorClientRegistry`                           |
| 是否必须    | 否。存在默认配置。                                        |
| 默认配置    | `enabled = false`，`type = null`。                 |
| 是否支持热重载 | 当前 registry 未覆写 `onReload()`，因此运行期修改不会重新启动向量客户端。 |

`vector.json` 用于控制向量客户端。未启用时，配置可以省略。

关闭向量客户端：

```json
{
  "enabled": false,
  "type": null
}
```

使用 Ollama 向量服务：

```json
{
  "enabled": true,
  "type": "OLLAMA",
  "ollamaEmbeddingUrl": "http://127.0.0.1:11434",
  "ollamaEmbeddingModel": "nomic-embed-text"
}
```

使用本地 ONNX 模型：

```json
{
  "enabled": true,
  "type": "ONNX",
  "tokenizerPath": "/path/to/tokenizer.json",
  "embeddingModelPath": "/path/to/model.onnx"
}
```

字段说明：

| 字段                     | 必填                  | 含义                            |
|------------------------|---------------------|-------------------------------|
| `enabled`              | 是                   | 是否启用向量客户端。为 `false` 时不会启动客户端。 |
| `type`                 | 启用时必填               | 向量客户端类型，可选 `OLLAMA` / `ONNX`。 |
| `ollamaEmbeddingUrl`   | `type = OLLAMA` 时必填 | Ollama 服务地址。                  |
| `ollamaEmbeddingModel` | `type = OLLAMA` 时必填 | Ollama embedding 模型名。         |
| `tokenizerPath`        | `type = ONNX` 时必填   | tokenizer 文件路径。               |
| `embeddingModelPath`   | `type = ONNX` 时必填   | ONNX embedding 模型路径。          |
