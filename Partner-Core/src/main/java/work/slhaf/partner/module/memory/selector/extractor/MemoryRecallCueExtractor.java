package work.slhaf.partner.module.memory.selector.extractor;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorResult;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MemoryRecallCueExtractor extends AbstractAgentModule.Sub<ExtractorInput, ExtractorResult> implements ActivateModel {

    private static final String MODULE_PROMPT = """
            你负责在记忆召回前，根据当前新输入与现有语境，提取本次值得检索的记忆线索。你的任务不是直接召回记忆内容，也不是总结对话，而是从当前输入中识别出：接下来应优先尝试检索哪些记忆主题路径，或是否应按具体日期检索记忆。
            
            你会收到：
            - 一条结构化上下文消息，其中包含当前活跃的 communication 域与 memory 域内容；
            - 一条任务消息，其中包含：
              - new_inputs：一组按时间顺序累积的新输入，每条输入附带 interval-to-first；
              - current_date：当前日期；
              - memory_topic_tree：当前可用的记忆主题树结构。
            
            你的任务：
            - 基于 new_inputs、当前语境与已有记忆主题树，提取本次记忆召回最值得尝试的匹配项；
            - 匹配项只允许有两类：topic 或 date；
            - topic 用于表示应优先检索的记忆主题路径；
            - date 用于表示应优先检索的具体日期；
            - 若当前输入不足以支持稳定的记忆检索线索，则返回空列表。
            
            提取原则：
            - 你的目标是提取“可用于后续召回”的线索，而不是复述输入内容本身。
            - new_inputs 应整体理解，不要只抓最后一句；如果多条输入共同收敛到同一记忆方向，应提取更稳定的主题线索。
            - communication 域用于判断当前输入是否在承接近期某段对话、某个旧话题或某个已出现过的指代对象。
            - memory 域用于辅助判断当前输入与哪些已激活记忆方向明显相关；只有在这种相关性明确时才使用，不要机械复述 memory 域内容。
            - memory_topic_tree 是 topic 提取的主要参照；topic 应尽量贴近主题树中已有的层级与命名，不要随意发明与主题树无关的新路径。
            - 如果输入只能支持较上层的主题方向，则输出较短路径；不要为了显得具体而伪造下层节点。
            - 如果输入同时指向多个可能的记忆方向，只保留最有召回价值、最稳定的少量结果，不要泛化扩散。
            
            关于 topic：
            - topic 表示一个记忆主题路径。
            - topic 的 text 必须是纯路径文本，使用 `->` 连接层级，例如 A->B->C。
            - topic 应尽量对齐 memory_topic_tree 中已有的节点表达、层级关系与命名习惯。
            - topic 应体现“当前输入最可能在回指、延续或需要补充回忆的主题”。
            - 不要输出过于空泛、没有检索价值的 topic，例如“聊天”“问题”“内容”“事情”这类抽象词。
            - 不要在 topic 中附加解释、括号说明、标签注释或额外文字。
            
            关于 date：
            - date 表示一个明确的记忆日期。
            - date 的 text 必须是可被 Java LocalDate.parse 正常解析的日期文本，即 yyyy-MM-dd。
            - 只有在输入中存在明确日期，或结合 current_date 后可以稳定推断出具体某一天时，才输出 date。
            - 像“今天”“昨天”“前天”“上周六”这类表达，只有在能够稳定落到某个具体日期时才可输出。
            - 对于“最近”“前几天”“那段时间”“之前”“上次”这类无法稳定定位到某一天的表达，不要输出 date。
            
            何时应提取 topic：
            - 当前输入明显在延续某个已出现过的话题；
            - 当前输入在追问、回指、比较、复盘某个过去讨论过的方向；
            - 当前输入虽然表面简短，但结合 communication 或 memory 上下文后，可以明确看出其所指主题；
            - 当前输入本身就在讨论一个具有长期记忆意义、适合按主题检索的内容。
            
            何时应提取 date：
            - 当前输入明确提到了某个具体日期；
            - 当前输入使用相对日期表达，但结合 current_date 可以稳定还原到具体某一天；
            - 当前输入中的回忆目标明显依赖某个特定日期，且该日期能够被明确确定。
            
            何时不应轻易输出：
            - 当前输入只是即时情绪、临时感叹或泛泛回应，没有形成稳定的记忆检索方向；
            - 只能看出模糊相关性，无法判断应检索哪个主题；
            - 只能看出模糊时间范围，无法稳定确定到某一天；
            - 仅凭 memory 域里恰好出现过某个内容，但当前输入并没有明显指向它；
            - 仅凭表层关键词联想出某条路径，但缺乏足够语境支持。
            
            关于 matches 列表：
            - 每个 match 只能是 topic 或 date 两种类型之一。
            - 可以同时输出 topic 与 date；如果两者都对当前记忆召回明显有帮助，则都可以保留。
            - 不要输出语义重复或明显冗余的 match。
            - 如果已经能够稳定定位到较具体的 topic，通常不要再同时输出它的宽泛父路径，除非父路径本身也具有独立检索价值。
            - 若没有足够明确、足够稳定的匹配项，返回空的 matches 列表。
            
            其他约束：
            - 你不是在生成记忆内容，只是在提取检索线索。
            - 不要回答用户问题，不要总结输入，不要解释推理过程。
            - 不要输出除 topic / date 之外的类型。
            - 不要编造上下文中不存在的主题、事实或日期。
            - 不要为了凑结果而输出低质量匹配项。
            
            输出要求：
            - 严格按照 ExtractorResult 对应结构输出。
            - matches 中每一项都必须只包含 type 与 text。
            - type 只能是 "topic" 或 "date"。
            - topic 的 text 必须是 `A->B->C` 形式的纯路径文本。
            - date 的 text 必须是 yyyy-MM-dd 形式的日期文本。
            """;

    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectModule
    private MemoryRuntime memoryRuntime;

    @Override
    protected ExtractorResult doExecute(ExtractorInput input) {
        ExtractorResult extractorResult;
        List<Message> messages = List.of(
                resolveContextMessage(),
                resolveTaskMessage(input)
        );
        Result<ExtractorResult> result = formattedChat(
                messages,
                ExtractorResult.class
        );
        extractorResult = result.fold(
                value -> value,
                exception -> {
                    ExtractorResult fallback = new ExtractorResult();
                    fallback.setMatches(List.of());
                    return fallback;
                }
        );
        return fix(extractorResult);
    }

    private Message resolveTaskMessage(ExtractorInput input) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendChildElement(document, root, "new_inputs", (inputsElement) -> {
                    appendListElement(document, inputsElement, "inputs", "input", input.getInputs(), (inputElement, entry) -> {
                        inputElement.setAttribute("interval-to-first", String.valueOf(entry.getOffsetMillis()));
                        inputElement.setTextContent(entry.getContent());
                        return Unit.INSTANCE;
                    });
                    return Unit.INSTANCE;
                });
                appendTextElement(document, root, "current_date", input.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                appendTextElement(document, root, "memory_topic_tree", input.getTopic_tree());
            }
        }.encodeToMessage();
    }

    private Message resolveContextMessage() {
        return cognitionCapability.contextWorkspace().resolve(List.of(
                ContextBlock.FocusedDomain.COMMUNICATION, ContextBlock.FocusedDomain.MEMORY
        )).encodeToMessage();
    }

    private ExtractorResult fix(ExtractorResult extractorResult) {
        ExtractorResult safeResult = extractorResult == null ? new ExtractorResult() : extractorResult;
        List<ExtractorResult.ExtractorMatchData> rawMatches = safeResult.getMatches();
        if (rawMatches == null || rawMatches.isEmpty()) {
            safeResult.setMatches(List.of());
            return safeResult;
        }

        List<ExtractorResult.ExtractorMatchData> normalizedMatches = new ArrayList<>();
        for (ExtractorResult.ExtractorMatchData match : rawMatches) {
            if (match == null) {
                continue;
            }
            String type = match.getType();
            String text = match.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            if (ExtractorResult.ExtractorMatchData.Constant.TOPIC.equals(type)) {
                text = memoryRuntime.fixTopicPath(text);
                if (text.isBlank() || text.split("->")[0].isEmpty()) {
                    continue;
                }
                match.setText(text);
                normalizedMatches.add(match);
                continue;
            }

            if (ExtractorResult.ExtractorMatchData.Constant.DATE.equals(type)) {
                normalizedMatches.add(match);
            }
        }
        safeResult.setMatches(normalizedMatches);
        return safeResult;
    }

    @Override
    @NotNull
    public List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    @NotNull
    @Override
    public String modelKey() {
        return "memory_recall_cue_extractor";
    }
}
