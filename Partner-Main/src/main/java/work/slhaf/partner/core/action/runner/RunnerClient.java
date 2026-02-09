package work.slhaf.partner.core.action.runner;

import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.core.action.entity.ActionFileMetaData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaAction.Result;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.ActionInitFailedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static work.slhaf.partner.common.Constant.Path.DATA;
import static work.slhaf.partner.common.util.PathUtil.buildPathStr;

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

    protected final String ACTION_PATH;

    protected final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions;
    protected final ExecutorService executor;
    //TODO 仍可提供内部 MCP，但调用方式需要结合 AgentContext来获取，否则生命周期不合
    protected McpStatelessAsyncServer innerMcpServer;

    /**
     * ActionCore 将注入虚拟线程池
     */
    public RunnerClient(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, ExecutorService executor, @Nullable String baseActionPath) {
        this.existedMetaActions = existedMetaActions;
        this.executor = executor;
        baseActionPath = baseActionPath == null ? DATA : baseActionPath;
        this.ACTION_PATH = buildPathStr(baseActionPath, "action");

        createPath(ACTION_PATH);
    }

    /**
     * 执行行动程序
     */
    public void submit(MetaAction metaAction) {
        // 获取已存在行动列表
        Result result = metaAction.getResult();
        if (!result.getStatus().equals(Result.Status.WAITING)) {
            return;
        }
        RunnerResponse response = doRun(metaAction);
        result.setData(response.getData());
        result.setStatus(response.isOk() ? Result.Status.SUCCESS : Result.Status.FAILED);
    }

    protected abstract RunnerResponse doRun(MetaAction metaAction);

    public abstract String buildTmpPath(String actionKey, String codeType);

    public abstract void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException;

    public abstract void persistSerialize(MetaActionInfo metaActionInfo, ActionFileMetaData fileMetaData);

    protected void createPath(String pathStr) {
        val path = Path.of(pathStr);
        try {
            Files.createDirectory(path);
        } catch (IOException e) {
            if (!Files.exists(path)) {
                throw new ActionInitFailedException("目录创建失败: " + pathStr, e);
            }
        }
    }

    /**
     * 列出执行环境下的系统依赖情况
     */
    public abstract JSONObject listSysDependencies();

    @Data
    public static class RunnerResponse {
        private boolean ok;
        private String data;
    }

}
