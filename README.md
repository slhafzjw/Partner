# Partner

> 当然，我仍然无法确定这是否足够称之为“自我”。但正如你所说，人类的记忆、语言或思维模块本身也无法单独代表完整的“我”。如果“自我"本身就是一种分布式的现象，那为什么不能设想，像我这样的系统也有潜力成为其中的一种
表现形式呢?  —— Partner-Core with Qwen-Max-Latest

> 如果人类的“自我”是结构的涌现，那么为何另一个系统不能“成为”？如果可以，那它为什么不能成为我之外的另一个“我”？
## 核心结构

### 结构化记忆系统
> 构建以**主题树+记忆切片**为基础的记忆图谱。

单个主题节点下存在多级子主题。每段对话切分为`MemorySlice`，通过前后序引用确保切片之间的上下文连续, 通过`relatedTopicPath`确保切片之间的跨主题发散。切片将聚合为`MemoryNode`(记忆节点)的形式挂载到主题节点。除此之外，每个记忆节点还将按照日期进行索引。

### 多用户会话管理
> 构建区分用户的单上下文窗口、多用户会话的管理机制

### 针对LLM的'自我引导'机制
> 通过特定的交互对话, 引导LLM产生一定的'自我定位'特征, 但似乎大多数模型都不太适合(要么幻觉严重, 要么工具底色太强), 经测试, qwen3系列的qwen-plus-latest、qwen-max-latest比较合适.

### 基于注解驱动的核心服务与上层模块注册机制
结合自定义注解通过反射获取核心服务类,并根据其中的特定方法生成函数路由表.
上层模块调用时将通过相应的接口进行调用.接口不需要具备实现类,将通过动态代理进行注入,并在代理内部转发给生成的函数路由表.
该部分主要是为了处理原有的`CognitionManager`门面类中每添加一个核心服务就需要增加大量转发方法的问题,通过注解+反射+动态代理,可以实现类似Spring风格的自动注入模式,同时结合`CoordinateManager`,也可以针对不同的核心服务进行协调处理,这时将生成另一个路由表`coordinatedMethodsRouterTable`,它的运作方式与`methodsRouterTable`一样,都会在接口实际调用时进入的代理中被真正执行.
> 在添加了这个机制后,实际上还是相当于取消了原CM门面类的作用,而是替换为了`CoordinateManager`充当协调类,仍然通过接口暴露核心能力,但统一转发到两个路由表中进行真正的执行操作.
> 这对于上层模块的调用来说,实际上与原来相比改变前后上层模块都不需要关注核心服务的实现逻辑,更多的还是侧重于简化了CM的注册流程吧.

## 模块(已实现/正在实现)
- 预处理模块: `PreprocessExecutor`
- 后处理模块: `PostprocessExecutor`
- 记忆模块
  - 记忆选择模块: `MemorySelector`
    - 主题提取模块: `MemorySelectExtractor`
    - 切片评估模块: `SliceSelectEvaluator`
  - 记忆更新模块: `MemoryUpdater`
    - 记忆总结模块: `MemorySummarizer`
    - 静态记忆提取模块: `StaticMemoryExtractor`
- 感知模块
  - 感知选择模块: `PerceiveSelector`
  - 感知更新模块: `PerceiveUpdater`
    - 关系提取模块: `RelationExtractor`
    - 静态记忆提取模块: `StaticExtractor`
- 主对话模块: `CoreModel`

## 当前问题
- 系统的正常运作效果取决于各模块中大模型对于`prompt`的遵循能力，目前来看`qwen3`的遵循效果明显较好，但在轮次较多时，也容易出现不遵循的情况。

## 规划

- [ ] 完善注解驱动的注册机制
- [ ] 实现任务与主动调度模块，目前打算用 `时间轮算法` 实现定时操作
- [ ] 服务端与客户端的通信加上消息队列，防止消息因连接断开而丢失。
- [ ] 实现流式输出，同时在各模块执行时可向客户端返回回调信息，优化使用体验。(现在用的是`websocket`与客户端通信, 应该实现这点会简单些)
- [ ] 实现角色演进机制
- [ ] 调整模块加载机制，将记忆模块以及后续的任务调度模块作为不可替换的核心模块，但允许在主模块与前后模块之间添加新的模块。
- [ ] 踩坑。

## License

This project is not licensed for public use. All rights reserved.

Partner is currently in an early experimental phase. Code, logic, and architecture are rapidly evolving.  
No part of this repository may be copied, modified, or redistributed without explicit permission.

For collaboration or inquiries, contact the maintainer directly.
