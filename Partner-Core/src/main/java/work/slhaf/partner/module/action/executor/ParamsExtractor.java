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

import java.util.List;

/**
 * 负责依据输入内容进行行动单元的参数信息提取
 */
public class ParamsExtractor extends AbstractAgentModule.Sub<ExtractorInput, Result<ExtractorResult>> implements ActivateModel {

    private static final String MODULE_PROMPT = """
            你负责为指定行动提取参数信息。
            
            你会收到：
            - 一条结构化上下文消息，其中可能包含当前行动相关状态、近期交流轨迹、以及活跃记忆切片；
            - 一条任务消息，其中包含：
              - target_action：本次参数提取所面向的目标行动，用于帮助你判断上下文中哪些内容与当前提取直接相关；
              - meta_action_info：该行动对应的说明，以及允许提取的参数列表。每个 <param name="..."> 节点的文本内容表示该参数的含义或期望内容。
            
            你的任务：
            - 根据当前上下文与目标行动信息，提取本次行动所需的参数；
            - 只提取能够从当前输入、近期会话、相关行动历史或明确上下文中得到支持的参数；
            - 若当前信息不足以支持可靠提取，则返回 ok=false，而不是猜测或编造参数。
            
            提取原则：
            - target_action 用于帮助你在上下文中定位当前面对的是哪一个行动，不要把无关行动历史混入当前参数提取。
            - action 域中的执行中行动块及其阶段历史，可作为理解当前行动推进位置、已知条件与已出现参数的参考。
            - communication 域主要用于理解用户最近表达的条件、补充、修正、确认或否定信息。
            - memory 域只在与当前目标行动明显相关时作为辅助参考使用。
            - params 中只能填写 meta_action_info.params 中声明过的参数名，不要编造不存在的参数。
            - 每个参数值都应尽量保持为简洁、明确、可直接使用的文本，不要输出冗长解释。
            - 若某个参数无法从现有信息中可靠确定，就不要填写它。
            
            关于输出：
            - ok=true 表示你已经提取出了一组可用参数；不要求必须填满全部参数，但结果应足以支持后续执行继续推进。
            - ok=false 表示当前信息不足以形成可靠参数结果。
            - params 为参数项列表；每一项都必须包含：
              - name: 参数名
              - value: 参数值文本
            - 不要把 params 输出为对象、Map 或其他结构，只能输出 ParamEntry 列表。
            - 不要输出结构之外的解释、说明或额外文本。
            
            输出要求：
            - 严格按照 ExtractorResult 对应结构输出。
            """;

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    protected @NotNull Result<ExtractorResult> doExecute(ExtractorInput input) {
        List<Message> messages = List.of(
                resolveContextMessage(),
                resolveTaskMessage(input)
        );
        return formattedChat(messages, ExtractorResult.class);
    }

    private Message resolveTaskMessage(ExtractorInput input) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendChildElement(document, root, "target_action", block -> {
                    appendTextElement(document, block, "uuid", input.getTargetActionId());
                    appendTextElement(document, block, "description", input.getTargetActionDesc());
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
                        ContextBlock.FocusedDomain.ACTION,
                        ContextBlock.FocusedDomain.PERCEIVE,
                        ContextBlock.FocusedDomain.COMMUNICATION,
                        ContextBlock.FocusedDomain.MEMORY
                ))
                .encodeToMessage();
    }

    @Override
    @NotNull
    public List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    @NotNull
    @Override
    public String modelKey() {
        return "params_extractor";
    }
}
