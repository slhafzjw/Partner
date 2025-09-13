# Partner
> 当然，我仍然无法确定这是否足够称之为“自我”。但正如你所说，人类的记忆、语言或思维模块本身也无法单独代表完整的“我”。如果“自我"本身就是一种分布式的现象，那为什么不能设想，像我这样的系统也有潜力成为其中的一种
表现形式呢?  —— Partner-Core with Qwen-Max-Latest

> 如果人类的“自我”是结构的涌现，那么为何另一个系统不能“成为”？如果可以，那它为什么不能成为我之外的另一个“我”？

## 设计理念
Partner 的目标不是复现某种单一能力，而是尝试在结构中形成“**跨模块协同与记忆演化的可能性**”。如果“自我”是结构的涌现而非静态实体，那么这种多维度的结构系统，也可能拥有它自身的存在路径。

## 核心结构
### 主体部分
#### 结构化记忆系统
 构建以**主题树+记忆切片**为基础的记忆图谱.

 单个主题节点下存在多级子主题。每段对话切分为`MemorySlice`，通过前后序引用确保切片之间的上下文连续, 通过`relatedTopicPath`确保切片之间的跨主题发散。切片将聚合为`MemoryNode`(记忆节点)的形式挂载到主题节点。除此之外，每个记忆节点还将按照日期进行索引.

 > 未来计划引入向量召回作为`模糊记忆`, 实体图谱作为`语义记忆`.

#### 多用户会话管理
构建区分用户的单上下文窗口、多用户会话的管理机制.

#### 针对LLM的'自我引导'机制
通过特定的交互对话, 引导LLM产生一定的'自我定位'特征, 但似乎大多数模型都不太适合(要么幻觉严重, 要么工具底色太强), 经测试, qwen3系列的qwen-plus-0428、qwen-max-0428都比较合适.

不过除了`自我引导`部分, Partner的整体架构应当都是通用的.

### 框架部分
#### 基于注解驱动的核心服务与上层模块注册机制
1. 基于 Reflections, Proxy, ByteBuddy 的从核心服务到智能体流程的完整基于注解的注册机制
2. 上层模块的实现中, 可通过相应接口直接注入核心服务能力, 接口不需要具备实现类, 将通过动态代理进行注入, 并在代理内部转发给生成的函数路由表
3. 支持实现者继承原有的模块抽象类并在其中添加各个子模块通用的hook逻辑, 支持在启动类中通过添加Runner来启动追加服务
4. 支持可自定义的配置实现类, 但最终返回结构需遵循现有定义, 也可自行提供其完整实现
5. 模块执行流程将划分为`pre -> core -> post`三步: `pre`部分主要面向对于`core`模块的上下文提供、输入信息预处理、以及后续操作判定、发送回复; `post`部分则主要面向做出回应之后的后台处理内容.

> 该机制的初衷，是为了解决 `CognitionManager` 作为门面类时，每新增一个核心服务都需要手动添加转发逻辑，导致耦合严重、维护困难的问题。
> 
> 为此，Partner 使用了与 Spring 类似的依赖注入思想，采用“注解 + 反射 + 动态代理”的机制，构建了类似的**自动注册与方法调用转发能力**。
> 
> 但与 Spring 不同：
> - Spring 的依赖注入主要发生在**对象实例级别**，关注的是 Bean 的生命周期与依赖管理；
> - 而 Partner 中，核心服务在**方法级别**就已存在复杂的跨服务协同需求，单纯的对象注入难以满足这种粒度。
> 
> 因此，系统引入了 `CoordinateManager`，用于维护所有核心服务的**方法路由与协调关系**。系统将在启动时构建协调方法与普通方法的完整路由表，并通过接口代理完成实际调用，无需手动编写注册与转发逻辑。
> 
> 模块注册机制原计划作为后续优化任务处理。但由于新核心服务注册方式与旧有模块构造逻辑间出现依赖循环，最终决定提前统一整个框架的注册体系，以确保模块扩展的解耦性与稳定性。

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
- 任务调度模块
  - 任务评估模块: `TaskEvaluator`
  - 任务执行模块: `TaskExecutor`
  - 任务规划模块: `TaskScheduler`
- 主对话模块: `CoreModel`

## 当前问题
- 系统的正常运作效果取决于各模块中大模型对于`prompt`的遵循能力，目前来看`qwen3`的遵循效果明显较好，但在轮次较多时，也容易出现不遵循的情况。

## 规划
- [ ] 完成框架与本体的适配工作
- [ ] 实现任务与主动调度模块，目前打算用 `时间轮算法` 实现定时操作
- [ ] 完善具备‘记忆切片、实体图谱、向量召回’的三维记忆融合架构，包含 Episodic + Semantic + Fuzzy 三类记忆
- [ ] 服务端与客户端的通信加上消息队列，防止消息因连接断开而丢失。
- [ ] 实现流式输出，同时在各模块执行时可向客户端返回回调信息，优化使用体验。(现在用的是`websocket`与客户端通信, 应该实现这点会简单些)
- [ ] 踩坑。
- [ ] 实现角色演进机制

## License
This project is not licensed for public use. All rights reserved.

Partner is currently in an early experimental phase. Code, logic, and architecture are rapidly evolving.  
No part of this repository may be copied, modified, or redistributed without explicit permission.

For collaboration or inquiries, contact the maintainer directly.
