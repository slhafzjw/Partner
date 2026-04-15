package work.slhaf.partner.module.communication;

import kotlin.Unit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.Schedulable;
import work.slhaf.partner.core.action.entity.StateAction;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ExceptionReporterHandler;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.action.scheduler.ActionScheduler;
import work.slhaf.partner.module.communication.summarizer.MultiSummarizer;
import work.slhaf.partner.module.communication.summarizer.SingleSummarizer;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = true)
@Data
public class DialogRolling extends AbstractAgentModule.Running<PartnerRunningFlowContext> {

    private static final String AUTO_UPDATE_CRON = "0/10 * * * * ?";
    private static final long UPDATE_TRIGGER_INTERVAL = 60 * 60 * 1000;
    private static final int CONTEXT_RETAIN_DIVISOR = 6;
    private static final int DIALOG_ROLLING_TRIGGER_LIMIT = 36;

    private final AtomicBoolean rolling = new AtomicBoolean(false);

    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;
    @InjectCapability
    private ActionCapability actionCapability;

    @InjectModule
    private MultiSummarizer multiSummarizer;
    @InjectModule
    private SingleSummarizer singleSummarizer;
    @InjectModule
    private ActionScheduler actionScheduler;
    @InjectModule
    private AfterRollingRegistry afterRollingRegistry;

    @Init
    public void init() {
        registerScheduledUpdater();
    }

    private void registerScheduledUpdater() {
        StateAction stateAction = new StateAction(
                "system",
                "dialog-rolling-auto-update",
                "定时检查并触发对话滚动",
                Schedulable.ScheduleType.CYCLE,
                AUTO_UPDATE_CRON,
                new StateAction.Trigger.Call(() -> {
                    tryAutoRolling();
                    return Unit.INSTANCE;
                })
        );
        actionScheduler.schedule(stateAction);
        log.info("Dialog rolling has been registered into ActionScheduler, cron={}", AUTO_UPDATE_CRON);
    }

    @Override
    protected void doExecute(@NotNull PartnerRunningFlowContext context) {
        if (cognitionCapability.getChatMessages().size() < DIALOG_ROLLING_TRIGGER_LIMIT) {
            return;
        }
        actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL).execute(() -> triggerRolling(false));
    }

    private void tryAutoRolling() {
        long currentTime = System.currentTimeMillis();
        int chatCount = cognitionCapability.snapshotChatMessages().size();
        if (currentTime - perceiveCapability.showLastInteract().toEpochMilli() > UPDATE_TRIGGER_INTERVAL && chatCount > 1) {
            triggerRolling(true);
            log.debug("Dialog rolling: auto triggered");
        }
    }

    private void triggerRolling(boolean refreshMemoryId) {
        if (!rolling.compareAndSet(false, true)) {
            log.debug("Dialog rolling: rolling is already executing");
            return;
        }
        try {
            List<Message> fullChatSnapshot = cognitionCapability.snapshotChatMessages();
            if (fullChatSnapshot.size() <= 1) {
                return;
            }
            List<Message> chatIncrement = resolveChatIncrement(fullChatSnapshot);
            if (chatIncrement.isEmpty()) {
                if (refreshMemoryId) {
                    memoryCapability.refreshMemorySession();
                }
                return;
            }

            RollingResult result = buildRollingResult(chatIncrement, fullChatSnapshot.size(), CONTEXT_RETAIN_DIVISOR);
            applyRolling(result);
            afterRollingRegistry.trigger(result);

            if (refreshMemoryId) {
                memoryCapability.refreshMemorySession();
            }
        } catch (Exception e) {
            ExceptionReporterHandler.INSTANCE.report(new AgentRuntimeException("Dialog rolling failed", e));
        } finally {
            rolling.set(false);
        }
    }

    List<Message> resolveChatIncrement(List<Message> fullChatSnapshot) {
        String memoryId = memoryCapability.getMemorySessionId();
        if (memoryId.isBlank()) {
            return fullChatSnapshot;
        }
        MemoryUnit existingUnit = memoryCapability.getMemoryUnit(memoryId);
        if (existingUnit.getConversationMessages().isEmpty()) {
            return fullChatSnapshot;
        }
        List<Message> existingMessages = existingUnit.getConversationMessages();
        int maxOverlap = Math.min(existingMessages.size(), fullChatSnapshot.size());
        for (int overlap = maxOverlap; overlap > 0; overlap--) {
            List<Message> existingSuffix = existingMessages.subList(existingMessages.size() - overlap, existingMessages.size());
            List<Message> snapshotPrefix = fullChatSnapshot.subList(0, overlap);
            if (existingSuffix.equals(snapshotPrefix)) {
                return fullChatSnapshot.subList(overlap, fullChatSnapshot.size());
            }
        }
        return fullChatSnapshot;
    }

    @NotNull
    RollingResult buildRollingResult(List<Message> chatSnapshot, int rollingSize, int retainDivisor) {
        singleSummarizer.execute(chatSnapshot);
        Result<String> summaryResult = multiSummarizer.execute(chatSnapshot);
        String summary = summaryResult.fold(
                value -> value,
                exp -> "no summary, due to exception"
        );
        if (summary.isBlank()) {
            summary = "no summary, due to empty summarize result";
        }
        MemoryUnit memoryUnit = memoryCapability.updateMemoryUnit(chatSnapshot, summary);
        MemorySlice newSlice = memoryUnit.getSlices().getLast();
        return new RollingResult(memoryUnit, newSlice, List.copyOf(chatSnapshot), newSlice.getSummary(), rollingSize, retainDivisor);
    }

    private void applyRolling(RollingResult result) {
        cognitionCapability.contextWorkspace().register(new ContextBlock(
                buildDialogAbstractBlock(result.summary(), result.memoryUnit().getId(), result.memorySlice().getId()),
                Set.of(ContextBlock.FocusedDomain.MEMORY, ContextBlock.FocusedDomain.COMMUNICATION),
                20,
                5,
                10
        ));
        cognitionCapability.rollChatMessagesWithSnapshot(result.rollingSize(), result.retainDivisor());
    }

    private @NotNull BlockContent buildDialogAbstractBlock(String summary, String unitId, String sliceId) {
        return new BlockContent("dialog_history", "dialog_rolling") {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                root.setAttribute("related_memory_unit_id", unitId);
                root.setAttribute("related_memory_slice_id", sliceId);
                root.setAttribute("datetime", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                appendTextElement(document, root, "summary", summary);
            }
        };
    }

    @Override
    public int order() {
        return 7;
    }
}
