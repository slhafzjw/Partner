package work.slhaf.partner.module.modules.action.planner.evaluator;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.core.cognition.ResolvedContext;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class ActionEvaluator extends AbstractAgentModule.Sub<EvaluatorInput, List<EvaluatorResult>> implements ActivateModel {

    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    private InteractionThreadPoolExecutor executor;

    @Init
    public void init() {
        executor = InteractionThreadPoolExecutor.getInstance();
    }

    /**
     * 对输入的行为倾向进行评估，并根据评估结果，对缓存做出调整
     *
     * @param data 评估输入内容，包含提取/命中缓存的行动倾向、近几条聊天记录，正在生效的记忆切片内容
     * @return 评估结果集合
     */
    @Override
    public List<EvaluatorResult> execute(EvaluatorInput data) {
        List<Callable<EvaluatorResult>> tasks = buildEvaluateTasks(data.getTendencies());
        return executor.invokeAllAndReturn(tasks);
    }

    private List<Callable<EvaluatorResult>> buildEvaluateTasks(List<String> tendencies) {
        List<Callable<EvaluatorResult>> list = new ArrayList<>();
        for (String tendency : tendencies) {
            list.add(() -> {
                List<Message> messages = List.of(
                        cognitionCapability.contextWorkspace().resolve(List.of(ContextBlock.VisibleDomain.ACTION, ContextBlock.VisibleDomain.COGNITION, ContextBlock.VisibleDomain.MEMORY)).encodeToContextMessage(),
                        availableMetaActionContext(),
                        new Message(Message.Character.USER, tendency)
                );
                EvaluatorResult evaluatorResult = formattedChat(
                        messages,
                        EvaluatorResult.class
                );
                evaluatorResult.setTendency(tendency);
                return evaluatorResult;
            });
        }
        return list;
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
        return new ResolvedContext(List.of(content)).encodeToContextMessage();
    }

    @NotNull
    @Override
    public String modelKey() {
        return "action_evaluator";
    }
}
