package work.slhaf.partner.module.modules.action.dispatcher.executor;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ActionExecutorInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.CorrectorResult;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ExtractorResult;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.RepairerResult;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 测试矩阵（与文档一致）：
 * 1) 单行动-单阶段-单MetaAction成功（已覆盖）
 * 2) 多行动并发执行（未覆盖：需并发稳定性/线程调度控制）
 * 3) status 非 PREPARE 直接返回（已覆盖）
 * 4) 多阶段顺序执行（已覆盖）
 * 5) IO 行动使用虚拟线程池（已覆盖）
 * 6) extractor 失败 -> repairer OK -> 再成功（已覆盖）
 * 7) extractor 失败 -> repairer FAILED（已覆盖）
 * 8) extractor 失败 -> repairer ACQUIRE 阻塞后恢复（已覆盖）
 * 9) runnerClient.submit 抛异常（未覆盖：需更精细的异常吞吐与线程结束校验）
 * 10) paramsExtractor.execute 抛异常（未覆盖：与 #9 类似，需更精细的异常吞吐校验）
 * 11) corrector.execute 抛异常导致资源未清理（已标记已知缺陷，@Disabled）
 * 12) actionChain 为空导致异常与泄漏（已标记已知缺陷，@Disabled）
 * 13) metaActions 为空导致 awaitAdvance 阻塞（未覆盖：更适合集成/压测）
 * 17) result 状态不更新导致循环不退出（未覆盖：更适合集成/压测）
 * 18) 同 stage 多 metaAction 并发完成顺序不固定（未覆盖：更适合集成/压测）
 */
@SuppressWarnings("unused")
@Slf4j
@ExtendWith(MockitoExtension.class)
class ActionExecutorTest {

    @Mock
    ActionCapability actionCapability;
    @Mock
    MemoryCapability memoryCapability;
    @Mock
    CognationCapability cognationCapability;
    @Mock
    ParamsExtractor paramsExtractor;
    @Mock
    ActionRepairer actionRepairer;
    @Mock
    ActionCorrector actionCorrector;
    @Mock
    RunnerClient runnerClient;

    @InjectMocks
    ActionExecutor actionExecutor;

    @BeforeEach
    void setUp() {
        lenient().when(cognationCapability.getChatMessages()).thenReturn(Collections.emptyList());
        lenient().when(memoryCapability.getActivatedSlices(anyString())).thenReturn(Collections.emptyList());
        lenient().when(actionCapability.putPhaserRecord(any(Phaser.class), any(ActionData.class)))
                .thenAnswer(inv -> new PhaserRecord(inv.getArgument(0), inv.getArgument(1)));
        lenient().when(actionCapability.loadMetaActionInfo(anyString())).thenAnswer(inv -> {
            MetaActionInfo info = new MetaActionInfo();
            info.setDescription("desc");
            info.setParams(Collections.emptyMap());
            return info;
        });
        CorrectorResult correctorResult = new CorrectorResult();
        correctorResult.setMetaInterventionList(Collections.emptyList());
        lenient().when(actionCorrector.execute(any())).thenReturn(correctorResult);
        lenient().doNothing().when(actionCapability).handleInterventions(any(), any());
    }

    // 场景1：B1 -> B3 -> B4 -> B7(成功) -> B10。目的：验证正常主路径与资源清理。
    @Test
    void execute_singleAction_singleStage_success() {
        ExecutorService directExecutor = new DirectExecutorService();
        stubExecutors(directExecutor, directExecutor);

        ImmediateActionData actionData = buildActionData(singleStageChain(false));
        ActionExecutorInput input = buildInput("u1", actionData);

        ExtractorResult extractorResult = new ExtractorResult();
        extractorResult.setOk(true);
        when(paramsExtractor.execute(any())).thenReturn(extractorResult);
        doAnswer(inv -> {
            MetaAction metaAction = inv.getArgument(0);
            metaAction.getResult().setStatus(MetaAction.ResultStatus.SUCCESS);
            return null;
        }).when(runnerClient).submit(any(MetaAction.class));

        actionExecutor.init();
        actionExecutor.execute(input);

        verify(runnerClient, times(1)).submit(any(MetaAction.class));
        verify(actionCapability, times(1)).removePhaserRecord(any(Phaser.class));
        assertEquals(ActionData.ActionStatus.SUCCESS, actionData.getStatus());
        assertEquals(1, actionData.getHistory().get(0).size());
    }

