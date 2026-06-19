package work.slhaf.partner.module.impression;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ModuleExecutionException;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.TaskBlock;

import java.util.List;

public class ImpressionUpdatePlanner extends AbstractAgentModule.Sub<ImpressionUpdateContext, Result<ImpressionUpdatePlan>> implements ActivateModel {

    private static final String MODULE_PROMPT = """
            你负责在对话 rolling 后，根据新的 memory slice 证据生成保守的实体印象更新计划。

            你只输出 ImpressionUpdatePlan 对应结构：
            - 如果没有稳定、可复用的实体信息变化，返回 REJECTED 并说明原因。
            - 只有当证据明确支持时，才返回 PREPARED 计划来创建实体或更新已有实体。
            - 不要做复杂实体合并，不要发明不在证据中的事实。
            - patch 字段必须使用简洁、稳定、可索引的表达。
            - 不要输出 CONFIRMED；CONFIRMED 只能由代码 Validator 通过后设置。
            """;

    @Override
    protected Result<ImpressionUpdatePlan> doExecute(ImpressionUpdateContext context) {
        return plan(context);
    }

    public Result<ImpressionUpdatePlan> plan(ImpressionUpdateContext context) {
        try {
            return Result.success(formattedChat(List.of(buildTaskMessage(context)), ImpressionUpdatePlan.class).getOrThrow());
        } catch (AgentRuntimeException e) {
            return Result.failure(new ModuleExecutionException(
                    "planning impression update failed",
                    this.getClass(),
                    getModuleName()
            ));
        }
    }

    private Message buildTaskMessage(ImpressionUpdateContext context) {
        return new TaskBlock("impression_update_task") {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendTextElement(document, root, "memory_unit_id", context.memoryUnitId());
                appendTextElement(document, root, "memory_slice_id", context.memorySliceId());
                appendTextElement(document, root, "summary", context.summary());
                appendTextElement(document, root, "rolling_size", Integer.toString(context.rollingSize()));
                appendTextElement(document, root, "retain_divisor", Integer.toString(context.retainDivisor()));
                appendTextElement(document, root, "slice_start_index", Integer.toString(context.sliceStartIndex()));
                appendTextElement(document, root, "slice_end_index", Integer.toString(context.sliceEndIndex()));
                appendTextElement(document, root, "slice_timestamp", Long.toString(context.sliceTimestamp()));
                appendTextElement(document, root, "unit_timestamp", Long.toString(context.unitTimestamp()));
                appendListElement(document, root, "increment_messages", "message", context.incrementMessages(), (element, message) -> {
                    element.setAttribute("role", message.roleValue());
                    element.setTextContent(message.getContent());
                    return Unit.INSTANCE;
                });
            }
        }.encodeToMessage();
    }

    @Override
    public @NotNull String modelKey() {
        return "impression_update_planner";
    }

    @Override
    public @NotNull List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }
}
