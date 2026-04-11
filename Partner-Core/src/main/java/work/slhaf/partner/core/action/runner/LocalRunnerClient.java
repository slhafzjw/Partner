package work.slhaf.partner.core.action.runner;

import cn.hutool.system.OsInfo;
import cn.hutool.system.SystemUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.core.action.entity.ActionFileMetaData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.ActionInfrastructureStartupException;
import work.slhaf.partner.core.action.runner.execution.McpActionExecutor;
import work.slhaf.partner.core.action.runner.execution.OriginExecutionService;
import work.slhaf.partner.core.action.runner.mcp.*;
import work.slhaf.partner.core.action.runner.policy.BwrapPolicyProvider;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicyRegistry;
import work.slhaf.partner.core.action.runner.support.ActionSerializer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class LocalRunnerClient extends RunnerClient implements AutoCloseable {

    public static final String MCP_NAME_DESC = "mcp-desc";
    public static final String MCP_NAME_DYNAMIC = "mcp-dynamic";

    private final String tmpActionPath;
    private final String dynamicActionPath;
    private final String mcpServerPath;
    private final String mcpDescPath;

    private final McpClientRegistry mcpClientRegistry;
    private final McpTransportFactory mcpTransportFactory;
    private final ActionSerializer actionSerializer;
    private final OriginExecutionService originExecutionService;
    private final McpActionExecutor mcpActionExecutor;
    private final McpMetaRegistry mcpMetaRegistry;
    private final McpDescWatcher mcpDescWatcher;
    private final DynamicActionMcpManager dynamicActionMcpManager;
    private final McpConfigWatcher mcpConfigWatcher;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public LocalRunnerClient(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, ExecutorService executor, @Nullable String baseActionPath) {
        super(existedMetaActions, executor, baseActionPath);
        this.tmpActionPath = buildPathStr(ACTION_PATH, "tmp");
        this.dynamicActionPath = buildPathStr(ACTION_PATH, "dynamic");
        this.mcpServerPath = buildPathStr(ACTION_PATH, "mcp");
        this.mcpDescPath = buildPathStr(mcpServerPath, "desc");

        createPath(tmpActionPath);
        createPath(dynamicActionPath);
        createPath(mcpServerPath);
        createPath(mcpDescPath);

        McpClientRegistry clientRegistry = new McpClientRegistry();
        McpTransportFactory transportFactory = new McpTransportFactory();
        ActionSerializer serializer = new ActionSerializer(tmpActionPath, dynamicActionPath);
        OriginExecutionService originService = new OriginExecutionService();
        McpActionExecutor actionExecutor = new McpActionExecutor(clientRegistry);

        McpMetaRegistry metaRegistry = null;
        McpDescWatcher descWatcher = null;
        DynamicActionMcpManager dynamicManager = null;
        McpConfigWatcher configWatcher = null;

        try {

            registerPolicyProviders();

            metaRegistry = new McpMetaRegistry(existedMetaActions);
            registerMcpClient(clientRegistry, transportFactory, MCP_NAME_DESC, metaRegistry.clientConfig(MCP_NAME_DESC, 10));
            log.info("DescMcp 注册完毕");

            descWatcher = new McpDescWatcher(Path.of(mcpDescPath), metaRegistry, executor);
            descWatcher.start();

            dynamicManager = new DynamicActionMcpManager(
                    Path.of(dynamicActionPath),
                    existedMetaActions,
                    executor
            );
            registerMcpClient(clientRegistry, transportFactory, MCP_NAME_DYNAMIC, dynamicManager.clientConfig(10));
            log.info("DynamicActionMcp 注册完毕");
            dynamicManager.start();

            configWatcher = new McpConfigWatcher(
                    Path.of(mcpServerPath),
                    existedMetaActions,
                    clientRegistry,
                    transportFactory,
                    metaRegistry,
                    executor
            );
            configWatcher.start();
            configWatcher.registerPolicyListener();
        } catch (ActionInfrastructureStartupException e) {
            closeQuietly(configWatcher);
            closeQuietly(dynamicManager);
            closeQuietly(descWatcher);
            closeQuietly(metaRegistry);
            closeQuietly(clientRegistry);
            throw e;
        } catch (Exception e) {
            closeQuietly(configWatcher);
            closeQuietly(dynamicManager);
            closeQuietly(descWatcher);
            closeQuietly(metaRegistry);
            closeQuietly(clientRegistry);
            throw new ActionInfrastructureStartupException(
                    "LocalRunnerClient initialization failed",
                    "local-runner-client",
                    ACTION_PATH,
                    null,
                    e
            );
        }

        this.mcpClientRegistry = clientRegistry;
        this.mcpTransportFactory = transportFactory;
        this.actionSerializer = serializer;
        this.originExecutionService = originService;
        this.mcpActionExecutor = actionExecutor;
        this.mcpMetaRegistry = metaRegistry;
        this.mcpDescWatcher = descWatcher;
        this.dynamicActionMcpManager = dynamicManager;
        this.mcpConfigWatcher = configWatcher;
    }

    private void registerPolicyProviders() {
        OsInfo os = SystemUtil.getOsInfo();
        if (os.isLinux()) {
            ExecutionPolicyRegistry.INSTANCE.registerPolicyProvider(BwrapPolicyProvider.INSTANCE);
        }
    }

    @Override
    protected RunnerResponse doRun(MetaAction metaAction) {
        log.debug("执行行动: {}", metaAction);
        RunnerResponse response;
        try {
            response = switch (metaAction.getType()) {
                case MCP -> mcpActionExecutor.run(metaAction);
                case ORIGIN -> originExecutionService.run(metaAction);
                case BUILTIN -> doRunWithBuiltin(metaAction);
            };
        } catch (Exception e) {
            response = new RunnerResponse();
            response.setOk(false);
            response.setData(e.getLocalizedMessage());
        }
        log.debug("行动执行结果: {}", response);
        return response;
    }

    @Override
    public String buildTmpPath(String actionKey, String codeType) {
        return actionSerializer.buildTmpPath(actionKey, codeType);
    }

    @Override
    public void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException {
        actionSerializer.tmpSerialize(tempAction, code, codeType);
    }

    @Override
    public void persistSerialize(MetaActionInfo metaActionInfo, ActionFileMetaData fileMetaData) {
        actionSerializer.persistSerialize(metaActionInfo, fileMetaData);
    }

    private void registerMcpClient(McpClientRegistry clientRegistry, McpTransportFactory transportFactory, String id, McpTransportConfig transportConfig) {
        val client = io.modelcontextprotocol.client.McpClient.sync(transportFactory.create(transportConfig))
                .requestTimeout(java.time.Duration.ofSeconds(transportConfig.timeout()))
                .clientInfo(new io.modelcontextprotocol.spec.McpSchema.Implementation(id, "PARTNER"))
                .build();
        clientRegistry.register(id, client);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(mcpConfigWatcher);
        closeQuietly(dynamicActionMcpManager);
        closeQuietly(mcpDescWatcher);
        closeQuietly(mcpMetaRegistry);
        closeQuietly(mcpClientRegistry);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    public String buildPathStr(String... path) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < path.length; i++) {
            str.append(path[i]);
            if (i < path.length - 1) {
                str.append("/");
            }
        }
        return str.toString();
    }
}