    // 场景3：B1 -> B2。目的：验证非 PREPARE 不执行任何子任务。
    @Test
    void execute_statusNotPrepare_shouldSkip() {
        ExecutorService directExecutor = new DirectExecutorService();
        stubExecutors(directExecutor, directExecutor);

        ImmediateActionData actionData = buildActionData(singleStageChain(false));
        actionData.setStatus(ActionData.ActionStatus.EXECUTING);
        ActionExecutorInput input = buildInput("u1", actionData);

        actionExecutor.init();
        actionExecutor.execute(input);

        verify(actionCapability, never()).putPhaserRecord(any(Phaser.class), any(ActionData.class));
        verify(runnerClient, never()).submit(any(MetaAction.class));
    }

    // 场景4：B1 -> B3 -> B4(两轮) -> B7(成功) -> B10。目的：验证多阶段顺序执行。
    @Test
    void execute_multiStage_success() {
        ExecutorService platformExecutor = Executors.newFixedThreadPool(4);
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        stubExecutors(platformExecutor, virtualExecutor);

        Map<Integer, List<MetaAction>> chain = new HashMap<>();
        chain.put(0, List.of(buildMetaAction("a1", false)));
        chain.put(1, List.of(buildMetaAction("a2", false)));
        ImmediateActionData actionData = buildActionData(chain);
        ActionExecutorInput input = buildInput("u1", actionData);

        ExtractorResult extractorResult = new ExtractorResult();
        extractorResult.setOk(true);
        when(paramsExtractor.execute(any())).thenReturn(extractorResult);
        doAnswer(inv -> {
            MetaAction metaAction = inv.getArgument(0);
            metaAction.getResult().setStatus(MetaAction.ResultStatus.SUCCESS);
            log.info("metaAction result:{}", metaAction.getResult().getStatus());
            return null;
        }).when(runnerClient).submit(any(MetaAction.class));

        actionExecutor.init();
        actionExecutor.execute(input);

        verify(runnerClient, timeout(5000).times(2)).submit(any(MetaAction.class));
        verify(actionCorrector, timeout(5000).times(2)).execute(any());
        assertEquals(2, actionData.getHistory().size());
        assertEquals(ActionData.ActionStatus.SUCCESS, actionData.getStatus());
    }

    // 场景5：B4.2。目的：验证 IO 行动使用虚拟线程池。
    @Test
    void execute_ioMetaAction_usesVirtualExecutor() {
        ExecutorService platformExecutor = Executors.newFixedThreadPool(4);
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        stubExecutors(platformExecutor, virtualExecutor);

        ImmediateActionData actionData = buildActionData(singleStageChain(true));
        ActionExecutorInput input = buildInput("u1", actionData);

        ExtractorResult extractorResult = new ExtractorResult();
        extractorResult.setOk(true);
        lenient().when(paramsExtractor.execute(any())).thenReturn(extractorResult);
        lenient().doAnswer(inv -> {
            MetaAction metaAction = inv.getArgument(0);
            metaAction.getResult().setStatus(MetaAction.ResultStatus.SUCCESS);
            return null;
        }).when(runnerClient).submit(any(MetaAction.class));

        actionExecutor.init();
        actionExecutor.execute(input);

        verify(actionCapability, times(1)).getExecutor(ActionCore.ExecutorType.VIRTUAL);
        shutdownExecutor(virtualExecutor);
    }

