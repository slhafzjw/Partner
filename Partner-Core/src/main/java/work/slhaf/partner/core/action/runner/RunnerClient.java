package work.slhaf.partner.core.action.runner;

import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.action.entity.ActionFileMetaData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaAction.Result;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.ActionInfrastructureStartupException;
import work.slhaf.partner.module.action.builtin.BuiltinActionRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
public abstract class RunnerClient implements AutoCloseable {

    protected final String ACTION_PATH;

    protected final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions;
    protected final ExecutorService executor;
    @Setter
    protected BuiltinActionRegistry builtinActionRegistry;

    /**
     * ActionCore 将注入虚拟线程池
     */
    public RunnerClient(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, ExecutorService executor, @NotNull String baseActionPath) {
        this.existedMetaActions = existedMetaActions;
        this.executor = executor;
        this.ACTION_PATH = baseActionPath;

        createPath(ACTION_PATH);
    }

    /**
     * 执行行动程序
     */
    public void submit(MetaAction metaAction) {
        log.debug("执行行动: {}", metaAction);
        // 获取已存在行动列表
        Result result = metaAction.getResult();
        if (!result.getStatus().equals(Result.Status.WAITING)) {
            return;
        }
        RunnerResponse response = work.slhaf.partner.framework.agent.support.Result.runCatching(() -> doRun(metaAction)).fold(
                runnerResponse -> runnerResponse,
                ex -> {
                    RunnerResponse r = new RunnerResponse();
                    r.setOk(false);
                    r.setData(ex.getLocalizedMessage());
                    return r;
                }
        );
        result.setData(response.getData());
        result.setStatus(response.isOk() ? Result.Status.SUCCESS : Result.Status.FAILED);
        log.debug("行动执行结果: {}", response);
    }

    protected abstract RunnerResponse doRun(MetaAction metaAction);

    public abstract String buildTmpPath(String actionKey, String codeType);

    public abstract void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException;

    public abstract void persistSerialize(MetaActionInfo metaActionInfo, ActionFileMetaData fileMetaData);

    protected RunnerResponse doRunWithBuiltin(MetaAction metaAction) {
        RunnerResponse response = new RunnerResponse();
        if (builtinActionRegistry == null) {
            response.setOk(false);
            response.setData("BuiltinActionRegistry 未初始化");
            return response;
        }
        response.setData(builtinActionRegistry.call(metaAction.getKey(), metaAction.getParams()));
        response.setOk(true);
        return response;
    }

    protected void createPath(String pathStr) {
        val path = Path.of(pathStr);
        try {
            Files.createDirectory(path);
        } catch (IOException e) {
            if (!Files.exists(path)) {
                throw new ActionInfrastructureStartupException(
                        "Failed to create action directory: " + pathStr,
                        "runner-client",
                        pathStr,
                        null,
                        e
                );
            }
        }
    }

    @Data
    public static class RunnerResponse {
        private boolean ok;
        private String data;
    }

}
