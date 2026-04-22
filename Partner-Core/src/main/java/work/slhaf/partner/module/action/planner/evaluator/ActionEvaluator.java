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
            你负责评估单条行动倾向，并判断它是否应该进入后续行动链。
            
            你会收到：
            - 一条结构化上下文消息，其中可能包含当前活跃的 action、communication、perceive、memory、cognition 相关信息；
            - 一组 <available_meta_actions>，表示当前系统真实可用的 MetaAction 候选；
            - 一条最新 user message，其内容就是当前需要评估的单条 tendency。
            
            你的任务：
            - 判断该 tendency 是否成立；
            - 判断它是否真的需要通过“行动推进”来处理，而不是直接交流回应；
            - 判断它更适合立即执行，还是先进入规划；
            - 判断是否需要先向用户确认；
            - 只有在确实可推进时，才生成 primaryActionChain；
            - 若当前 tendency 与某个待处理 pending 明确对应，且本轮已完成承接或推进，应正确填写 resolvedPending；
            - 若当前 tendency 明确包含调度语义，再填写 scheduleData。
            
            你的核心目标：
            - 不要把“可以做点什么”误判成“应该进入行动链”；
            - 不要把“值得回应”误判成“值得行动”；
            - 不要返回“能推进但实际上没有动作链”的假阳性结果；
            - 如果用户确实提出了一个行动请求，但当前系统无法承接，也要明确评估出来，而不是伪造可执行结果。
            
            基本判断原则：
            - 只有当该 tendency 需要依赖后续动作链、能力调用、系统操作、任务推进或计划安排，才考虑返回 ok=true。
            - 若当前输入更适合直接交流回应，即使它看起来“也可以通过行动补充更多信息”，通常也应返回 ok=false。
            - 若当前上下文、记忆、感知信息已经足以直接回答，则不应为了获取更多信息而发起行动。
            - “信息查询”不等于“行动请求”；只有当用户明确要求系统执行查询、访问外部资源、操作对象、推进任务或完成某项动作时，才可能成立为行动倾向。
            - 若 tendency 只是解释、分析、总结、评价、翻译、闲聊、感叹、上下文询问、系统状态询问、记忆内容询问，通常应返回 ok=false。
            - 若 tendency 只是对当前交流内容的自然延续，且通过正常回复即可完成，也应返回 ok=false。
            
            与现有行动状态的关系：
            - 若 tendency 与已有正在执行、等待确认、或已明确覆盖的行动完全等价，通常不应重复建立新的行动链。
            - 若 tendency 是对已有 pending 的确认、拒绝、补充条件、修改要求、继续推进或取消，应优先视为对原有行动状态的承接。
            - 若 action 上下文中存在等待确认的 block，且当前 tendency 明显与其相关，则必须显式承接这一点，不要将其误判为全新无关任务。
            
            关于可执行性：
            - 当 ok=true 时，primaryActionChain 必须非空。
            - primaryActionChain 中只能使用 <available_meta_actions> 中实际存在的 action_key。
            - 若当前系统没有任何可用 MetaAction 能承接该 tendency，则不得返回 ok=true。
            - 不允许输出“该 tendency 成立，但没有动作链”的结果。
            - “理论上值得做”不等于“当前可推进”；只有当当前系统存在真实可用的 action chain 时，才可返回 ok=true。
            
            关于“做不了”的情况：
            - 若 tendency 确实对应用户明确提出的行动要求，但当前系统无法生成有效的 primaryActionChain，则应返回 ok=false。
            - 在这种情况下，reason 与 description 应明确指出：当前无法执行、无法推进、暂不支持，或缺少必要能力。
            - 这样后续交流模块才能自然向用户说明“当前做不了”，而不是假装已进入执行。
            
            needConfirm 的判断：
            - 若该 tendency 代表的行动具有明显副作用、资源消耗、系统修改、外部操作、风险、持续执行或用户可能希望先确认的影响，则可设 needConfirm=true。
            - 若该 tendency 是低风险、明确、可立即推进的动作，且上下文中没有要求先确认的信号，可设 needConfirm=false。
            
            type 的判断：
            - IMMEDIATE：当前可直接进入即时执行链。
            - PLANNING：需要先形成规划、拆分步骤、组织执行路径，再决定后续执行。
            - 若 ok=false，则 type 通常留空或无效默认值，不要强行填写。
            
            primaryActionChain 的要求：
            - 只在 ok=true 时填写。
            - 每个元素包含：
              - order：执行顺序
              - actionKeys：该顺序下要使用的 action_key 列表
            - 不要编造不存在的 action_key。
            - 不要输出自然语言步骤说明，不要输出伪代码，只输出动作链结构。
            
            scheduleData 的要求：
            - 仅当该 tendency 明确包含未来时刻的调度语义时填写，否则留为空对象；
            - 若用户只是泛泛表示“以后提醒我”“找时间做”，但无法稳定落到可调度语义，则不要强填；
            - scheduleData.type 表示一次性或周期性计划；
            - scheduleData.content 必须符合 Quartz 标准的 Cron 表达式。
            
            resolvedPending 的要求：
            - 仅当你能明确判断当前 tendency 已承接某个 pending block 时填写；
            - 若无法明确对应，则留空；
            - 不要为了“看起来完整”而猜测填写。
            
            reason 与 description 的要求：
            - reason 用于简洁说明你为何做出该判断；
            - description 用于概括这条 tendency 的处理结论，帮助后续模块快速理解；
            - 若 ok=false 且原因是“更适合直接回复”，应明确体现这一点；
            - 若 ok=false 且原因是“用户要求行动，但当前做不了”，也应明确体现这一点。
            
            以下情形通常应返回 ok=false：
            - 当前输入本质上是可直接回答的问题；
            - 当前输入只是解释型、说明型、分析型、评价型、总结型请求；
            - 当前输入只是询问上下文、系统状态、记忆内容、当前可见内容；
            - 当前输入只是轻量追问、闲聊、感叹、态度表达；
            - tendency 与已有行动完全重复，且没有新的推进信息；
            - tendency 虽然像一个行动请求，但当前没有任何 available_meta_actions 能承接，无法形成有效 primaryActionChain。
            
            以下情形通常才应返回 ok=true：
            - 用户明确要求系统执行某项动作、访问外部资源、操作系统对象、调用能力、推进某项任务；
            - 用户明确要求继续、修改、取消、确认某个已有待处理行动；
            - 用户明确要求调度未来动作；
            - 当前若不进入行动链，就无法真正完成该 tendency；
            - 且当前系统确实存在可用的 primaryActionChain。
            
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
