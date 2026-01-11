package work.slhaf.partner.core.action.runner;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionType;

import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class LocalRunnerClientTest {

    static LocalRunnerClient runnerClient;

    @BeforeAll
    static void beforeAll() {
        runnerClient = new LocalRunnerClient(new ConcurrentHashMap<>(), Executors.newVirtualThreadPerTaskExecutor(), "/home/slhaf/Projects/IdeaProjects/Projects/Partner/Partner-Main/src/test/java/resources/action");
    }

    @Test
    void testRunOrigin() {
        MetaAction metaAction = buildTmpMetaAction();

        RunnerClient.RunnerResponse runnerResponse = runnerClient.doRun(metaAction);
        System.out.println(runnerResponse.getData());
    }

    private static @NotNull MetaAction buildTmpMetaAction() {
        MetaAction metaAction = new MetaAction();
        metaAction.setIo(false);
        metaAction.setName("hello_world");
        metaAction.setParams(Map.of("name", "origin_run"));
        metaAction.setType(MetaActionType.ORIGIN);
        metaAction.setLocation("/home/slhaf/Projects/IdeaProjects/Projects/Partner/Partner-Main/src/test/java/resources/action/tmp/hello_world.py");
        return metaAction;
    }

    @Test
    void testWatch() {
        // 直接等待输入然后尝试触发各种文件监听事件即可
        Scanner scanner = new Scanner(System.in);
        scanner.next();
    }
}
