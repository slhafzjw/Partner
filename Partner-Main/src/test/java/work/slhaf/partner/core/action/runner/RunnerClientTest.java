package work.slhaf.partner.core.action.runner;

import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.core.action.entity.McpData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class RunnerClientTest {

    private static class TestRunnerClient extends RunnerClient {

        public TestRunnerClient() {
            super(new ConcurrentHashMap<>(), Executors.newVirtualThreadPerTaskExecutor());
        }

        @Override
        protected RunnerResponse doRun(MetaAction metaAction) {
            return null;
        }

        @Override
        public String buildTmpPath(MetaAction tempAction, String codeType) {
            return null;
        }

        @Override
        public void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException {

        }

        @Override
        public void persistSerialize(MetaActionInfo metaActionInfo, McpData mcpData) {

        }

        @Override
        public JSONObject listSysDependencies() {
            return null;
        }
    }
}
