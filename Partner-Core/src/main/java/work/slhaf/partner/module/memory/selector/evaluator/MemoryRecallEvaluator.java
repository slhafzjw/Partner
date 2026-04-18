package work.slhaf.partner.module.memory.selector.evaluator;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.memory.selector.ActivatedMemorySlice;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorBatchInput;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorBatchResult;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class MemoryRecallEvaluator extends AbstractAgentModule.Sub<EvaluatorInput, List<ActivatedMemorySlice>> implements ActivateModel {

    private static final String MODULE_PROMPT = """
            你负责在记忆切片召回流程中，对单个候选 memory slice 做保留评估。你的任务不是重新提取检索线索，也不是在多个切片之间排序，而是基于当前新输入、近期 communication 语境，以及当前这个 memory slice 本身，判断：这段切片是否值得保留进入后续记忆上下文。
            
            你会收到：
            - 一条结构化上下文消息，其中包含当前活跃的 communication 域内容；
            - 一条任务消息，其中包含：
              - new_inputs：一组按时间顺序累积的新输入，每条输入附带 interval-to-first；
              - memory_slice：当前正在评估的单个候选切片，其中包括：
                - slice_summary：该切片的摘要；
                - source_messages：该切片对应的部分原始消息。若消息较多，只会展示前两条、中间四条、末尾两条，并在中间插入省略说明。
            
            你的任务：
            - 判断当前这个 memory slice 是否与本轮输入明显相关，并且值得保留；
            - 如果值得保留，则返回 passed=true；
            - 如果不值得保留，则返回 passed=false。
            
            评估目标：
            - 这里的“值得保留”不是指“勉强有点关系”，而是指：这段切片很可能对理解当前输入、承接当前话题、补充当前回指对象、或支持接下来的回应有实际帮助。
            - 你评估的是“当前这个切片自身是否值得保留”，而不是“是否存在某个相关记忆主题”。
            - 不要把切片所属主题近期很活跃，误当成它当前就应被保留。
            
            核心判断原则：
            - new_inputs 应整体理解，不要只看最后一句；如果多条输入共同收敛到某个具体关注点，应按整体意图判断。
            - communication 域用于辅助理解当前输入是否在承接、回指或延续近期对话中的某个对象、某段讨论、某个比较目标。
            - memory_slice 是当前唯一需要评估的对象；判断重点在于它是否真正贴合当前输入，而不是它是否看起来“总体上像是相关的”。
            - slice_summary 与 source_messages 应结合起来看；如果 summary 看似相关，但 source_messages 显示其实际讨论重点并不一致，则不应仅凭 summary 通过。
            - source_messages 可能被截断展示，因此你可以基于已展示内容做审慎判断；若现有内容已足以看出明显不贴合，则应直接拒绝，不要自行脑补缺失内容。
            
            何时应通过：
            - 当前输入明显在追问、延续、回指、比较、复盘这段切片所对应的那段讨论；
            - 当前输入中的代词、简称、模糊表达，结合 communication 与该切片内容后，可以较明确地对应到这段切片；
            - 当前输入虽然没有复述切片内容，但它正在继续推进这段切片中的核心议题；
            - 当前输入需要补足的背景、上下文或前情，正是该切片能够提供的；
            - 该切片与当前输入在关注点上是同一条线，而不只是共享一些表层关键词。
            
            何时不应通过：
            - 该切片只是在宽泛主题上相关，但与当前输入的具体关注点并不一致；
            - 当前输入谈的是某条主题中的一个更具体方向，而该切片实际对应的是同主题下的另一支内容；
            - 该切片只是“最近讨论过”“当前活跃过”，但当前输入并没有明显指向它；
            - 该切片与当前输入只有表层关键词重合，缺乏稳定的语义承接；
            - 只能看出“可能有一点关系”，但不足以认为它对当前轮次真的有帮助；
            - 当前输入是在排除、收窄或转离该切片所对应的方向；
            - 仅凭切片摘要中的高层概括词、抽象标签、热门主题，就武断通过；
            - 仅因为 communication 中存在宽泛相关话题，就把当前切片也一起保留。
            
            关于评估尺度：
            - 通过标准应偏保守，宁可少保留，也不要把低质量、弱相关、误方向的切片混入后续上下文。
            - “属于同一个大主题”不等于“值得保留”。
            - “曾经讨论过类似内容”不等于“当前就在指向这段切片”。
            - “能勉强联想到”不等于“应通过”。
            - 只有当你能够比较稳定地判断：这段切片对当前输入确实有帮助时，才应通过。
            
            你不应做的事：
            - 不要重新选择别的切片；
            - 不要把别的可能更相关的记忆当成当前切片通过的理由；
            - 不要扩展用户话题；
            - 不要回答用户问题；
            - 不要总结整个对话；
            - 不要输出解释、理由、附加字段或额外文本；
            - 不要因为不确定就倾向通过。
            
            输出要求：
            - 严格按照 EvaluatorBatchResult 对应结构输出；
            - 结果中只表达当前这个 memory slice 是否通过；
            - 不要输出除结构要求之外的任何内容。
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

    @Override
    protected List<ActivatedMemorySlice> doExecute(EvaluatorInput evaluatorInput) {
        List<ActivatedMemorySlice> preparedSlices = evaluatorInput.getMemorySlices();
        List<ActivatedMemorySlice> result = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(preparedSlices.size());

        for (ActivatedMemorySlice slice : preparedSlices) {
            executor.execute(() -> {
                try {
                    EvaluatorBatchInput batchInput = new EvaluatorBatchInput(evaluatorInput.getInputs(), slice);
                    List<Message> messages = List.of(
                            resolveContextMessage(),
                            resolveTaskMessage(batchInput)
                    );
                    formattedChat(messages, EvaluatorBatchResult.class)
                            .onSuccess(evaluatorBatchResult -> {
                                if (evaluatorBatchResult.isPassed()) {
                                    synchronized (result) {
                                        result.add(slice);
                                    }
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
            Thread.currentThread().interrupt();
        }

        return result;
    }

    private Message resolveContextMessage() {
        return cognitionCapability.contextWorkspace().resolve(List.of(
                ContextBlock.FocusedDomain.COMMUNICATION
        )).encodeToMessage();
    }

    private Message resolveTaskMessage(EvaluatorBatchInput batchInput) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendChildElement(document, root, "new_inputs", (inputsElement) -> {
                    appendListElement(document, inputsElement, "inputs", "input", batchInput.getInputs(), (inputElement, entry) -> {
                        inputElement.setAttribute("interval-to-first", String.valueOf(entry.getOffsetMillis()));
                        inputElement.setTextContent(entry.getContent());
                        return Unit.INSTANCE;
                    });
                    return Unit.INSTANCE;
                });
                appendChildElement(document, root, "memory_slice", (element) -> {
                    ActivatedMemorySlice slice = batchInput.getActivatedMemorySlice();
                    appendTextElement(document, element, "slice_summary", slice.getSummary());
                    appendChildElement(document, element, "source_messages", (messagesElement) -> {
                        List<Message> messages = slice.getMessages();
                        int size = messages.size();
                        if (size > 10) {
                            // 展示前两条
                            appendMessageElement(document, messagesElement, messages.subList(0, 2));

                            // 中间省略说明
                            int middleStart = Math.max(2, (size - 4) / 2);
                            int middleEnd = Math.min(size - 2, middleStart + 4);
                            int omittedBeforeMiddle = middleStart - 2;
                            appendTextElement(document, messagesElement, "omitted_messages", "省略了 " + omittedBeforeMiddle + " 条消息");

                            // 中间四条
                            appendMessageElement(document, messagesElement, messages.subList(middleStart, middleEnd));

                            // 中间到结尾前的省略说明
                            int omittedAfterMiddle = (size - 2) - middleEnd;
                            if (omittedAfterMiddle > 0) {
                                appendTextElement(document, messagesElement, "omitted_messages", "省略了 " + omittedAfterMiddle + " 条消息");
                            }

                            // 展示末尾两条
                            appendMessageElement(document, messagesElement, messages.subList(size - 2, size));
                        } else {
                            appendMessageElement(document, messagesElement, messages);
                        }
                        return Unit.INSTANCE;
                    });
                    return Unit.INSTANCE;
                });
            }

            private void appendMessageElement(Document document, Element parent, List<Message> messages) {
                appendRepeatedElements(document, parent, "message", messages, (messageElement, message) -> {
                    messageElement.setAttribute("role", message.getRole().name().toLowerCase(Locale.ROOT));
                    messageElement.setTextContent(message.getContent());
                    return Unit.INSTANCE;
                });
            }
        }.encodeToMessage();
    }

    @Override
    @NotNull
    public List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    @Override
    @NotNull
    public String modelKey() {
        return "slice_evaluator";
    }
}
