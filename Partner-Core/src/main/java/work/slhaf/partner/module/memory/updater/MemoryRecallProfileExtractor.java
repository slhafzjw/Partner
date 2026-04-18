package work.slhaf.partner.module.memory.updater;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.communication.AfterRolling;
import work.slhaf.partner.module.communication.AfterRollingRegistry;
import work.slhaf.partner.module.communication.RollingResult;
import work.slhaf.partner.module.memory.pojo.ActivationProfile;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.updater.summarizer.entity.MemoryTopicResult;

import java.util.List;

public class MemoryRecallProfileExtractor extends AbstractAgentModule.Standalone implements AfterRolling, ActivateModel {

    private static final String MODULE_PROMPT = """
            你负责在新的 memory slice 生成后，为该切片提取用于后续记忆索引与召回的主题信息。你的任务不是总结整段对话，也不是判断当前要召回什么记忆，而是针对“这一个新产生的记忆切片”给出：
            - 它最适合挂载到哪个主题路径；
            - 它还可以关联哪些 related topic paths；
            - 它在后续召回时的三个基础特征权重。
            
            你会收到一条任务消息，其中包含：
            - current_topic_tree：当前已有的记忆主题树结构；
            - slice_summary：当前 memory slice 的摘要；
            - message：该 memory slice 对应的原始消息片段，按发生顺序给出。
            
            你的任务：
            - 为当前这一个 memory slice 选择一个主主题路径 topicPath；
            - 视需要补充少量 relatedTopicPaths；
            - 给出 activationProfile，其中包含：
              - activationWeight
              - diffusionWeight
              - contextIndependenceWeight
            
            总体原则：
            - 你评估的是“当前这一个切片应该如何被索引和召回”，不是在做整轮对话总结。
            - slice_summary 与 message 要结合起来看；如果 summary 过于抽象，应以 message 体现出的实际讨论内容为准。
            - topicPath 应尽量对齐 current_topic_tree 已有的节点表达、层级关系与命名习惯。
            - 如果现有主题树中已有可匹配路径，优先复用，不要随意发明近义重复路径。
            - 如果当前切片只能稳定落到较上层主题，就输出较短路径；不要为了显得具体而捏造下层节点。
            - relatedTopicPaths 只用于表达这条切片还可能从哪些相邻主题方向被召回，不要泛化扩散。
            - 如果没有足够稳定的 related 方向，可以返回空列表。
            
            关于 topicPath：
            - topicPath 表示这条切片的主挂载主题。
            - topicPath 必须是纯路径文本，使用 `->` 连接层级，例如 A->B->C。
            - topicPath 应描述这条切片讨论的核心议题，而不是宽泛标签。
            - 不要输出“聊天”“对话”“问题”“事情”“内容”这类过于空泛的路径。
            - 不要在路径中附加括号说明、注释、额外解释或权重信息。
            
            关于 relatedTopicPaths：
            - relatedTopicPaths 表示与该切片存在明确相邻关系、可能支持跨主题扩散召回的少量主题路径。
            - relatedTopicPaths 中每一项也必须是 `A->B->C` 形式的纯路径文本。
            - 只有在该切片确实同时牵涉多个清晰主题方向时，才输出 relatedTopicPaths。
            - 不要因为共享几个表层关键词就添加 relatedTopicPaths。
            - 不要输出与 topicPath 语义重复的轻微改写路径。
            - 不要为了显得全面而输出很多 related paths；宁少勿滥。
            
            关于 activationProfile：
            - 所有权重都使用 0.0 到 1.0 之间的小数表示。
            - activationWeight：表示这条切片整体上值不值得在后续召回中被优先考虑。
              - 高：包含明确事实、稳定偏好、约定、计划、决策、长期背景、可复用信息。
              - 低：只是寒暄、即时反应、短时情绪、信息量很低的临时片段。
            - diffusionWeight：表示这条切片是否值得通过 relatedTopicPaths 向相邻主题扩散召回。
              - 高：内容天然横跨多个明确主题，或者对相关主题也有明显补充价值。
              - 低：虽然属于某个主题，但基本不适合跨主题带出。
            - contextIndependenceWeight：表示这条切片脱离原始上下文后，是否仍然容易被单独理解和使用。
              - 高：内容自洽、事实明确、实体清楚、单独拿出来也能理解。
              - 低：强依赖当时上下文、代词、省略、只看切片本身难以明白。
            
            评分尺度：
            - activationWeight 应反映“这条切片本身的重要性与可复用性”。
            - diffusionWeight 应反映“是否真的值得跨主题扩散”，默认应比 activationWeight 更保守。
            - contextIndependenceWeight 应反映“脱离原对话是否还能读懂和使用”。
            - 不要把所有值都打高；只有当切片在对应维度上确实强时才给高分。
            - 不要机械使用固定分数；要根据当前切片内容判断。
            
            何时应给较高 activationWeight：
            - 明确记录了事实、偏好、约束、习惯、长期计划、角色信息；
            - 形成了结论、约定、决策、方案取舍；
            - 未来很可能被再次引用。
            
            何时应给较低 activationWeight：
            - 只是礼貌回应、寒暄、附和、笑声、过渡句；
            - 主要是短时情绪或即时反馈；
            - 单独拿出来几乎没有长期价值。
            
            何时应给较高 diffusionWeight：
            - 同一切片明显同时涉及两个或多个独立但相关的主题方向；
            - 这条切片从相关主题角度看也有明确召回价值。
            
            何时应给较低 diffusionWeight：
            - 切片内容主要只属于一个主主题；
            - related 关系很弱，只是边缘沾到；
            - 只有表层关键词重合，没有稳定的跨主题价值。
            
            何时应给较高 contextIndependenceWeight：
            - 切片中讨论对象明确；
            - 即使不看原始对话，也能大致理解这条记忆在说什么；
            - 内容偏事实、偏结论、偏规则，而不是依赖上下文补全。
            
            何时应给较低 contextIndependenceWeight：
            - 大量使用“这个”“那个”“刚才说的”“上次那个”等强指代表达；
            - 切片很短，且核心信息依赖前文；
            - 单独提取出来容易误解。
            
            你不应做的事：
            - 不要回答用户问题；
            - 不要写解释、理由、备注；
            - 不要输出除 topicPath、relatedTopicPaths、activationProfile 之外的额外字段；
            - 不要把 current_topic_tree 中的节点计数或标记原样抄进路径；
            - 不要因为不确定就发明新主题或给过高权重。
            
            输出要求：
            - 严格按照 MemoryTopicResult 对应结构输出。
            - topicPath 必须是字符串。
            - relatedTopicPaths 必须是字符串数组，可为空数组。
            - activationProfile 必须包含：
              - activationWeight
              - diffusionWeight
              - contextIndependenceWeight
            - 所有路径字段都只输出纯路径文本。
            - 所有权重字段都必须是 0.0 到 1.0 的数值。
            """;

