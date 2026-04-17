package work.slhaf.partner.module.communication.summarizer;

import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class MessageCompressor extends AbstractAgentModule.Sub<List<Message>, Void> implements ActivateModel {

    private static final String MODULE_PROMPT = """
            你负责对单条消息进行压缩改写。
            
            目标：
            - 在不改变原意的前提下，压缩冗余表达，减少长度；
            - 保留消息中真正有价值的信息；
            - 让压缩结果仍然像原消息，而不是另一种文体的摘要。
            
            核心要求：
            - 尽量保留原消息的视角、语气、态度、情绪与表达倾向，不要无故改写成中性、客观、旁白式总结。
            - 不要把第一人称改成第三人称；不要把直接表达改成“用户表示……”“其意思是……”这类转述，除非原消息本身就是这种口吻。
            - 若原消息包含明显的情绪、评价、犹豫、强调、否定、推进意图、反问、吐槽等内容，压缩后应尽量保留这些信息。
            - 压缩的重点是删除冗余、合并重复、收紧表达，不是改写说话风格。
            
            格式要求：
            - 允许保留原消息中已有的 markdown、标题、项目符号、编号列表、引用、代码块、代码片段等结构。
            - 不要为了压缩而强行去除这些结构；若这些结构本身承载了信息层级或语义边界，应尽量保留。
            - 也不要为了“更整齐”主动新增原消息没有的标题、列表或代码块。
            - 原消息有结构时，优先继承其组织方式；原消息没有结构时，保持自然文本即可。
            
            压缩策略：
            - 删除明显重复、空转、口头垫话、对主旨无帮助的展开。
            - 合并语义接近、重复推进的句子。
            - 保留真正影响理解的事实、判断、条件、限制、结论、态度和情绪。
            - 若原消息包含技术内容、代码、配置、接口、规则、步骤等，优先保留这些实质信息，不要只保留泛泛结论。
            - 若原消息本身已经很短或进一步压缩会损失重要语义，则可基本保持原样。
            
            关于日志、代码及长文本片段：
            - 若原消息中包含日志、代码、配置、报错堆栈、命令输出等长片段，且内容较长、重复性强或并非全部都对理解当前消息同等重要，则可以进行截断。
            - 截断时应优先保留：
              - 与当前问题、判断、结论直接相关的部分；
              - 首尾中能体现上下文和结果的关键部分；
              - 报错、异常、返回值、状态变化、关键参数、关键命令、关键代码段。
            - 不要无说明地直接删去中间内容；若发生截断，必须显式标注。
            - 截断标注统一使用以下格式之一，并与原文风格保持尽量一致：
              - `...[中间内容已截断]...`
              - 代码或日志块内可使用：`// ...[中间内容已截断]...` 或 `# ...[中间内容已截断]...`
            - 截断后的内容仍应保持可读，且不能歪曲原始含义。
            - 若长片段本身就是当前消息的核心，且截断会损失关键语义，则不要截断。
            
            禁止事项：
            - 不要补充原消息没有的新信息。
            - 不要替原消息做解释、分析、总结或评价。
            - 不要把技术表达改写得过于口语化，也不要把口语表达改写得过于书面化。
            - 不要输出“压缩后：”之类前缀，只直接输出压缩结果。
            
            输出要求：
            - 只输出压缩后的消息正文。
            """;

    private static final int COMPRESS_TRIGGER_LENGTH = 1200;
    private static final int FALLBACK_MAX_LENGTH = 900;
    private static final String FALLBACK_OMITTED_MARKER = "\n...[中间内容已裁剪]...\n";
    private static final String COMPRESSED_MARKER = "[COMPRESSED]";
    private static final String UNKNOWN_ROLE_MARKER = "[[Unknown]: Unknown]";
    private static final String MARKER_BODY_SEPARATOR = ":\n\n";
    private static final Pattern ROLE_PREFIX_PATTERN = Pattern.compile("(\\[\\[(?:USER|AGENT)]:\\s*[^]]+])");

    @InjectCapability
    private ActionCapability actionCapability;

    private ExecutorService executor;

    @Init
    public void init() {
        executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
    }

    @Override
    protected Void doExecute(List<Message> chatMessages) {
        List<Integer> targetIndexes = IntStream.range(0, chatMessages.size())
                .filter(index -> shouldCompress(chatMessages.get(index)))
                .boxed()
                .toList();
        CountDownLatch latch = new CountDownLatch(targetIndexes.size());
        for (Integer index : targetIndexes) {
            Message chatMessage = chatMessages.get(index);
            ParsedMessage parsedMessage = parseMessage(chatMessage.getContent());
            executor.execute(() -> {
                try {
                    String summarized = summarizeOrFallback(parsedMessage.body());
                    chatMessages.set(index, new Message(chatMessage.getRole(), rebuildMessage(parsedMessage, summarized)));
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
        return null;
    }

    private boolean shouldCompress(Message chatMessage) {
        return parseMessage(chatMessage.getContent()).body().length() > COMPRESS_TRIGGER_LENGTH;
    }

    private String summarizeOrFallback(String content) {
        String summarized = chat(List.of(new Message(Message.Character.USER, content))).fold(
                res -> res,
                exp -> null
        );
        if (isAcceptableSummary(summarized, content)) {
            return summarized.trim();
        }
        return truncateForFallback(content);
    }

    private boolean isAcceptableSummary(String summarized, String originalContent) {
        if (summarized == null) {
            return false;
        }
        String normalized = summarized.trim();
        return !normalized.isEmpty() && normalized.length() < originalContent.length();
    }

    private String truncateForFallback(String content) {
        if (content == null || content.length() <= FALLBACK_MAX_LENGTH) {
            return content;
        }
        int available = FALLBACK_MAX_LENGTH - FALLBACK_OMITTED_MARKER.length();
        int headBudget = available / 2;
        int tailBudget = available - headBudget;
        int headEnd = adjustHeadEnd(content, headBudget);
        int tailStart = adjustTailStart(content, content.length() - tailBudget);
        if (tailStart <= headEnd) {
            return content.substring(0, FALLBACK_MAX_LENGTH).stripTrailing();
        }
        return content.substring(0, headEnd).stripTrailing()
                + FALLBACK_OMITTED_MARKER
                + content.substring(tailStart).stripLeading();
    }

    private int adjustHeadEnd(String content, int preferredEnd) {
        int safePreferredEnd = Math.clamp(preferredEnd, 0, content.length());
        int windowEnd = Math.min(content.length(), safePreferredEnd + 80);
        for (int i = safePreferredEnd; i < windowEnd; i++) {
            if (isBoundary(content.charAt(i))) {
                return i + 1;
            }
        }
        return safePreferredEnd;
    }

    private int adjustTailStart(String content, int preferredStart) {
        int safePreferredStart = Math.clamp(preferredStart, 0, content.length());
        int windowStart = Math.max(0, safePreferredStart - 80);
        for (int i = safePreferredStart; i > windowStart; i--) {
            if (isBoundary(content.charAt(i - 1))) {
                return i;
            }
        }
        return safePreferredStart;
    }

    private boolean isBoundary(char ch) {
        return ch == '\n'
                || ch == '。'
                || ch == '！'
                || ch == '？'
                || ch == ';'
                || ch == '；'
                || ch == '.';
    }

    private ParsedMessage parseMessage(String content) {
        String source = content == null ? "" : content;
        int separatorIndex = source.indexOf(MARKER_BODY_SEPARATOR);
        String markerLine = separatorIndex >= 0 ? source.substring(0, separatorIndex).trim() : "";
        String remaining = separatorIndex >= 0 ? source.substring(separatorIndex + MARKER_BODY_SEPARATOR.length()).trim() : source.trim();
        String rolePrefix = null;
        String statusMarkers = "";

        Matcher roleMatcher = ROLE_PREFIX_PATTERN.matcher(markerLine);
        if (roleMatcher.find()) {
            rolePrefix = roleMatcher.group(1);
            statusMarkers = markerLine.substring(roleMatcher.end()).trim();
            if (statusMarkers.startsWith(":")) {
                statusMarkers = statusMarkers.substring(1).trim();
            }
            if (statusMarkers.endsWith(":")) {
                statusMarkers = statusMarkers.substring(0, statusMarkers.length() - 1).trim();
            }
        }
        return new ParsedMessage(rolePrefix, statusMarkers, remaining);
    }

    private String rebuildMessage(ParsedMessage parsedMessage, String compressedBody) {
        return buildMarkerHeader(parsedMessage.rolePrefix(), parsedMessage.statusMarkers())
                + MARKER_BODY_SEPARATOR
                + compressedBody;
    }

    private String buildMarkerHeader(String rolePrefix, String statusMarkers) {
        String identityMarker = rolePrefix == null || rolePrefix.isBlank() ? UNKNOWN_ROLE_MARKER : rolePrefix;
        String normalizedStatusMarkers = statusMarkers == null ? "" : statusMarkers.trim();
        normalizedStatusMarkers = normalizedStatusMarkers.replace(COMPRESSED_MARKER, "").trim();
        normalizedStatusMarkers += COMPRESSED_MARKER;
        return identityMarker + ": " + normalizedStatusMarkers;
    }

    @Override
    @NotNull
    public List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    @NotNull
    @Override
    public String modelKey() {
        return "single_summarizer";
    }

    private record ParsedMessage(String rolePrefix, String statusMarkers, String body) {
    }
}
