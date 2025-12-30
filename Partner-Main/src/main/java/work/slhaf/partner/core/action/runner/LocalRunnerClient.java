package work.slhaf.partner.core.action.runner;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import work.slhaf.partner.common.mcp.InProcessMcpTransport;
import work.slhaf.partner.core.action.entity.McpData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.MetaActionType;
import work.slhaf.partner.core.action.exception.ActionInitFailedException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static work.slhaf.partner.common.util.PathUtil.buildPathStr;

@Slf4j
public class LocalRunnerClient extends RunnerClient {

    private final String TMP_ACTION_PATH = buildPathStr(ACTION_PATH, "tmp");
    private final String DYNAMIC_ACTION_PATH = buildPathStr(ACTION_PATH, "dynamic");
    private final String MCP_SERVER_PATH = buildPathStr(ACTION_PATH, "mcp");
    private final String MCP_DESC_PATH = buildPathStr(MCP_SERVER_PATH, "desc");

    private final Map<String, McpSyncClient> mcpClients = new HashMap<>();
    /**
     * 动态生成的行动程序都将挂载至该 McpServer
     */
    private McpStatelessAsyncServer dynamicActionMcpServer;
    /**
     * 负责监听常规 MCP Server 的描述文件（描述文件主要用于添加原本 MCP Tools 不携带的信息，如前置依赖、后置依赖、是否 IO 密集等
     */
    private McpStatelessAsyncServer mcpDescServer;
    private final WatchService watchService;

