# 流程参考: 行动模块
> 行动模块当前仍在推进中，当前展示的为设想中或者当前阶段的流程图，可能与最终实现存在差异

## 前置模块
### 行动规划模块: [ActionPlanner](../../Partner-Main/src/main/java/work/slhaf/partner/module/modules/action/planner/ActionPlanner.java)

```mermaid
---
config:
  layout: elk
  elk:
    nodePlacementStrategy: LINEAR_SEGMENTS
---
flowchart TD

    direction TB

    Context --> Input[输入]
    ActionCore --> ActionTendencyCache[行动意图缓存]

    subgraph AC [行动缓存匹配]
        Input[输入] --> ActionTendencyCache
        ActionTendencyCache --> Hit{是否命中}
    end
    Hit --> |否| AR

    subgraph AR [行动意图识别]
        ActionExtractor[行动意图提取]

        Input[输入] --> ActionExtractor
        Messages --> ActionExtractor

        ActionExtractor --> ExtractorResult{是否存在行动意图}
    end

    ExtractorResult --> |否| ResultEmpty

    subgraph AE [行动意图评估]
        ActionTendencies[行动意图列表] 
        EvaluatorResult[意图评估结果]
        DATA[数据<br/>---<br/>记忆切片 可选行动单元 近期对话记录 用户信息]

        Hit --> |是| ActionTendencies
        ExtractorResult --> |是| ActionTendencies

        DATA --> EvaluatorThread1
        DATA --> EvaluatorThread2
        DATA --> EvaluatorThread3

        ActionTendencies --> Tendency1[行动意图1] --> EvaluatorThread1[评估线程1] --> EvaluatorResult
        ActionTendencies --> Tendency2[行动意图2] --> EvaluatorThread2[评估线程2] --> EvaluatorResult
        ActionTendencies --> Tendency3[行动意图3] --> EvaluatorThread3[评估线程3] --> EvaluatorResult
    end

    EvaluatorResult --> |放入行动池| ActionCore
    EvaluatorResult --> |异步更新行动意图缓存| ActionCore
    EvaluatorResult --> ResultNormal --> |回写| Context

    ResultEmpty@{shape: braces, label: "[结束]<br/>---<br/>行动模块前置流程结束"}
    ResultNormal@{shape: braces, label: "[结束]<br/>---<br/>聚合为特定格式的 Prompt"}

    ActionCore[行动核心] --> DATA
    MemoryCore[记忆核心] --> DATA
    CognationCore[认知核心] --> DATA
    PerceiveCore[感知核心] --> DATA
    Context[流程上下文]
```