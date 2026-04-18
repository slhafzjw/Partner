package work.slhaf.partner.module.memory.updater;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.communication.AfterRollingRegistry;
import work.slhaf.partner.module.communication.RollingResult;
import work.slhaf.partner.module.memory.pojo.ActivationProfile;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.updater.summarizer.entity.MemoryTopicResult;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryUpdaterTest {

    @BeforeAll
    static void beforeAll(@TempDir Path tempDir) {
        System.setProperty("user.home", tempDir.toAbsolutePath().toString());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Message message(Message.Character role, String content) {
        return new Message(role, content);
    }

    @Test
    void shouldRegisterItselfToAfterRollingRegistryOnInit() throws Exception {
        MemoryUpdater updater = Mockito.spy(new MemoryUpdater());
        AfterRollingRegistry registry = Mockito.mock(AfterRollingRegistry.class);
        setField(updater, "afterRollingRegistry", registry);

        updater.init();

        verify(registry).register(updater);
    }

    @Test
    void shouldExtractTopicAndRecordMemoryOnConsume() throws Exception {
        MemoryUpdater updater = Mockito.spy(new MemoryUpdater());
        MemoryRuntime memoryRuntime = Mockito.mock(MemoryRuntime.class);
        setField(updater, "memoryRuntime", memoryRuntime);
        when(memoryRuntime.getTopicTree()).thenReturn("topic-tree");
        when(memoryRuntime.fixTopicPath("root[2]->branch[1]")).thenReturn("root->branch");
        when(memoryRuntime.fixTopicPath("root[2]->related[1]")).thenReturn("root->related");

        MemoryTopicResult topicResult = new MemoryTopicResult();
        topicResult.setTopicPath("root[2]->branch[1]");
        topicResult.setRelatedTopicPaths(List.of("root[2]->related[1]"));
        topicResult.setActivationProfile(new ActivationProfile(0.8f, 0.9f, 0.7f));
        Mockito.doReturn(Result.success(topicResult))
                .when(updater)
                .formattedChat(Mockito.anyList(), eq(MemoryTopicResult.class));

        MemoryUnit unit = new MemoryUnit("session-1");
        unit.getConversationMessages().addAll(List.of(
                message(Message.Character.USER, "old"),
                message(Message.Character.ASSISTANT, "old-reply"),
                message(Message.Character.USER, "new"),
                message(Message.Character.ASSISTANT, "new-reply")
        ));
        MemorySlice slice = new MemorySlice(2, 4, "slice-summary");
        unit.getSlices().add(slice);

        updater.consume(new RollingResult(unit, slice, List.of(
                message(Message.Character.USER, "new"),
                message(Message.Character.ASSISTANT, "new-reply")
        ), "slice-summary", 4, 6));

        verify(memoryRuntime).recordMemory(
                eq(unit),
                eq("root->branch"),
                eq(List.of("root->related")),
                argThat(profile -> profile != null
                        && profile.getActivationWeight() == 0.8f
                        && profile.getDiffusionWeight() == 0.9f
                        && profile.getContextIndependenceWeight() == 0.7f)
        );
    }

    @Test
    void shouldFallbackToDateOnlyRecordWhenExtractionFails() throws Exception {
        MemoryUpdater updater = Mockito.spy(new MemoryUpdater());
        MemoryRuntime memoryRuntime = Mockito.mock(MemoryRuntime.class);
        setField(updater, "memoryRuntime", memoryRuntime);
        when(memoryRuntime.getTopicTree()).thenReturn("topic-tree");
        Mockito.doReturn(Result.failure(new AgentRuntimeException("boom")))
                .when(updater)
                .formattedChat(Mockito.anyList(), eq(MemoryTopicResult.class));

        MemoryUnit unit = new MemoryUnit("session-2");
        unit.getConversationMessages().addAll(List.of(
                message(Message.Character.USER, "u1"),
                message(Message.Character.ASSISTANT, "a1")
        ));
        MemorySlice slice = new MemorySlice(0, 2, "slice-summary");
        unit.getSlices().add(slice);

        updater.consume(new RollingResult(unit, slice, unit.getConversationMessages(), "slice-summary", 2, 6));

        verify(memoryRuntime).recordMemory(
                eq(unit),
                eq(null),
                eq(List.of()),
                argThat(profile -> profile != null
                        && profile.getActivationWeight() == 0.55f
                        && profile.getDiffusionWeight() == 0.35f
                        && profile.getContextIndependenceWeight() == 0.50f)
        );
    }

    @Test
    void shouldClampAndAdjustActivationProfileBeforeRecording() throws Exception {
        MemoryUpdater updater = Mockito.spy(new MemoryUpdater());
        MemoryRuntime memoryRuntime = Mockito.mock(MemoryRuntime.class);
        setField(updater, "memoryRuntime", memoryRuntime);
        when(memoryRuntime.getTopicTree()).thenReturn("topic-tree");
        when(memoryRuntime.fixTopicPath("root[2]->branch[1]")).thenReturn("root->branch");

        MemoryTopicResult topicResult = new MemoryTopicResult();
        topicResult.setTopicPath("root[2]->branch[1]");
        topicResult.setRelatedTopicPaths(List.of());
        topicResult.setActivationProfile(new ActivationProfile(1.5f, 0.9f, -0.2f));
        Mockito.doReturn(Result.success(topicResult))
                .when(updater)
                .formattedChat(Mockito.anyList(), eq(MemoryTopicResult.class));

        MemoryUnit unit = new MemoryUnit("session-3");
        unit.getConversationMessages().add(message(Message.Character.USER, "only"));
        MemorySlice slice = new MemorySlice(0, 1, "slice-summary");
        unit.getSlices().add(slice);

        updater.consume(new RollingResult(unit, slice, unit.getConversationMessages(), "slice-summary", 1, 6));

        verify(memoryRuntime).recordMemory(
                eq(unit),
                eq("root->branch"),
                eq(List.of()),
                argThat(profile -> profile != null
                        && profile.getActivationWeight() == 0.95f
                        && profile.getDiffusionWeight() == 0.45f
                        && profile.getContextIndependenceWeight() == 0.0f)
        );
    }
}
