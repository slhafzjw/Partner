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
    public List<EvaluatorResult> execute(EvaluatorInput data) {
        List<String> tendencies = data.getTendencies();
        CountDownLatch latch = new CountDownLatch(tendencies.size());
        List<EvaluatorResult> evaluatorResults = new ArrayList<>();

        for (String tendency : tendencies) {
            executor.execute(() -> {
                try {
                    List<Message> messages = List.of(
                            cognitionCapability.contextWorkspace().resolve(List.of(
                                    ContextBlock.VisibleDomain.ACTION,
                                    ContextBlock.VisibleDomain.COGNITION,
                                    ContextBlock.VisibleDomain.MEMORY
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

    @NotNull
    @Override
    public String modelKey() {
        return "action_evaluator";
    }
}