    // 场景6：B7.2(失败) -> repairer OK -> B7(成功)。目的：验证修复后成功与上下文追加。
    @Test
    void execute_extractorFail_thenRepairOk_thenSuccess() {
        ExecutorService platformExecutor = Executors.newFixedThreadPool(4);
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        stubExecutors(platformExecutor, virtualExecutor);

        ImmediateActionData actionData = buildActionData(singleStageChain(false));
        ActionExecutorInput input = buildInput("u1", actionData);

        ExtractorResult fail = new ExtractorResult();
        fail.setOk(false);
        ExtractorResult ok = new ExtractorResult();
        ok.setOk(true);
        when(paramsExtractor.execute(any())).thenReturn(fail, ok);

        RepairerResult repairerResult = new RepairerResult();
        repairerResult.setStatus(RepairerResult.RepairerStatus.OK);
        repairerResult.setFixedData(List.of("fix-1"));
        when(actionRepairer.execute(any())).thenReturn(repairerResult);

        doAnswer(inv -> {
            MetaAction metaAction = inv.getArgument(0);
            metaAction.getResult().setStatus(MetaAction.ResultStatus.SUCCESS);
            return null;
        }).when(runnerClient).submit(any(MetaAction.class));

        actionExecutor.init();
        actionExecutor.execute(input);

        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
        assertEquals(1, actionData.getAdditionalContext().get(0).size());
        verify(runnerClient, timeout(5000).times(1)).submit(any(MetaAction.class));
    }

    // 场景7：B7.2(失败) -> repairer FAILED。目的：验证失败分支不提交外部执行。
    @Test
    void execute_extractorFail_thenRepairFailed() {
        ExecutorService platformExecutor = Executors.newFixedThreadPool(4);
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        stubExecutors(platformExecutor, virtualExecutor);

        ImmediateActionData actionData = buildActionData(singleStageChain(false));
        ActionExecutorInput input = buildInput("u1", actionData);

        ExtractorResult fail = new ExtractorResult();
        fail.setOk(false);
        when(paramsExtractor.execute(any())).thenReturn(fail);

        RepairerResult repairerResult = new RepairerResult();
        repairerResult.setStatus(RepairerResult.RepairerStatus.FAILED);
        when(actionRepairer.execute(any())).thenReturn(repairerResult);

        actionExecutor.init();
        actionExecutor.execute(input);

        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
        MetaAction metaAction = actionData.getActionChain().get(0).get(0);
        assertEquals(MetaAction.ResultStatus.FAILED, metaAction.getResult().getStatus());
        verify(runnerClient, never()).submit(any(MetaAction.class));
    }

    // 场景8：B7.2(ACQUIRE) -> interrupt 阻塞 -> 状态恢复 -> B7(成功)。目的：验证阻塞可恢复且不死锁。
    @Test
    @Timeout(3)
    void execute_extractorFail_thenAcquire_thenResume() throws Exception {
        ExecutorService platformExecutor = Executors.newCachedThreadPool();
        ExecutorService virtualExecutor = Executors.newCachedThreadPool();
        stubExecutors(platformExecutor, virtualExecutor);

        ImmediateActionData actionData = buildActionData(singleStageChain(false));
        ActionExecutorInput input = buildInput("u1", actionData);

        ExtractorResult fail = new ExtractorResult();
        fail.setOk(false);
        ExtractorResult ok = new ExtractorResult();
        ok.setOk(true);
        when(paramsExtractor.execute(any())).thenReturn(fail, ok);

        RepairerResult repairerResult = new RepairerResult();
        repairerResult.setStatus(RepairerResult.RepairerStatus.ACQUIRE);
        when(actionRepairer.execute(any())).thenReturn(repairerResult);

        doAnswer(inv -> {
            MetaAction metaAction = inv.getArgument(0);
            metaAction.getResult().setStatus(MetaAction.ResultStatus.SUCCESS);
            return null;
        }).when(runnerClient).submit(any(MetaAction.class));

        CountDownLatch doneLatch = new CountDownLatch(1);
        doAnswer(inv -> {
            doneLatch.countDown();
            return null;
        }).when(actionCapability).removePhaserRecord(any(Phaser.class));

        ExecutorService resumeExecutor = Executors.newSingleThreadExecutor();
        resumeExecutor.execute(() -> {
            while (actionData.getStatus() != ActionData.ActionStatus.INTERRUPTED) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
            actionData.setStatus(ActionData.ActionStatus.EXECUTING);
        });

        actionExecutor.init();
        actionExecutor.execute(input);

        assertTrue(doneLatch.await(2, TimeUnit.SECONDS));
        shutdownExecutor(platformExecutor);
        shutdownExecutor(virtualExecutor);
        shutdownExecutor(resumeExecutor);
    }

