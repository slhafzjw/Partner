package work.slhaf.partner.module.impression;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.context.ContextWorkspace;
import work.slhaf.partner.core.cognition.impression.ActiveEntity;
import work.slhaf.partner.core.cognition.impression.Entity;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.communication.AfterRollingRegistry;
import work.slhaf.partner.module.communication.RollingResult;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImpressionUpdaterTest {

    @BeforeAll
    static void beforeAll(@TempDir Path tempDir) {
        System.setProperty("user.home", tempDir.toAbsolutePath().toString());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static RollingResult rollingResult() {
        String unitId = "impression-updater-test-" + UUID.randomUUID();
        MemoryUnit unit = new MemoryUnit(unitId);
        unit.getConversationMessages().addAll(List.of(
                new Message(Message.Character.USER, "user likes quiet tools"),
                new Message(Message.Character.ASSISTANT, "noted")
        ));
        MemorySlice slice = new MemorySlice(0, 2, "summary");
        unit.getSlices().add(slice);
        return new RollingResult(unit.snapshot(), slice.snapshot(), 2, 6);
    }

    private static ImpressionUpdatePlan plan(PlanStatus status, ImpressionUpdateStep... steps) {
        return new ImpressionUpdatePlan(List.of(steps), status, null);
    }

    @Test
    void shouldRegisterItselfToAfterRollingRegistryOnInit() throws Exception {
        ImpressionUpdater updater = new ImpressionUpdater();
        AfterRollingRegistry registry = mock(AfterRollingRegistry.class);
        setField(updater, "afterRollingRegistry", registry);

        updater.init();

        verify(registry).register(updater);
    }

    @Test
    void shouldNotApplyEmptyPlan() throws Exception {
        TestApplier applier = new TestApplier(Result.success(ImpressionUpdateApplyResult.empty()));
        ImpressionUpdater updater = updaterWith(plan(PlanStatus.PREPARED), applier, new RecordingCognitionCapability());

        updater.consume(rollingResult());

        assertEquals(0, applier.applyCount);
    }

    @Test
    void shouldNotApplyRejectedOrInvalidPlan() throws Exception {
        TestApplier applier = new TestApplier(Result.success(ImpressionUpdateApplyResult.empty()));
        ImpressionUpdater updater = updaterWith(
                plan(PlanStatus.REJECTED, new UpdateExistingStep("entity-1", new ImpressionPatch("stable"))),
                applier,
                new RecordingCognitionCapability()
        );

        updater.consume(rollingResult());

        assertEquals(0, applier.applyCount);
    }

    @Test
    void shouldApplyConfirmedUpdateExistingPlanThroughMutationApi() throws Exception {
        RecordingCognitionCapability cognitionCapability = new RecordingCognitionCapability();
        ImpressionUpdatePlanApplier applier = new ImpressionUpdatePlanApplier();
        setField(applier, "cognitionCapability", cognitionCapability);

        Result<ImpressionUpdateApplyResult> result = applier.apply(plan(
                PlanStatus.CONFIRMED,
                new UpdateExistingStep("entity-1", new ImpressionPatch("old impression", "new impression", 0.7))
        ));

        assertNull(result.exceptionOrNull());
        assertEquals("entity-1", cognitionCapability.lastImpressionEntityUuid);
        assertEquals("old impression", cognitionCapability.lastImpression);
        assertEquals("new impression", cognitionCapability.lastNewImpression);
        assertEquals(0.7, cognitionCapability.lastConfidence);
    }

    @Test
    void shouldCreateEntityApplyPatchesActivateAndRegisterActiveSnapshot() throws Exception {
        RecordingCognitionCapability cognitionCapability = new RecordingCognitionCapability();
        cognitionCapability.createdEntityUuid = "entity-created";
        ImpressionUpdatePlanApplier applier = new ImpressionUpdatePlanApplier();
        setField(applier, "cognitionCapability", cognitionCapability);

        Result<ImpressionUpdateApplyResult> result = applier.apply(plan(
                PlanStatus.CONFIRMED,
                new CreateEntityStep(
                        "User",
                        List.of(new ImpressionPatch("prefers concise updates")),
                        List.of(),
                        List.of(new AliasPatch("operator")),
                        List.of()
                )
        ));

        assertNull(result.exceptionOrNull());
        assertEquals("User", cognitionCapability.createdSubject);
        assertEquals("entity-created", cognitionCapability.lastImpressionEntityUuid);
        assertEquals("entity-created", cognitionCapability.lastAliasEntityUuid);
        assertEquals(List.of("entity-created"), result.getOrThrow().createdEntityUuids());
    }

    @Test
    void shouldConfirmValidatedPreparedPlanAndRegisterCreatedActiveEntity() throws Exception {
        RecordingCognitionCapability cognitionCapability = new RecordingCognitionCapability();
        ActiveEntity activeEntity = new ActiveEntity("runtime-1");
        activeEntity.updateSubject("User");
        activeEntity.bindEntity("entity-created");
        cognitionCapability.activatedEntity = activeEntity.snapshot();
        TestApplier applier = new TestApplier(Result.success(new ImpressionUpdateApplyResult(List.of("entity-created"))));
        ImpressionUpdater updater = updaterWith(
                plan(PlanStatus.PREPARED, new CreateEntityStep(
                        "User",
                        List.of(new ImpressionPatch("prefers concise updates")),
                        List.of(),
                        List.of(),
                        List.of()
                )),
                applier,
                cognitionCapability
        );

        updater.consume(rollingResult());

        assertEquals(1, applier.applyCount);
        assertEquals(PlanStatus.CONFIRMED, applier.lastPlanStatus);
        assertEquals("entity-created", cognitionCapability.activatedEntityUuid);
        String resolvedXml = cognitionCapability.contextWorkspace()
                .resolve(List.of(work.slhaf.partner.core.cognition.context.ContextBlock.FocusedDomain.COGNITION))
                .encodeToMessage()
                .getContent();
        assertTrue(resolvedXml.contains("active_entity_runtime-1"));
    }

    private static ImpressionUpdater updaterWith(ImpressionUpdatePlan plan,
                                                 ImpressionUpdatePlanApplier applier,
                                                 CognitionCapability cognitionCapability) throws Exception {
        ImpressionUpdater updater = new ImpressionUpdater();
        setField(updater, "cognitionCapability", cognitionCapability);
        setField(updater, "planner", new TestPlanner(Result.success(plan)));
        setField(updater, "validator", new ImpressionUpdatePlanValidator());
        setField(updater, "applier", applier);
        return updater;
    }

    private static class TestPlanner extends ImpressionUpdatePlanner {
        private final Result<ImpressionUpdatePlan> result;

        private TestPlanner(Result<ImpressionUpdatePlan> result) {
            this.result = result;
        }

        @Override
        public Result<ImpressionUpdatePlan> plan(ImpressionUpdateContext context) {
            return result;
        }
    }

    private static class TestApplier extends ImpressionUpdatePlanApplier {
        private final Result<ImpressionUpdateApplyResult> result;
        private int applyCount;
        private PlanStatus lastPlanStatus;

        private TestApplier(Result<ImpressionUpdateApplyResult> result) {
            this.result = result;
        }

        @Override
        public Result<ImpressionUpdateApplyResult> apply(ImpressionUpdatePlan plan) {
            applyCount++;
            lastPlanStatus = plan.getStatus();
            return result;
        }
    }

    private static class RecordingCognitionCapability implements CognitionCapability {
        private final ContextWorkspace contextWorkspace = new ContextWorkspace();
        private final Lock lock = new ReentrantLock();
        private String createdEntityUuid = "entity-1";
        private String createdSubject;
        private String lastImpressionEntityUuid;
        private String lastImpression;
        private String lastNewImpression;
        private double lastConfidence;
        private String lastAliasEntityUuid;
        private String activatedEntityUuid;
        private ActiveEntity activatedEntity;

        @Override
        public void initiateTurn(String input, String target, String... skippedModules) {
        }

        @Override
        public ContextWorkspace contextWorkspace() {
            return contextWorkspace;
        }

        @Override
        public List<Message> getChatMessages() {
            return List.of();
        }

        @Override
        public List<Message> snapshotChatMessages() {
            return List.of();
        }

        @Override
        public void rollChatMessagesWithSnapshot(int snapshotSize, int retainDivisor) {
        }

        @Override
        public void refreshRecentChatMessagesContext() {
        }

        @Override
        public Element messageNotesElement() {
            return null;
        }

        @Override
        public Lock getMessageLock() {
            return lock;
        }

        @Override
        public Set<ActiveEntity> projectEntity(String input) {
            return Set.of();
        }

        @Override
        public Map<ActiveEntity, Entity> showEntities() {
            return Map.of();
        }

        @Override
        public String createEntity(String subject) {
            createdSubject = subject;
            return createdEntityUuid;
        }

        @Override
        public Entity getEntity(String uuid) {
            return null;
        }

        @Override
        public ActiveEntity activateKnownEntity(String entityUuid) {
            activatedEntityUuid = entityUuid;
            return activatedEntity;
        }

        @Override
        public boolean bindActiveEntity(String runtimeId, String entityUuid) {
            return true;
        }

        @Override
        public boolean renameEntitySubject(String entityUuid, String newSubject, boolean keepOldSubjectAsAlias) {
            return true;
        }

        @Override
        public boolean addEntityAlias(String entityUuid, String alias, boolean deprecated) {
            lastAliasEntityUuid = entityUuid;
            return true;
        }

        @Override
        public boolean updateEntityImpression(String entityUuid, String impression, String newImpression, double confidence) {
            lastImpressionEntityUuid = entityUuid;
            lastImpression = impression;
            lastNewImpression = newImpression;
            lastConfidence = confidence;
            return true;
        }

        @Override
        public boolean updateEntityFeature(String entityUuid, String feature, String newFeature, double confidence) {
            return true;
        }

        @Override
        public boolean updateEntityRelation(String entityUuid, String target, String relation, double strength) {
            return true;
        }
    }
}