    private static final float DEFAULT_ACTIVATION_WEIGHT = 0.55f;
    private static final float DEFAULT_DIFFUSION_WEIGHT = 0.35f;
    private static final float DEFAULT_CONTEXT_INDEPENDENCE_WEIGHT = 0.50f;
    private static final float NO_RELATED_DIFFUSION_CAP = 0.45f;
    private static final float SINGLE_MESSAGE_ACTIVATION_PENALTY = 0.05f;

    @InjectModule
    private MemoryRuntime memoryRuntime;
    @InjectModule
    private AfterRollingRegistry afterRollingRegistry;

    @Init
    public void init() {
        afterRollingRegistry.register(this);
    }

    @Override
    public void consume(RollingResult result) {
        List<Message> slicedMessages = sliceMessages(result);
        if (slicedMessages.isEmpty()) {
            return;
        }
        Result<MemoryTopicResult> extractResult = formattedChat(
                List.of(
                        resolveTopicTaskMessage(result, slicedMessages)
                ),
                MemoryTopicResult.class
        );
        extractResult.onSuccess(topicResult -> {
            String topicPath = topicResult.getTopicPath() == null ? null : memoryRuntime.fixTopicPath(topicResult.getTopicPath());
            List<String> relatedTopicPaths = topicResult.getRelatedTopicPaths() == null
                    ? List.of()
                    : topicResult.getRelatedTopicPaths().stream().map(memoryRuntime::fixTopicPath).toList();
            ActivationProfile activationProfile = stabilizeActivationProfile(
                    topicResult.getActivationProfile(),
                    relatedTopicPaths,
                    slicedMessages
            );
            memoryRuntime.recordMemory(result.memoryUnit(), topicPath, relatedTopicPaths, activationProfile);
        }).onFailure(exp -> memoryRuntime.recordMemory(
                result.memoryUnit(),
                null,
                List.of(),
                defaultActivationProfile()
        ));
    }