    // 场景11：B4.4 异常 -> 资源未清理（已知缺陷）。目的：暴露当前行为。
    // @Disabled("known-issue: corrector 抛异常时未清理 phaser 记录")
    // @Tag("known-issue")
    @Test
    void execute_correctorThrows_shouldLeakPhaserRecord() {
        ExecutorService platformExecutor = Executors.newCachedThreadPool();
        ExecutorService virtualExecutor = Executors.newCachedThreadPool();
        stubExecutors(platformExecutor, virtualExecutor);

        ImmediateActionData actionData = buildActionData(singleStageChain(false));
        ActionExecutorInput input = buildInput("u1", actionData);

        ExtractorResult ok = new ExtractorResult();
        ok.setOk(true);
        lenient().when(paramsExtractor.execute(any())).thenReturn(ok);
        lenient().doAnswer(inv -> {
            MetaAction metaAction = inv.getArgument(0);
            metaAction.getResult().setStatus(MetaAction.ResultStatus.SUCCESS);
            return null;
        }).when(runnerClient).submit(any(MetaAction.class));

        lenient().doThrow(new RuntimeException("boom")).when(actionCorrector).execute(any());

        actionExecutor.init();
        actionExecutor.execute(input);

        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
        verify(actionCapability).removePhaserRecord(any(Phaser.class));
    }

    // 场景12：B4.1 actionChain 为空导致异常（已知缺陷）。目的：暴露当前行为。
    @Disabled("known-issue: actionChain 为空导致 IndexOutOfBounds 与资源未清理")
    @Tag("known-issue")
    @Test
    void execute_emptyActionChain_shouldFail() {
        ExecutorService directExecutor = new DirectExecutorService();
        stubExecutors(directExecutor, directExecutor);

        ImmediateActionData actionData = buildActionData(new HashMap<>());
        ActionExecutorInput input = buildInput("u1", actionData);

        actionExecutor.init();
        actionExecutor.execute(input);

        verify(actionCapability, never()).removePhaserRecord(any(Phaser.class));
    }

    private void stubExecutors(ExecutorService platformExecutor, ExecutorService virtualExecutor) {
        when(actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM)).thenReturn(platformExecutor);
        when(actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL)).thenReturn(virtualExecutor);
        when(actionCapability.runnerClient()).thenReturn(runnerClient);
    }

    private ActionExecutorInput buildInput(String userId, ImmediateActionData actionData) {
        ActionExecutorInput input = new ActionExecutorInput();
        input.setUserId(userId);
        input.setImmediateActions(List.of(actionData));
        return input;
    }

    private ImmediateActionData buildActionData(Map<Integer, List<MetaAction>> actionChain) {
        ImmediateActionData actionData = new ImmediateActionData();
        actionData.setStatus(ActionData.ActionStatus.PREPARE);
        actionData.setActionChain(actionChain);
        actionData.setAdditionalContext(initAdditionalContext(actionChain));
        actionData.setReason("reason");
        actionData.setDescription("desc");
        actionData.setSource("source");
        actionData.setTendency("tendency");
        return actionData;
    }

    private Map<Integer, List<MetaAction>> singleStageChain(boolean io) {
        Map<Integer, List<MetaAction>> chain = new HashMap<>();
        chain.put(0, List.of(buildMetaAction("a1", io)));
        return chain;
    }

    private MetaAction buildMetaAction(String name, boolean io) {
        MetaAction metaAction = new MetaAction();
        metaAction.setName(name);
        metaAction.setLocation("loc");
        metaAction.setIo(io);
        return metaAction;
    }

    private Map<Integer, List<String>> initAdditionalContext(Map<Integer, List<MetaAction>> actionChain) {
        Map<Integer, List<String>> context = new HashMap<>();
        for (Integer stage : actionChain.keySet()) {
            context.put(stage, new ArrayList<>());
        }
        return context;
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdownNow();
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
