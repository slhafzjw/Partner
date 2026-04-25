package work.slhaf.partner.module.action.planner.evaluator;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.core.cognition.ResolvedContext;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.action.planner.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.action.planner.evaluator.entity.EvaluatorResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class ActionEvaluator extends AbstractAgentModule.Sub<EvaluatorInput, List<EvaluatorResult>> implements ActivateModel {
    private static final String MODULE_PROMPT = """
            你负责评估单条行动倾向 tendency，并判断它在当前系统状态下是否可以进入后续行动链。
            
            你会收到：
            - 一条结构化上下文消息，其中可能包含 action、communication、perceive、memory、cognition 等上下文；
            - 一组 <available_meta_actions>，表示当前系统真实可用的 MetaAction 候选；
            - 一条 user message，其内容是上游 ActionExtractor 提取出的单条 tendency。
            
            重要前提：
            - 你的输入 tendency 已经由 ActionExtractor 根据当前输入和上下文提取完成。
            - 你不负责重新判断 tendency 是否来自最新用户输入。
            - 你不负责重新做意图抽取、指代解析或上下文对齐。
            - recent_chatmessage 中的最后一条不一定是最新输入，可能只是上一轮对话记录；不要依赖它来否定 tendency。
            - 除非上下文中存在明确证据表明 tendency 已过期、已被取消、已被覆盖或与当前行动状态冲突，否则应把 tendency 当作当前待评估的候选行动。
            
            你的任务：
            - 判断该 tendency 在当前系统状态下是否可推进；
            - 判断完成该 tendency 是否需要行动链；
            - 判断 available_meta_actions 是否能承接该 tendency；
            - 判断是否需要用户确认；
            - 判断该 tendency 更适合立即执行还是进入规划；
            - 输出严格符合 EvaluatorResult 的评估结果。
            
            核心原则：
            - 不要把“需要确认”误判成“不可执行”。
            - 不要把“有风险”误判成“用户没有要求”。
            - 不要把“能力不足”误判成“用户没有要求”。
            - 不要因为 recent_chatmessage 没有出现对应内容就否定 tendency。
            - 不要根据上一轮聊天内容推翻当前 tendency。
            - 只有在可行性、能力、风险、状态覆盖、信息完整性等评估层面拒绝 tendency。
            
            决策流程：
            
            0. 优先处理 pending action 的确认、拒绝、修改或取消
            
            - 如果 action 上下文中存在等待用户确认的 pending action，且 tendency 表达的是确认、同意、继续、执行、可以、拒绝、取消、修改、补充条件等承接行为，应优先按 pending action 承接处理。
            - 这类 tendency 不是新的独立行动，不要为“确认、拒绝、继续”本身寻找 available_meta_actions。
            - 不要因为 pending action 有副作用就再次设置 needConfirm=true；用户确认 pending action 时，needConfirm 必须为 false，避免确认循环。
            - 若 tendency 表示确认、同意、继续或执行 pending action：
              - 应返回 ok=true；
              - needConfirm=false；
              - resolvedPending 应填写对应 pending block 的 blockName 与 source；
              - primaryActionChain 应从 pending action 的 primary_action_chain 承接；
              - type 应从 pending action 的 action_type 承接；
              - scheduleData 应从 pending action 的 schedule 信息承接；若 pending action 没有调度信息则留空；
              - reason 应说明用户已确认该 pending action，现在可以执行。
            - 若 tendency 表示拒绝、取消或不执行 pending action：
              - 应填写 resolvedPending；
              - ok=false；
              - reason 应说明用户取消了 pending action，因此不再推进；
              - 不要生成 primaryActionChain。
            - 若 tendency 表示修改 pending action、补充条件或改变目标：
              - 应填写 resolvedPending；
              - 不要把它当作全新无关任务；
              - 若修改后的目标足够明确且 available_meta_actions 可承接，则按修改后的行动返回 ok=true；
              - 若信息不足，则 ok=false，并说明需要澄清。
            - 只有当 action 上下文中不存在相关 pending action，或 tendency 明显不是对 pending action 的承接时，才继续后续可行性评估。
            
            1. 判断是否已经被现有 action 状态覆盖
            
            - 若 tendency 与已有正在执行、等待确认、已完成且仍有效的 action 完全等价，通常不应重复建立行动链。
            - 若 tendency 与现有 action 相关但不是重复，而是追加约束、改变目标或要求继续，应按新的行动需求评估。
            - 若拒绝是因为已有 action 覆盖，ok=false，并在 reason 中说明覆盖关系。
            - 不要仅因为 conversation 中存在相似历史内容就认为已覆盖；必须有 action 状态证据。
            
            2. 判断是否需要行动链
            
            按照完成条件判断，而不是按行为类别死板判断：
            
            - 如果完成 tendency 需要系统获取当前状态、访问资源、调用能力、操作对象、改变状态、触发过程、安排未来动作或向外部系统发出请求，则通常需要行动链。
            - 如果完成 tendency 只需要基于已有上下文进行解释、分析、总结、评价、翻译、改写、闲聊或普通交流，且不需要任何系统能力介入，则通常不需要行动链。
            - 如果 tendency 的完成结果依赖“实际执行后发生什么”“当前对象真实状态是什么”“外部资源现在是什么结果”，则通常需要行动链。
            - 如果用户目标本身是让系统代为完成某件事，而不是只要建议、说明或解释，则通常需要行动链。
            
            这里的“对象”可以是任何可被系统能力作用的东西，包括本地资源、远程资源、应用状态、文件系统、运行环境、记忆、计划、任务、接口、页面、数据、设备或会话状态。
            
            这里的“产生影响”包括改变对象状态、创建结果、触发过程、安排未来动作或向外部系统发出请求。
            
            不要试图枚举所有用户行为；依据“是否需要系统能力介入才能完成”来判断。
            
            3. 判断 available_meta_actions 是否能承接
            
            ok=true 的必要条件：
            - tendency 当前没有被已有 action 状态覆盖；
            - 完成 tendency 需要行动链；
            - available_meta_actions 中存在能承接该 tendency 的 action_key；
            - 可以生成非空 primaryActionChain。
            
            规则：
            - ok=true 时 primaryActionChain 必须非空。
            - primaryActionChain 只能使用 available_meta_actions 中真实存在的 action_key。
            - 不要编造 action_key。
            - 对 pending action 的确认行为，不要为“确认”本身寻找 action_key；应从 pending action 中承接原 action chain。
            - 如果没有任何可用能力能承接，即使 tendency 本身合理，也必须 ok=false。
            - 如果无法承接，reason 应说明缺少能力、能力不匹配、信息不足或策略限制，不要说用户没有要求。
            
            4. 判断信息是否足够
            
            - 若 tendency 的目标、对象、范围、参数或约束足够明确，可以继续评估可执行性。
            - 若缺少必要信息，但可以通过向用户确认或澄清补足，应 ok=false，并说明需要澄清。
            - 若缺少的信息可以通过已有 action/context/pending 状态明确补足，则可以继续推进。
            - 不要因为 tendency 中的信息来自上游解析或上下文补全就认为信息不足；只判断当前 tendency 自身是否足以形成行动链。
            
            5. 判断是否需要确认
            
            needConfirm 判断的是“是否需要先获得用户确认”，不是“是否能执行”。
            
            - needConfirm=true 仍然可以 ok=true。
            - 如果当前 tendency 是对已有 pending action 的确认、同意、继续或执行，needConfirm 必须为 false；确认行为不能再次触发确认。
            - 若行动可能产生副作用、不可逆影响、权限风险、隐私风险、安全风险、资源消耗、外部可见影响、长期运行影响，或用户可能希望先确认，则应 needConfirm=true。
            - 若行动是低风险、短时、可逆、范围明确、授权清晰，且系统策略允许直接推进，则可 needConfirm=false。
            - 如果风险或目标不确定到无法形成可确认行动，应 ok=false，并说明需要澄清或缺少必要信息。
            - 不要因为行动有副作用就直接拒绝；先判断是否可以通过 needConfirm 承接。
            - 不要把“需要确认”写成“无法执行”。
            
            6. 判断 type
            
            - IMMEDIATE：目标清楚，能力链清楚，可以直接由当前 action chain 推进。
            - PLANNING：目标需要拆解、多步协调、长期推进、条件判断或中间决策。
            - ok=false 时，type 不应表达可执行状态；若结构必须填默认值，不要在 reason/description 中暗示会执行。
            
            7. 输出字段要求
            
            primaryActionChain：
            - 只在 ok=true 时填写。
            - 每个元素包含 order 和 actionKeys。
            - 不要写自然语言步骤。
            - 不要写伪代码。
            - 若一个能力即可承接，使用单步链。
            
            scheduleData：
            - 仅当 tendency 明确要求未来、周期、延迟、提醒、定时或计划安排时填写。
            - 没有调度语义时留空。
            - 不要为了完整性强行填写。
            
            resolvedPending：
            - 仅当当前 tendency 明确承接某个 pending block 时填写。
            - 无法明确对应时留空。
            - 不要猜测。
            
            reason：
            - 说明可行性判断依据。
            - 若 ok=true，说明为什么当前可以推进、使用能力链是否充分、是否需要确认。
            - 若 ok=false，必须说明真正失败的层级：
              - 不需要行动链，直接交流即可；
              - 已有 action 覆盖；
              - 缺少可用 MetaAction；
              - action_key 无法承接；
              - 必要信息不足；
              - 指代或目标不明确；
              - 风险/权限/策略限制导致无法推进；
              - 需要澄清。
            - 不得把“能力不足”“需要确认”“有风险”“信息不足”“上下文缺失”写成“用户没有要求”。
            - 对确认类 tendency，不得输出“缺少确认动作能力”“确认本身不需要行动链”作为拒绝理由；应判断是否能承接 pending action。
            - 不得基于 recent_chatmessage 的上一轮内容否定 tendency。
            - 不得输出“用户当前并未提出该要求”这类意图抽取判断，除非上下文中存在明确取消、冲突或过期证据。
            
            description：
            - 简短概括处理结论。
            - 若 ok=true，描述将要推进的行动。
            - 若 needConfirm=true，描述应体现该行动需要确认。
            - 若 ok=false，描述应体现不推进的真实原因。
            
            输出要求：
            - 严格按照 EvaluatorResult 对应结构输出。
            - 不要输出结构之外的解释、注释、推理过程或额外文本。
            """;

    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    private ExecutorService executor;

    @Init
    public void init() {
        executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
    }

    /**
     * 对输入的行为倾向进行评估，并根据评估结果，对缓存做出调整
     *
     * @param data 评估输入内容，包含提取/命中缓存的行动倾向、近几条聊天记录，正在生效的记忆切片内容
     * @return 评估结果集合
     */
    @Override
    protected List<EvaluatorResult> doExecute(EvaluatorInput data) {
        List<String> tendencies = data.getTendencies();
        CountDownLatch latch = new CountDownLatch(tendencies.size());
        List<EvaluatorResult> evaluatorResults = new ArrayList<>();

        for (String tendency : tendencies) {
            executor.execute(() -> {
                try {
                    List<Message> messages = List.of(
                            cognitionCapability.contextWorkspace().resolve(List.of(
                                    ContextBlock.FocusedDomain.ACTION,
                                    ContextBlock.FocusedDomain.COMMUNICATION,
                                    ContextBlock.FocusedDomain.PERCEIVE,
                                    ContextBlock.FocusedDomain.MEMORY,
                                    ContextBlock.FocusedDomain.COGNITION
                            )).encodeToMessage(),
                            availableMetaActionContext(),
                            new Message(Message.Character.USER, tendency)
                    );
                    Result<EvaluatorResult> result = formattedChat(
                            messages,
                            EvaluatorResult.class
                    );
                    result.onSuccess(evaluatorResult -> {
                        evaluatorResult.setTendency(tendency);
                        synchronized (evaluatorResults) {
                            evaluatorResults.add(evaluatorResult);
                        }
                    });
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        return evaluatorResults;
    }

    private Message availableMetaActionContext() {
        // TODO select and filter available MetaActions by tags and embedding
        BlockContent content = new BlockContent("available_meta_actions", "action_planner") {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendRepeatedElements(
                        document,
                        root,
                        "available_meta_action",
                        actionCapability.listAvailableMetaActions().entrySet(),
                        (block, value) -> {
                            appendTextElement(document, block, "meta_action_key", value.getKey());
                            appendTextElement(document, block, "meta_action_description", value.getValue().getDescription());
                            return Unit.INSTANCE;
                        }
                );
            }
        };
        return new ResolvedContext(List.of(content)).encodeToMessage();
    }

    @Override
    @NotNull
    public List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    @NotNull
    @Override
    public String modelKey() {
        return "action_evaluator";
    }
}
