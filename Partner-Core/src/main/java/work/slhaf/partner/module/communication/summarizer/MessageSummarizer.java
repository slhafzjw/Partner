package work.slhaf.partner.module.communication.summarizer;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.TaskBlock;

import java.util.List;

public class MessageSummarizer extends AbstractAgentModule.Sub<List<Message>, Result<String>> implements ActivateModel {

    private static final String MODULE_PROMPT = """
            你负责对一组已经发生过的聊天消息进行总结整理，生成一段可供后续系统使用的摘要结果。
            
            你会收到一条结构化任务消息，其中：
            - <message_tag_notes> 说明聊天消息中可能出现的标签及其含义；
            - <chat_messages> 承载本次需要总结的消息列表，每条消息都带有 role 与正文内容。
            
            你的任务：
            - 基于整组消息，提炼出一段紧凑、连贯、信息完整的摘要；
            - 摘要应尽量覆盖这组消息中的主要事实、结论、约束、推进情况、未决点、明显态度与情绪变化；
            - 若消息中包含技术讨论、配置、代码、报错、规则、方案比较、设计判断等内容，应优先保留这些对后续理解真正有帮助的信息。
            
            摘要视角要求：
            - 摘要默认采用 AGENT 视角书写，即以“我”的立场整理这组对话，而不是使用外部旁观者口吻。
            - 对于来自 [AGENT] 或 assistant 的消息，可将其理解为我的表达、我的判断、我的推进、我的反思或我的内部反馈，并以“我”来概括。
            - 对于来自 [USER] 的消息，应明确保留其“用户”身份，不要模糊为无来源的陈述，也不要误写成“我”的观点。
            - 不要默认把整组消息改写成“用户近期……”“系统如何……”这类第三人称阶段报告，除非原消息本身就是这种汇报视角。
            - 若消息中出现 [NOT_REPLIED]，表示这是一条我未直接发给用户、但保留在交流轨迹中的内部交流结果；必要时可在摘要中说明这是我内部保留的判断或反馈。
            
            总结原则：
            - 重点提炼这组消息中真正影响后续理解和推进的信息，不要平均分配篇幅。
            - 合并重复表达、重复确认和多轮来回拉扯后的同类结论。
            - 若消息中形成了明确结论、决定、偏好、限制条件、行动推进或阶段性判断，应优先写出。
            - 若消息中仍存在未解决问题、待确认事项、分歧点或风险点，也应明确保留。
            - 若消息整体只是闲聊、感叹或状态表达，也应如实概括其主要情绪和交流走向，不要硬总结出不存在的任务结论。
            
            关于技术内容：
            - 若消息中包含代码、日志、命令输出、配置片段等长内容，不要原样大段复写；
            - 应概括其中真正关键的信息，例如：关键报错、关键配置、关键判断、关键修改点、关键结果。
            - 只有在少量原文片段对后续理解不可替代时，才可保留必要短句。
            
            输出要求：
            - 只输出一段摘要正文，不要添加标题、前缀、说明或额外标签。
            - 不要输出项目符号列表，除非原始内容极度结构化且不用列表会明显损失可读性。
            - 不要输出结构之外的解释、注释或额外文本。
            """;

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    protected @NotNull Result<String> doExecute(List<Message> messages) {
        return chat(List.of(buildChatMessagesBlock(messages).encodeToMessage()));
    }

    private @NotNull TaskBlock buildChatMessagesBlock(List<Message> messages) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                document.appendChild(document.importNode(cognitionCapability.messageNotesElement(), true));
                appendListElement(document, root, "chat_messages", "message", messages, (element, message) -> {
                    element.setAttribute("role", message.roleValue());
                    element.setTextContent(message.getContent());
                    return Unit.INSTANCE;
                });
            }
        };
    }

    @Override
    @NotNull
    public List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    @NotNull
    @Override
    public String modelKey() {
        return "multi_summarizer";
    }
}