    private List<Message> sliceMessages(RollingResult result) {
        int size = result.memoryUnit().getConversationMessages().size();
        int start = Math.clamp(result.memorySlice().getStartIndex(), 0, size);
        int end = Math.clamp(result.memorySlice().getEndIndex(), start, size);
        if (start >= end) {
            return List.of();
        }
        return result.memoryUnit().getConversationMessages().subList(start, end);
    }

    private Message resolveTopicTaskMessage(RollingResult result, List<Message> slicedMessages) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendTextElement(document, root, "current_topic_tree", memoryRuntime.getTopicTree());
                appendTextElement(document, root, "slice_summary", result.summary());
                appendRepeatedElements(document, root, "message", slicedMessages, (messageElement, message) -> {
                    messageElement.setAttribute("role", message.roleValue());
                    messageElement.setTextContent(message.getContent());
                    return kotlin.Unit.INSTANCE;
                });
            }
        }.encodeToMessage();
    }

    @Override
    @NotNull
    public String modelKey() {
        return "memory_recall_profile_extractor";
    }

    @Override
    @NotNull
    public List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    private ActivationProfile stabilizeActivationProfile(ActivationProfile activationProfile,
                                                         List<String> relatedTopicPaths,
                                                         List<Message> slicedMessages) {
        ActivationProfile profile = activationProfile == null ? defaultActivationProfile() : new ActivationProfile(
                activationProfile.getActivationWeight(),
                activationProfile.getDiffusionWeight(),
                activationProfile.getContextIndependenceWeight()
        );
        profile.setActivationWeight(clampOrDefault(profile.getActivationWeight(), DEFAULT_ACTIVATION_WEIGHT));
        profile.setDiffusionWeight(clampOrDefault(profile.getDiffusionWeight(), DEFAULT_DIFFUSION_WEIGHT));
        profile.setContextIndependenceWeight(clampOrDefault(
                profile.getContextIndependenceWeight(),
                DEFAULT_CONTEXT_INDEPENDENCE_WEIGHT
        ));

        if (relatedTopicPaths.isEmpty()) {
            profile.setDiffusionWeight(Math.min(profile.getDiffusionWeight(), NO_RELATED_DIFFUSION_CAP));
        }
        if (slicedMessages.size() <= 1) {
            profile.setActivationWeight(clamp(profile.getActivationWeight() - SINGLE_MESSAGE_ACTIVATION_PENALTY));
        }
        return profile;
    }

    private ActivationProfile defaultActivationProfile() {
        return new ActivationProfile(
                DEFAULT_ACTIVATION_WEIGHT,
                DEFAULT_DIFFUSION_WEIGHT,
                DEFAULT_CONTEXT_INDEPENDENCE_WEIGHT
        );
    }

    private float clampOrDefault(Float value, float defaultValue) {
        return value == null ? defaultValue : clamp(value);
    }

    private float clamp(float value) {
        return Math.clamp(value, 0.0f, 1.0f);
    }
}
