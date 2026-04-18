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

public class MemoryUpdater extends AbstractAgentModule.Standalone implements AfterRolling, ActivateModel {

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
        return "topic_extractor";
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
