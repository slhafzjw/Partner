package work.slhaf.partner.core.action.runner;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.core.action.entity.McpData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaAction.Result;
import work.slhaf.partner.core.action.entity.MetaAction.ResultStatus;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
public abstract class RunnerClient {

    protected final Map<String, MetaActionInfo> existedMetaActions;
    protected final ExecutorService executor;

    /**
     * ActionCore 将注入虚拟线程池
     */
    public RunnerClient(Map<String, MetaActionInfo> existedMetaActions, ExecutorService executor) {
        this.existedMetaActions = existedMetaActions;
        this.executor = executor;
    }

    /**
     * 执行行动程序
     */
    public void run(MetaAction metaAction) {
        // 获取已存在行动列表
        Result result = metaAction.getResult();
        if (!result.getStatus().equals(ResultStatus.WAITING)) {
            return;
        }
        RunnerResponse response = doRun(metaAction);
        result.setData(response.getData());
        result.setStatus(response.isOk() ? ResultStatus.SUCCESS : ResultStatus.FAILED);
    }

    protected abstract RunnerResponse doRun(MetaAction metaAction);

    public abstract Path buildTmpPath(MetaAction tempAction, String codeType);

    public abstract void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException;

    public abstract void persistSerialize(MetaActionInfo metaActionInfo, McpData mcpData);

    /**
     * 列出执行环境下的系统依赖情况
     */
    public abstract JSONObject listSysDependencies();

    @Data
    protected static class RunnerResponse {
        private boolean ok;
        private String data;
    }
}
