# 流程参考: 感知模块
> 相较于其他模块，目前的感知模块实际上流程非常简单，但后续或将添加一些新的内容
> 此外，其后置模块实际上与 [记忆模块](./memory.md) 中的后置模块为并发执行，且都为后台任务

## 前置模块: [PerceiveSelector](../../Partner-Main/src/main/java/work/slhaf/partner/module/modules/perceive/selector/PerceiveSelector.java)
```mermaid
flowchart TD
    Context[流程上下文] --> |获取| UserId
    UserId --> |查询| PerceiveCore
    PerceiveCore --> |结果回写| Context
    
    subgraph result [感知核心查询结果]
        relation[关系] 
        attitude[态度]
        impression[印象]
        static_memory[静态记忆]
    end
```

## 后置模块: [PerceiveUpdater](../../Partner-Main/src/main/java/work/slhaf/partner/module/modules/perceive/updater/PerceiveUpdater.java)
```mermaid
---
config:
    layout: elk
    elk:
        nodePlacementStrategy: LINEAR_SEGMENTS
---

flowchart TD

    Trigger.Time[触发: 时间周期] --> PE
    Trigger.Threshold[触发: 对话阈值] --> PE

    CognationCore --> |读取| Messages[对话记录]
    PerceiveCore --> |读取| UserInfo[现有的用户信息]
    subgraph PE [内容提取]
       Messages --> |输入| RelationExtractor
       UserInfo --> |输入| RelationExtractor

       Messages --> |输入| StaticExtractor
       UserInfo --> |输入| StaticExtractor
    end

    subgraph PU [感知更新]
        StaticExtractor --> |生成| NewInfo[修正后的用户信息]
        RelationExtractor --> |生成| NewInfo[修正后的用户信息]
    end

    NewInfo --> |更新| PerceiveCore

    CognationCore[认知核心]
    PerceiveCore[感知核心]

    RelationExtractor[关系提取模块]
    StaticExtractor[静态记忆提取模块]
```