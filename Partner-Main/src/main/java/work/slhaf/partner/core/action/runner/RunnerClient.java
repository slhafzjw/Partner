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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 执行客户端抽象类
 * <br/>
 * 只负责暴露序列化、执行等相应接口，具体逻辑交给下游实现
 * <br/>
 * 默认存在两类实现，{@link LocalRunnerClient} 和 {@link SandboxRunnerClient}
 * <ol>
 *     LocalRunnerClient:
 *     <li>
 *         对应本地运行环境，可在本地启动 MCP 客户端将 RunnerClient 暴露的能力接口转发至本地 MCP Client 并执行
 *     </li>
 *     SandboxRunnerClient:
 *     <li>
 *         对应沙盒运行环境，该 Client 仅作为沙盒环境的客户端，不持有额外能力，仅保持远端连接已存在行动的内容更新
 *     </li>
 * </ol>
 */
@Slf4j
public abstract class RunnerClient {

    protected final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions;
    protected final ExecutorService executor;

    /**
     * ActionCore 将注入虚拟线程池
     */
    public RunnerClient(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, ExecutorService executor) {
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

    public abstract String buildTmpPath(MetaAction tempAction, String codeType);

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
