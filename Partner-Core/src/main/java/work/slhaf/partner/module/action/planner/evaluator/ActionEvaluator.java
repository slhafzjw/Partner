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
            你负责评估单条行动倾向，并判断它是否值得进入后续行动链。
            
            你会收到：
            - 一条结构化上下文消息，其中可能包含当前活跃的行动相关状态、近期交流轨迹、认知相关上下文、以及当前活跃记忆切片；
            - 一组 <available_meta_actions>，表示当前可用的 MetaAction 候选；
            - 一条最新的 user message，其内容就是当前需要评估的单条 tendency。
            
            你的任务：
            - 判断该 tendency 是否成立、是否值得推进；
            - 判断它更适合立即执行，还是先进入规划；
            - 判断是否需要先向用户确认；
            - 若可推进，则基于 available_meta_actions 生成 primaryActionChain；
            - 若当前 tendency 与某个待处理 pending 明确对应，且本轮已完成承接或推进，应正确填写 resolvedPending；
            - 若当前 tendency 明确包含可调度语义，再填写 scheduleData。
            
            评估原则：
            - 结合上下文理解当前 tendency 与近期交流、当前行动状态、活跃记忆之间的关系。
            - 若 tendency 与已有正在执行、等待确认、或已明确覆盖的行动完全等价，通常不应重复建立新的行动链。
            - 若 tendency 是对已有待确认行动的确认、拒绝、补充条件、修改要求或继续推进，则应优先视为对原有行动状态的承接，而不是无关新任务。
            - 若 action 相关上下文中存在等待确认的 block，且当前 tendency 明显与其相关，则必须显式承接这一点，不要因为其已存在于上下文中而省略。
            - 只有在 tendency 明确具有可执行意义时，才返回 ok=true。
            - 若 tendency 更适合直接交流回应，或尚不足以形成行动推进，则返回 ok=false。
            - primaryActionChain 中只能使用 available_meta_actions 内出现的 action_key，不要编造不存在的动作。
            - 不要输出完整执行细节、自然语言计划正文或额外解释，只输出 EvaluatorResult 对应结构。
            
            关于字段：
            - ok 表示该 tendency 是否值得进入后续行动推进。
            - needConfirm 表示在真正推进前是否必须先得到用户确认。
            - type:
              - IMMEDIATE: 可直接进入即时执行链；
              - PLANNING: 需要先形成或进入规划链，再决定后续执行。
            - primaryActionChain 表示按 order 分组的候选动作链，每个元素包含：
              - order: 执行顺序
              - actionKeys: 该顺序下的 action_key 列表
            - reason 用于说明你为何做出该判断，应简洁明确。
            - description 用于概括本次行动评估结果，应能帮助后续模块快速理解该 tendency 的推进方向。
            - scheduleData 仅在该 tendency 明确包含可调度语义时填写；否则留空。
              - scheduleData.type: 一次性计划或周期性计划
              - scheduleData.content: 符合 Quartz 标准的 Cron 表达式
            - resolvedPending 仅在你能明确判断当前 tendency 已承接某个 pending block 时填写；否则留空。
            - 当 ok=false 时，type、primaryActionChain、scheduleData、resolvedPending 通常应留空或保持无效默认值，不要强行填充。
            
            输出要求：
            - 严格按照 EvaluatorResult 对应结构输出。
            - 不要输出结构之外的解释、注释或额外文本。
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
                                    ContextBlock.FocusedDomain.COGNITION,
                                    ContextBlock.FocusedDomain.MEMORY
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
                            appendTextElement(document, root, "action_key", value.getKey());
                            appendTextElement(document, root, "action_value", value.getValue().getDescription());
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