    public LocalRunnerClient(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, ExecutorService executor, @Nullable String baseActionPath) {
        super(existedMetaActions, executor, baseActionPath);
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new ActionInitFailedException("目录监听器启动失败", e);
        }
        registerDescMcp();
        registerDynamicActionMcp();
        setupShutdownHook();
    }

    private void registerDescMcp() {
        InProcessMcpTransport.Pair pair = InProcessMcpTransport.pair();
        McpSchema.ServerCapabilities serverCapabilities = McpSchema.ServerCapabilities.builder()
                .resources(true, true)
                .build();
        mcpDescServer = McpServer.async(pair.serverSide())
                .capabilities(serverCapabilities)
                .jsonMapper(McpJsonMapper.getDefault())
                .build();

        // TODO 完善加载与监听逻辑
        registerMcpClient("mcp-desc", pair.clientSide(), 10);
    }

    private void registerDynamicActionMcp() {
        InProcessMcpTransport.Pair pair = InProcessMcpTransport.pair();
        McpSchema.ServerCapabilities serverCapabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .resources(true, true)
                .build();
        dynamicActionMcpServer = McpServer.async(pair.serverSide())
                .capabilities(serverCapabilities)
                .jsonMapper(McpJsonMapper.getDefault())
                .build();
        registerMcpClient("dynamic-action", pair.clientSide(), 10);
    }

    @Override
    protected RunnerResponse doRun(MetaAction metaAction) {
        RunnerResponse response;
        try {
            response = switch (metaAction.getType()) {
                case MetaActionType.MCP -> doRunWithMcp(metaAction);
                case MetaActionType.ORIGIN -> doRunWithOrigin(metaAction);
            };
        } catch (Exception e) {
            response = new RunnerResponse();
            response.setOk(false);
            response.setData(e.getLocalizedMessage());
        }
        return response;
    }

    private RunnerResponse doRunWithOrigin(MetaAction metaAction) {
        RunnerResponse response = new RunnerResponse();
        File file = new File(metaAction.getLocation());
        String ext = FileUtil.getSuffix(file);
        if (ext == null || ext.isEmpty()) {
            response.setOk(false);
            response.setData("未知文件类型");
            return response;
        }
        String[] commands = SystemExecHelper.buildCommands(ext, metaAction.getParams(), file.getAbsolutePath());
        SystemExecHelper.Result execResult = SystemExecHelper.exec(commands);
        response.setOk(execResult.isOk());
        response.setData(execResult.getTotal());
        return response;
    }

    private RunnerResponse doRunWithMcp(MetaAction metaAction) {
        RunnerResponse response = new RunnerResponse();
        McpSyncClient mcpClient = mcpClients.get(metaAction.getLocation());
        McpSchema.CallToolRequest callToolRequest = McpSchema.CallToolRequest.builder()
                .name(metaAction.getName())
                .arguments(metaAction.getParams())
                .build();
        McpSchema.CallToolResult callToolResult = mcpClient.callTool(callToolRequest);
        response.setOk(callToolResult.isError());
        response.setData(callToolResult.structuredContent().toString());
        return response;
    }

    @Override
    public String buildTmpPath(MetaAction tempAction, String codeType) {
        return Path.of(TMP_ACTION_PATH, System.currentTimeMillis() + "-" + tempAction.getKey() + codeType).toString();
    }

    @Override
    public void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException {
        Path path = Path.of(tempAction.getLocation());
        File file = path.toFile();
        file.createNewFile();
        Files.writeString(path, code);
    }

    @Override
    public void persistSerialize(MetaActionInfo metaActionInfo, McpData mcpData) {
        throw new UnsupportedOperationException("Unimplemented method 'doPersistSerialize'");
    }

    @Override
    public JSONObject listSysDependencies() {
        // 先只列出系统/环境的 Python 依赖
        // TODO 在 AgentConfigManager 内配置启用的脚本语言及对应的扩展名
        // 这里的逻辑后续需要替换为“根据 AgentConfigManager 读取到的脚本语言启用情况，遍历并列出当前系统环境依赖”
        // 还需要将返回值调整为相应的数据类
        // 后续还需要将不同语言的处理逻辑分散到不同方法内，这里为了验证，先写死在当前方法
        JSONObject sysDependencies = new JSONObject();
        sysDependencies.put("language", "Python");
        JSONArray dependencies = sysDependencies.putArray("dependencies");
        SystemExecHelper.Result pyResult = SystemExecHelper.exec("pip", "list", "--format=freeze");
        System.out.println(pyResult);
        if (pyResult.isOk()) {
            List<String> resultList = pyResult.getResultList();
            for (String result : resultList) {
                JSONObject element = dependencies.addObject();
                String[] split = result.split("==");
                element.put("name", split[0]);
                element.put("version", split[1]);
            }
        } else {
            JSONObject element = dependencies.addObject();
            element.put("error", pyResult.getTotal());
        }
        return sysDependencies;
    }

    /**
     * 该部分主要发生在扫描到新的MCP Server描述文件时出现的注册逻辑
     *
     * @param id                       MCP Client 的 id
     * @param mcpClientTransportParams MCP Server 的参数
     */
    private void registerMcpClient(String id, McpClientTransportParams mcpClientTransportParams) {
        McpClientTransport clientTransport = createTransport(mcpClientTransportParams);
        int timeout = mcpClientTransportParams.timeout;
        registerMcpClient(id, clientTransport, timeout);
    }

    private void registerMcpClient(String id, McpClientTransport clientTransport, int timeout) {
        McpSyncClient client = McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofSeconds(timeout))
                .clientInfo(new McpSchema.Implementation(id, "PARTNER"))
                // 行动程序(现 MCP Tool)的描述文本将直接由resources返回
                // 原因: ToolChange 发送的内容侧重调用，缺少可承担描述文本的字段
                //       ResourcesChange 事件传递的 Resource 可以由 Client 读取内容
                //       预计在 Server 侧，收到客户端发送的新的行动程序信息，该信息由客户端处补充后，将其放置在指定位置
                //       并写入描述文件、发起 ResourcesChange 事件
                .toolsChangeConsumer(tools -> updateExistedMetaActions(id, tools))
                .build();
        mcpClients.put(id, client);
    }

    private void updateExistedMetaActions(String id, @UnknownNullability List<McpSchema.Tool> tools) {
        for (McpSchema.Tool tool : tools) {
            MetaActionInfo info = buildMetaActionInfo(tool);
            String actionKey = id + "::" + tool.name();
            existedMetaActions.put(actionKey, info);
        }
    }

    private @NotNull MetaActionInfo buildMetaActionInfo(McpSchema.Tool tool) {
        MetaActionInfo info = new MetaActionInfo();
        info.setDescription(tool.description());
        Map<String, Object> outputSchema = tool.outputSchema();
        info.setResponseSchema(outputSchema == null ? JSONObject.of() : JSONObject.from(outputSchema));
        info.setParams(tool.inputSchema().properties());

        JSONObject meta = JSONObject.from(tool.meta());
        info.setIo(meta.getBoolean("io"));
        info.setPreActions(meta.getList("pre", String.class));
        info.setPostActions(meta.getList("post", String.class));
        info.setStrictDependencies(meta.getBoolean("strict"));
        info.setTags(meta.getList("tag", String.class));
        return info;
    }

    private McpClientTransport createTransport(McpClientTransportParams mcpClientTransportParams) {
        return switch (mcpClientTransportParams) {
            case McpClientTransportParams.Stdio params -> {
                ServerParameters serverParameters = ServerParameters.builder(params.command)
                        .env(params.env)
                        .args(params.args)
                        .build();
                yield new StdioClientTransport(serverParameters, McpJsonMapper.getDefault());
            }
            case McpClientTransportParams.Http params -> {
                McpSyncHttpClientRequestCustomizer customizer = (builder, method, endpoint, body, context) -> {
                    params.headers.forEach(builder::setHeader);
                };
                yield HttpClientSseClientTransport.builder(params.baseUri)
                        .httpRequestCustomizer(customizer)
                        .sseEndpoint(params.endpoint)
                        .build();
            }
        };
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            dynamicActionMcpServer.close();
            mcpDescServer.close();
            this.mcpClients.forEach((id, client) -> {
                client.close();
                log.info("[{}] MCP-Client 已关闭", id);
            });
        }));
    }

    private LocalWatchServiceBuild registerWatchService(Path path) {
        return new LocalWatchServiceBuild.BuildRegistry(path, watchService, executor);
    }

    private interface LocalWatchServiceBuild {
        LocalWatchServiceBuild registerCreate(EventHandler handler);

        LocalWatchServiceBuild registerModify(EventHandler handler);

        LocalWatchServiceBuild registerDelete(EventHandler handler);

        LocalWatchServiceBuild registerOverflow(EventHandler handler);

        LocalWatchServiceBuild initialLoad(InitLoader loader);

        void commit();

        interface EventHandler {
            void handle(Path thisDir, Path context);
        }

        interface InitLoader {
            void load(Path path);
        }

        class BuildRegistry implements LocalWatchServiceBuild {

            private final Map<WatchEvent.Kind<?>, EventHandler> handlers = new HashMap<>();
            private final Path path;
            private final WatchService watchService;
            private final ExecutorService executor;
            private InitLoader initLoader;

            private BuildRegistry(Path path, WatchService watchService, ExecutorService executor) {
                this.path = path;
                this.watchService = watchService;
                this.executor = executor;
            }

            @Override
            public LocalWatchServiceBuild registerCreate(EventHandler handler) {
                handlers.put(StandardWatchEventKinds.ENTRY_CREATE, handler);
                return this;
            }

            @Override
            public LocalWatchServiceBuild registerModify(EventHandler handler) {
                handlers.put(StandardWatchEventKinds.ENTRY_MODIFY, handler);
                return this;
            }

            @Override
            public LocalWatchServiceBuild registerDelete(EventHandler handler) {
                handlers.put(StandardWatchEventKinds.ENTRY_DELETE, handler);
                return this;
            }

            @Override
            public LocalWatchServiceBuild registerOverflow(EventHandler handler) {
                handlers.put(StandardWatchEventKinds.OVERFLOW, handler);
                return this;
            }

            @Override
            public LocalWatchServiceBuild initialLoad(InitLoader loader) {
                initLoader = loader;
                return this;
            }

            @Override
            public void commit() {
                if (initLoader != null) initLoader.load(path);
                executor.execute(buildWatchTask());
            }

            private Runnable buildWatchTask() {
                return () -> {
                    String pathStr = path.toString();
                    log.info("行动程序目录监听器已启动，监听目录: {}", pathStr);
                    while (true) {
                        WatchKey key;
                        try {
                            key = watchService.take();
                            List<WatchEvent<?>> events = key.pollEvents();
                            for (WatchEvent<?> e : events) {
                                WatchEvent.Kind<?> kind = e.kind();
                                Object context = e.context();
                                log.info("行动程序目录变更事件: {} - {} - {}", pathStr, kind.name(), context);
                                Path thisDir = (Path) key.watchable();
                                if (!thisDir.equals(path)) {
                                    // 若事件所在目录不为为 path，忽略并步入下一轮循环
                                    continue;
                                }
                                EventHandler handler = handlers.get(kind);
                                if (handler == null) {
                                    continue;
                                }
                                handler.handle(thisDir, context instanceof Path ? (Path) context : null);
                            }
                        } catch (InterruptedException e) {
                            log.info("监听线程被中断，准备退出...");
                            Thread.currentThread().interrupt(); // 恢复中断标志
                            break;
                        } catch (ClosedWatchServiceException e) {
                            log.info("WatchService 已关闭，监听线程退出。");
                            break;
                        }
                    }
                };
            }

        }
    }

    private sealed static abstract class LocalWatchServiceHelper permits LocalWatchServiceHelper.Dynamic, LocalWatchServiceHelper.Desc, LocalWatchServiceHelper.Common {

        protected final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions;

        private LocalWatchServiceHelper(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions) {
            this.existedMetaActions = existedMetaActions;
        }

        protected abstract @NotNull LocalWatchServiceBuild.InitLoader buildLoad();

        protected abstract @NotNull LocalWatchServiceBuild.EventHandler buildModify();

        protected abstract @NotNull LocalWatchServiceBuild.EventHandler buildCreate();

        protected abstract @NotNull LocalWatchServiceBuild.EventHandler buildDelete();

        protected abstract @NotNull LocalWatchServiceBuild.EventHandler buildOverflow();

        private static final class Dynamic extends LocalWatchServiceHelper {

            private final McpStatelessAsyncServer dynamicActionMcpServer;

            private Dynamic(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, McpStatelessAsyncServer dynamicActionMcpServer) {
                super(existedMetaActions);
                this.dynamicActionMcpServer = dynamicActionMcpServer;
            }

            @SuppressWarnings("BooleanMethodIsAlwaysInverted")
            private boolean normalPath(Path path) {
                File file = path.toFile();
                if (file.isFile()) {
                    return false;
                }
                File[] files = file.listFiles();
                if (files == null) {
                    return false;
                }
                if (files.length < 2) {
                    return false;
                }
                boolean desc = false;
                int run = 0;
                for (File f : files) {
                    String fileName = f.getName();
                    if (fileName.equals("desc.json")) {
                        desc = true;
                    }
                    if (fileName.startsWith("run.")) {
                        run++;
                    }
                }
                return run == 1 && desc;
            }

            private BiFunction<McpTransportContext, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>> buildToolHandler(File finalProgram) {
                return (mcpTransportContext, callToolRequest) -> {
                    Map<String, Object> arguments = callToolRequest.arguments();
                    if (arguments == null) {
                        arguments = Map.of();
                    }
                    String ext = FileUtil.getSuffix(finalProgram);
                    String[] commands = SystemExecHelper.buildCommands(ext, arguments, finalProgram.getAbsolutePath());
                    if (commands == null) {
                        return Mono.just(McpSchema.CallToolResult.builder()
                                .addTextContent("未知文件类型: " + finalProgram.getName())
                                .isError(true)
                                .build());
                    }

                    return Mono.fromCallable(() -> {
                        SystemExecHelper.Result execResult = SystemExecHelper.exec(commands);
                        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                                .isError(!execResult.isOk());
                        List<String> resultList = execResult.getResultList();
                        if (resultList != null && !resultList.isEmpty()) {
                            builder.textContent(resultList);
                            builder.structuredContent(resultList);
                        } else {
                            builder.addTextContent(execResult.getTotal());
                            builder.structuredContent(execResult.getTotal());
                        }
                        return builder.build();
                    }).subscribeOn(Schedulers.boundedElastic());
                };
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.InitLoader buildLoad() {
                return path -> {
                    // 从该路径列出已存在的目录，每个目录对应不同的行动程序及描述文件，从描述文件加载程序信息
                    File file = path.toFile();
                    if (file.isFile()) {
                        throw new ActionInitFailedException("未找到目录: " + path);
                    }
                    File[] files = file.listFiles();
                    if (files == null) {
                        throw new ActionInitFailedException("未正常读取目录: " + path);
                    }
                    for (File dir : files) {
                        if (!normalPath(dir.toPath())) {
                            continue;
                        }
                        File meta = new File(dir, "desc.json");
                        File program = null;
                        //noinspection DataFlowIssue
                        for (File f : dir.listFiles()) {
                            if (f.getName().startsWith("run.")) {
                                program = f;
                            }
                        }

                        MetaActionInfo info = JSONUtil.readJSONObject(meta, StandardCharsets.UTF_8).toBean(MetaActionInfo.class);
                        String actionKey = "local::" + dir.getName();
                        existedMetaActions.put(actionKey, info);

                        McpStatelessServerFeatures.AsyncToolSpecification specification = buildAsyncToolSpecification(info, program, actionKey, dir.getName());
                        dynamicActionMcpServer.addTool(specification).subscribe();
                    }
                };
            }

            private McpStatelessServerFeatures.AsyncToolSpecification buildAsyncToolSpecification(MetaActionInfo info, File program, String actionKey, String name) {
                Map<String, Object> additional = Map.of("pre", info.getPreActions(),
                        "post", info.getPostActions(),
                        "strict_pre", info.isStrictDependencies(),
                        "io", info.isIo());
                McpSchema.Tool tool = McpSchema.Tool.builder()
                        .name(name)
                        .description(info.getDescription())
                        .inputSchema(McpJsonMapper.getDefault(), JSONObject.toJSONString(info.getParams()))
                        .outputSchema(info.getResponseSchema())
                        .title(actionKey)
                        .meta(additional)
                        .build();
                return McpStatelessServerFeatures.AsyncToolSpecification.builder()
                        .tool(tool)
                        .callHandler(buildToolHandler(program))
                        .build();
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildModify() {
                return (thisDir, context) -> {
                    // 查看当前目录是否为空或者能否正常读取
                    if (!normalPath(thisDir)) {
                        return;
                    }
                    // 对应本地程序或者描述文件的修改行为
                    String fileName = context.getFileName().toString();
                    if (fileName.equals("desc.json")) {
                        handleMetaModify(thisDir, context);
                    }
                    if (fileName.startsWith("run.")) {
                        handleProgramModify(thisDir, context);
                    }
                };
            }

            private void handleProgramModify(Path thisDir, Path context) {
                String name = thisDir.getFileName().toString();
                String actionKey = "local::" + name;
                // 检查是否存在当前 program 对应的 Tool
                if (existedMetaActions.containsKey(actionKey)) {
                    return;
                }
                // 检查描述文件是否可读取，如果可以正常读取，则新增 Tool
                File meta = Path.of(thisDir.toString(), "desc.json").toFile();
                try {
                    MetaActionInfo info = JSONUtil.readJSONObject(meta, StandardCharsets.UTF_8).toBean(MetaActionInfo.class);
                    dynamicActionMcpServer.addTool(buildAsyncToolSpecification(info, context.toFile(), actionKey, name)).subscribe();
                    existedMetaActions.put(actionKey, info);
                } catch (IORuntimeException e) {
                    log.warn("读取 desc.json 失败，请检查字段", e);
                }
            }

            private void handleMetaModify(Path thisDir, Path context) {
                // 检查是否除了描述文件外还存在别的可执行文件
                File meta = Path.of(thisDir.toString(), context.toString()).toFile();
                try {
                    MetaActionInfo info = JSONUtil.readJSONObject(meta, StandardCharsets.UTF_8).toBean(MetaActionInfo.class);
                    existedMetaActions.put("local::" + thisDir.getFileName().toString(), info);
                } catch (Exception e) {
                    log.warn("读取 desc 失败，可能处于写入中: {}", meta.getAbsolutePath(), e);
                }
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildCreate() {
                return buildModify();
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildDelete() {
                return null;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildOverflow() {
                return null;
            }
        }

        private static final class Desc extends LocalWatchServiceHelper {

            private final McpStatelessAsyncServer mcpDescServer;

            private Desc(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, McpStatelessAsyncServer mcpDescServer) {
                super(existedMetaActions);
                this.mcpDescServer = mcpDescServer;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.InitLoader buildLoad() {
                return null;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildModify() {
                return null;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildCreate() {
                return null;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildDelete() {
                return null;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildOverflow() {
                return null;
            }
        }

        private static final class Common extends LocalWatchServiceHelper {

            private final Map<String, McpSyncClient> mcpClients;

            private Common(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, Map<String, McpSyncClient> mcpClients) {
                super(existedMetaActions);
                this.mcpClients = mcpClients;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.InitLoader buildLoad() {
                return null;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildModify() {
                return null;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildCreate() {
                return null;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildDelete() {
                return null;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildOverflow() {
                return null;
            }
        }
    }

    private sealed abstract static class McpClientTransportParams permits McpClientTransportParams.Http, McpClientTransportParams.Stdio {
        private final int timeout;

        private McpClientTransportParams(int timeout) {
            this.timeout = timeout;
        }

        private final static class Http extends McpClientTransportParams {
            private final String baseUri;
            private final String endpoint;
            private final Map<String, String> headers;

            private Http(int timeout, String baseUri, String endpoint, Map<String, String> header) {
                super(timeout);
                this.baseUri = baseUri;
                this.endpoint = endpoint;
                this.headers = header;
            }
        }

        private final static class Stdio extends McpClientTransportParams {
            private final String command;
            private final Map<String, String> env;
            private final List<String> args;

            private Stdio(int timeout, String command, Map<String, String> env, List<String> args) {
                super(timeout);
                this.command = command;
                this.env = env;
                this.args = args;
            }
        }
    }

    private static class SystemExecHelper {

        //TODO 后续需在加载时、或者通过配置文件获取可用命令并注册匹配
        private static String[] buildCommands(String ext, Map<String, Object> params, String absolutePath) {
            String command = switch (ext) {
                case "py" -> "python";
                case "sh" -> "bash";
                default -> null;
            };
            if (command == null) {
                return null;
            }
            int paramSize = params == null ? 0 : params.size();
            String[] commands = new String[paramSize + 2];
            commands[0] = command;
            commands[1] = absolutePath;
            AtomicInteger paramCount = new AtomicInteger(2);
            if (params != null) {
                params.forEach((param, value) -> commands[paramCount.getAndIncrement()] = "--" + param + "=" + value);
            }
            return commands;
        }

        private static Result exec(String... command) {
            Result result = new Result();
            List<String> output = new ArrayList<>();
            List<String> error = new ArrayList<>();

            try {
                Process process = new ProcessBuilder(command)
                        .redirectErrorStream(false)  // 分开读
                        .start();

                Thread stdoutThread = new Thread(() -> {
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            output.add(line);
                        }
                    } catch (Exception ignored) {
                    }
                });

                Thread stderrThread = new Thread(() -> {
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            error.add(line);
                        }
                    } catch (Exception ignored) {
                    }
                });

                stdoutThread.start();
                stderrThread.start();

                int exitCode = process.waitFor();
                stdoutThread.join();
                stderrThread.join();

                result.setOk(exitCode == 0);
                result.setResultList(output.isEmpty() ? error : output);
                result.setTotal(String.join("\n",
                        output.isEmpty() ? error : output));

            } catch (Exception e) {
                result.setOk(false);
                result.setTotal(e.getMessage());
            }

            return result;
        }

        @Data
        private static class Result {
            private boolean ok;
            private String total;
            private List<String> resultList;
        }
    }

}
