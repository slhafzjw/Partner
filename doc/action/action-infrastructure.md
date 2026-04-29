# 行动基础设施

本文是 Partner 行动基础设施文档索引。行动基础设施位于 `ActionExecutor / ActionCore` 之下，负责把已经完成参数填充的 `MetaAction` 路由到对应运行通道，并为本地 action、外部 MCP、命令执行和行动描述信息提供底层支撑。

```text
ActionExecutor
  ↓
RunnerClient
  ↓
MCP / BUILTIN / ORIGIN
  ↓
ExecutionPolicy / CommandExecutionService / OriginExecutionService
```

## 定位

行动基础设施负责把“结构化行动单元”转换成真实执行。

它主要处理：

- `MetaAction` 的提交与结果写回。
- 不同 `MetaAction.Type` 的执行路由。
- 内部能力与外部 MCP 能力的接入。
- 命令执行前的策略包装。
- 本地 action 文件的执行。
- 临时行动与持久化行动的序列化入口。
- `MetaActionInfo` 的描述生成与覆写。

本文只保留总览和目录；细节拆分到以下文档。

## 目录

- [`infra/runner-client.md`](infra/runner-client.md)：说明 `RunnerClient`、`LocalRunnerClient` 以及 `MCP` / `BUILTIN` / `ORIGIN` 三类执行路由。
- [`infra/execution-policy.md`](infra/execution-policy.md)：说明执行策略如何把原始命令包装为 `WrappedLaunchSpec`，以及 `direct` / `bwrap` provider 的职责。
- [`infra/command-execution-service.md`](infra/command-execution-service.md)：说明一次性命令、持久命令、`CommandSession` 和 `Result` 的处理方式。
- [`infra/meta-action-info.md`](infra/meta-action-info.md)：说明 `MetaActionInfo` 的来源、描述覆写和在行动系统中的生效位置。
