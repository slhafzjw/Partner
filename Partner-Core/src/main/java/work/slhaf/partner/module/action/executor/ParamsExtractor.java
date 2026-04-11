package work.slhaf.partner.module.action.executor;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.action.executor.entity.ExtractorInput;
import work.slhaf.partner.module.action.executor.entity.ExtractorResult;

import java.util.HashMap;
import java.util.List;

/**
 * 负责依据输入内容进行行动单元的参数信息提取
 */
public class ParamsExtractor extends AbstractAgentModule.Sub<ExtractorInput, ExtractorResult> implements ActivateModel {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public ExtractorResult execute(ExtractorInput input) {
        List<Message> messages = List.of(
                resolveContextMessage(),
                resolveTaskMessage(input)
        );
        Result<ExtractorResult> result = formattedChat(messages, ExtractorResult.class);
        if (result.isFailure()) {
            log.error("ParamsExtractor解析结果失败", result.exceptionOrNull());
            ExtractorResult fallback = new ExtractorResult();
            fallback.setOk(false);
            fallback.setParams(new HashMap<>());
            return fallback;
        }
        return result.getOrThrow();
    }

    private Message resolveTaskMessage(ExtractorInput input) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendChildElement(document, root, "target_action", blok -> {
                    appendTextElement(document, blok, "uuid", input.getTargetActionId());
                    appendTextElement(document, blok, "description", input.getTargetActionDesc());
                    return Unit.INSTANCE;
                });
                appendChildElement(document, root, "meta_action_info", element -> {
                    MetaActionInfo info = input.getMetaActionInfo();
                    appendTextElement(document, element, "description", info.getDescription());
                    appendListElement(document, element, "params", "param", info.getParams().entrySet(), (item, param) -> {
                        item.setAttribute("name", param.getKey());
                        item.setTextContent(param.getValue());
                        return Unit.INSTANCE;
                    });
                    return Unit.INSTANCE;
                });
            }

        }.encodeToMessage();
    }

    private Message resolveContextMessage() {
        return cognitionCapability.contextWorkspace()
                .resolve(List.of(
                        ContextBlock.VisibleDomain.ACTION,
                        ContextBlock.VisibleDomain.COGNITION,
                        ContextBlock.VisibleDomain.MEMORY
                ))
                .encodeToMessage();
    }

    @NotNull
    @Override
    public String modelKey() {
        return "params_extractor";
    }
}
