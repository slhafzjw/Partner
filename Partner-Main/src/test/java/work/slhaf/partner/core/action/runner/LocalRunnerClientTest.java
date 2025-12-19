package work.slhaf.partner.core.action.runner;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionType;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

public class LocalRunnerClientTest {

    static LocalRunnerClient runnerClient;

    @BeforeAll
    static void beforeAll() {
        runnerClient = new LocalRunnerClient(Map.of(), Executors.newVirtualThreadPerTaskExecutor(), "/home/slhaf/Projects/IdeaProjects/Projects/Partner/Partner-Main/src/test/java/resources/action/data");
    }

    @Test
    void runOrigin() {
        MetaAction metaAction = buildTmpMetaAction();

        RunnerClient.RunnerResponse runnerResponse = runnerClient.doRun(metaAction);
        System.out.println(runnerResponse.getData());
    }

    private static @NotNull MetaAction buildTmpMetaAction() {
        MetaAction metaAction = new MetaAction();
        metaAction.setIo(false);
        metaAction.setKey("hello_world");
        metaAction.setParams(Map.of("name", "origin_run"));
        metaAction.setType(MetaActionType.ORIGIN);
        metaAction.setPath(Path.of("/home/slhaf/Projects/IdeaProjects/Projects/Partner/Partner-Main/src/test/java/resources/action/tmp/hello_world.py"));
        return metaAction;
    }
}
