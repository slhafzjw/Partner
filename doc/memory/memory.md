# 记忆存储与组织

本文是 Partner 记忆系统文档索引。Partner 的记忆系统分为两个层面：

- **存储层**：负责保存稳定的记忆数据。当前核心模型是 `MemoryUnit` 与 `MemorySlice`。
- **组织层**：负责在存储数据之上建立可替换的召回结构。当前默认实现基于主题路径和日期索引；如果替换召回实现，需要由新实现自行维护对应索引。

这种划分让记忆落盘格式保持稳定，同时允许上层召回策略独立演进。

## 目录

- [`memory-storage.md`](memory-storage.md)：说明 `MemoryCore`、`MemoryUnit`、`MemorySlice` 的数据模型，以及原始记忆如何由 `CommunicationProducer` 和 `DialogRolling` 生成。
- [`memory-retrieval.md`](memory-retrieval.md)：说明默认组织层 `MemoryRuntime`，包括主题路径索引、日期索引、索引建立和运行时召回。
- [`after-rolling.md`](after-rolling.md)：说明 `DialogRolling` 后的扩展触发点，以及 memory 侧如何通过 `AfterRolling` consumer 补充索引信息。
