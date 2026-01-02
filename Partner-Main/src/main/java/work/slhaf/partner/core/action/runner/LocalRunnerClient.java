package work.slhaf.partner.core.action.runner;

import cn.hutool.core.io.FileUtil;
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
import javassist.NotFoundException;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static work.slhaf.partner.common.util.PathUtil.buildPathStr;

@Slf4j
public class LocalRunnerClient extends RunnerClient {

    private final String TMP_ACTION_PATH = buildPathStr(ACTION_PATH, "tmp");
    private final String DYNAMIC_ACTION_PATH = buildPathStr(ACTION_PATH, "dynamic");
    private final String MCP_SERVER_PATH = buildPathStr(ACTION_PATH, "mcp");
    private final String MCP_DESC_PATH = buildPathStr(MCP_SERVER_PATH, "desc");

    /**
     * 存储包括 DescMcp、DynamicActionMcp、CommonMcp 在内的所有 MCP Server 对应的客户端
     * <br/>
     * 自身需要针对 CommonMcp 维护一个存储 McpServers.json 文件的目录
     * <br/>
     * 相关目录按照以下格式组织:
     * <p>
     * MCP_SERVER_PATH/mcp-server.json
     * </p>
     */
    private final Map<String, McpSyncClient> mcpClients = new HashMap<>();
    /**
     * 动态生成的行动程序都将挂载至该 McpServer
     * <br/>
     * 相关目录按照以下格式进行组织:
     * <p>
     * DYNAMIC_ACTION_PATH/action 名称/
     * </p>
     * 每个action子目录下，除了相关的程序文件外，将额外提供一个 <program>.meta.json 文件来提供相关描述文件，
     * 该描述文件将携带 McpTools、MetaActionInfo 相关的所有信息，
     * 故 McpDescServer 将只负责 Common Mcp Servers 的额外描述文件
     */
    private McpStatelessAsyncServer dynamicActionMcpServer;
    /**
     * 负责监听常规 MCP Server 的描述文件（描述文件主要用于添加原本 MCP Tools 不携带的信息，如前置依赖、后置依赖、是否 IO 密集等
     * <br/>
     * 目录按照以下格式组织:
     * <p>
     * MCP_DESC_PATH/server::toolName.desc.json
     * </p>
     * 该 MCP Server-Client 的作用为: 与 CommonMcp Clients 配合，补齐第三方 MCP 服务的描述信息
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
                .build();
        dynamicActionMcpServer = McpServer.async(pair.serverSide())
                .capabilities(serverCapabilities)
                .jsonMapper(McpJsonMapper.getDefault())
                .build();
        // Tools 的执行逻辑应当高度一致化，但仍需要独立为不同 Tool
        // 初始的加载逻辑通过 initialLoad 加载
        // 后续的动态更新通过对应的 event 事件触发
        registerDynamicActionMcpWatch();
        registerMcpClient("dynamic-action", pair.clientSide(), 10);
    }

    private void registerDynamicActionMcpWatch() {
        // MODIFY、CREATE、DELETE、OVERFLOW 都需要不同的处理方式
        WatchContext ctx = new WatchContext(Path.of(DYNAMIC_ACTION_PATH), watchService);
        LocalWatchEventProcessor.Dynamic dynamic = new LocalWatchEventProcessor.Dynamic(existedMetaActions, dynamicActionMcpServer, ctx);
        new LocalWatchServiceBuild.BuildRegistry(ctx)
                .initialLoad(dynamic.buildLoad())
                .registerCreate(dynamic.buildCreate())
                .registerModify(dynamic.buildModify())
                .registerDelete(dynamic.buildDelete())
                .registerOverflow(dynamic.buildOverflow())
                .watchAll(true)
                .commit(executor);
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
                //TODO 更新触发应当遵循触发逻辑: ToolChange -> client.listResource -> 仅填写 tool 信息 | 根据查找到的 resource 文件修正信息
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

    private interface LocalWatchServiceBuild {
        LocalWatchServiceBuild registerCreate(EventHandler handler);

        LocalWatchServiceBuild registerModify(EventHandler handler);

        LocalWatchServiceBuild registerDelete(EventHandler handler);

        LocalWatchServiceBuild registerOverflow(EventHandler handler);

        LocalWatchServiceBuild initialLoad(InitLoader loader);

        LocalWatchServiceBuild watchAll(boolean watchAll);

        void commit(ExecutorService executor);

        interface EventHandler {
            void handle(Path thisDir, Path context);
        }

        interface InitLoader {
            void load();
        }

        class BuildRegistry implements LocalWatchServiceBuild {

            private final Map<WatchEvent.Kind<?>, EventHandler> handlers = new HashMap<>();
            private InitLoader initLoader;
            private final WatchContext ctx;
            private boolean watchAll = false;

            private BuildRegistry(WatchContext ctx) {
                this.ctx = ctx;
            }

            @Override
            public LocalWatchServiceBuild registerCreate(EventHandler handler) {
                ctx.kinds.add(StandardWatchEventKinds.ENTRY_CREATE);
                handlers.put(StandardWatchEventKinds.ENTRY_CREATE, handler);
                return this;
            }

            @Override
            public LocalWatchServiceBuild registerModify(EventHandler handler) {
                ctx.kinds.add(StandardWatchEventKinds.ENTRY_MODIFY);
                handlers.put(StandardWatchEventKinds.ENTRY_MODIFY, handler);
                return this;
            }

            @Override
            public LocalWatchServiceBuild registerDelete(EventHandler handler) {
                ctx.kinds.add(StandardWatchEventKinds.ENTRY_DELETE);
                handlers.put(StandardWatchEventKinds.ENTRY_DELETE, handler);
                return this;
            }

            @Override
            public LocalWatchServiceBuild registerOverflow(EventHandler handler) {
                ctx.kinds.add(StandardWatchEventKinds.OVERFLOW);
                handlers.put(StandardWatchEventKinds.OVERFLOW, handler);
                return this;
            }

            @Override
            public LocalWatchServiceBuild initialLoad(InitLoader loader) {
                initLoader = loader;
                return this;
            }

            @Override
            public LocalWatchServiceBuild watchAll(boolean watchAll) {
                this.watchAll = watchAll;
                return this;
            }

            @Override
            public void commit(ExecutorService executor) {
                registerPath();
                if (initLoader != null)
                    initLoader.load();
                executor.execute(buildWatchTask());
            }

            private void registerPath() {
                Path root = ctx.root;
                WatchService watchService = ctx.watchService;
                Map<WatchKey, Path> watchKeys = ctx.watchKeys;
                try {
                    WatchEvent.Kind<?>[] kindsArray = ctx.kinds.toArray(WatchEvent.Kind[]::new);
                    WatchKey rootKey = root.register(watchService, kindsArray);
                    watchKeys.put(rootKey, root);
                    if (!watchAll) {
                        return;
                    }
                    Stream<Path> walk = Files.list(root).filter(Files::isDirectory);
                    for (Path dir : walk.toList()) {
                        WatchKey key = dir.register(watchService, kindsArray);
                        watchKeys.put(key, dir);
                    }
                    walk.close();
                } catch (IOException e) {
                    log.error("监听目录注册失败: ", e);
                }
            }

            private Runnable buildWatchTask() {
                return () -> {
                    String rootStr = ctx.root.toString();
                    log.info("行动程序目录监听器已启动，监听目录: {}", rootStr);
                    while (true) {
                        WatchKey key;
                        try {
                            key = ctx.watchService.take();
                            List<WatchEvent<?>> events = key.pollEvents();
                            for (WatchEvent<?> e : events) {
                                WatchEvent.Kind<?> kind = e.kind();
                                Object context = e.context();
                                log.info("行动程序目录变更事件: {} - {} - {}", rootStr, kind.name(), context);
                                Path thisDir = (Path) key.watchable();
                                EventHandler handler = handlers.get(kind);
                                if (handler == null) {
                                    continue;
                                }
                                handler.handle(thisDir, context instanceof Path ? thisDir.resolve((Path) context) : null);
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

    private record WatchContext(Path root, WatchService watchService, Map<WatchKey, Path> watchKeys,
                                List<WatchEvent.Kind<?>> kinds) {
        private WatchContext(Path root, WatchService watchService) {
            this(root, watchService, new HashMap<>(), new ArrayList<>());
        }
    }

    private sealed static abstract class LocalWatchEventProcessor permits LocalWatchEventProcessor.Dynamic, LocalWatchEventProcessor.Desc, LocalWatchEventProcessor.Common {

        protected final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions;
        protected final WatchContext ctx;

        private LocalWatchEventProcessor(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, WatchContext ctx) {
            this.existedMetaActions = existedMetaActions;
            this.ctx = ctx;
        }

        protected abstract @NotNull LocalWatchServiceBuild.InitLoader buildLoad();

        protected abstract @NotNull LocalWatchServiceBuild.EventHandler buildModify();

        protected abstract @NotNull LocalWatchServiceBuild.EventHandler buildCreate();

        protected abstract @NotNull LocalWatchServiceBuild.EventHandler buildDelete();

        protected abstract @NotNull LocalWatchServiceBuild.EventHandler buildOverflow();

        @SuppressWarnings("LoggingSimilarMessage")
        private static final class Dynamic extends LocalWatchEventProcessor {

            private final McpStatelessAsyncServer dynamicActionMcpServer;

            private Dynamic(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, McpStatelessAsyncServer dynamicActionMcpServer, WatchContext ctx) {
                super(existedMetaActions, ctx);
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

            private boolean addAction(String name, Path dir) {
                File program = null;
                try (Stream<Path> stream = Files.list(dir)) {
                    for (Path p : stream.toList()) {
                        if (p.getFileName().toString().startsWith("run."))
                            program = p.toFile();
                    }
                } catch (Exception e) {
                    log.error("添加 action 失败", e);
                    return false;
                }
                MetaActionInfo info;
                try {
                    info = JSONUtil.readJSONObject(dir.resolve("desc.json").toFile(), StandardCharsets.UTF_8).toBean(MetaActionInfo.class);
                } catch (Exception e) {
                    log.error("desc.json 加载失败: {}", dir);
                    return false;
                }
                String actionKey = "local::" + name;
                existedMetaActions.put(actionKey, info);
                dynamicActionMcpServer.addTool(buildAsyncToolSpecification(info, program, actionKey, name)).subscribe();
                return true;
            }

            private void removeAction(String name) {
                existedMetaActions.remove("local::" + name);
                dynamicActionMcpServer.removeTool(name).subscribe();
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
                // 从该路径列出已存在的目录，每个目录对应不同的行动程序及描述文件，从描述文件加载程序信息
                return () -> {
                    Path root = ctx.root;
                    File file = root.toFile();
                    if (file.isFile()) {
                        throw new ActionInitFailedException("未找到目录: " + root);
                    }
                    File[] files = file.listFiles();
                    if (files == null) {
                        throw new ActionInitFailedException("未正常读取目录: " + root);
                    }
                    for (File dir : files) {
                        if (!normalPath(dir.toPath())) {
                            continue;
                        }
                        addAction(dir.getName(), dir.toPath());
                    }
                };
            }

            private McpStatelessServerFeatures.AsyncToolSpecification buildAsyncToolSpecification(MetaActionInfo
                                                                                                          info, File program, String actionKey, String name) {
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
                    modify(thisDir, context);
                };
            }

            private void modify(Path thisDir, Path context) {
                String fileName = context.getFileName().toString();
                if (fileName.equals("desc.json")) {
                    handleMetaModify(thisDir);
                }
                if (fileName.startsWith("run.")) {
                    handleProgramModify(thisDir);
                }
            }

            private void handleProgramModify(Path thisDir) {
                String name = thisDir.getFileName().toString();
                String actionKey = "local::" + name;
                // 检查是否存在当前 program 对应的 Tool
                if (existedMetaActions.containsKey(actionKey)) {
                    return;
                }
                if (!addAction(name, thisDir)) {
                    removeAction(name);
                }
            }

            private void handleMetaModify(Path thisDir) {
                // 检查是否除了描述文件外还存在别的可执行文件
                String name = thisDir.getFileName().toString();
                if (!addAction(name, thisDir)) {
                    removeAction(name);
                }
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildCreate() {
                return (thisDir, context) -> {
                    if (thisDir.equals(ctx.root) && Files.isDirectory(context)) {
                        try {
                            context.register(ctx.watchService, ctx.kinds.toArray(WatchEvent.Kind[]::new));
                        } catch (IOException e) {
                            log.error("监听目录注册失败: {}", context);
                        }
                    }
                    if (normalPath(thisDir)) {
                        modify(thisDir, context);
                    }
                    if (Files.isDirectory(context) && normalPath(context)) {
                        File[] files = context.toFile().listFiles();
                        if (files == null) {
                            log.warn("目录无法访问: {}", context);
                            return;
                        }
                        for (File f : files) {
                            modify(context, f.toPath());
                        }
                    }
                };
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildDelete() {
                return (thisDir, context) -> {
                    // 如果发生在 root，且context为目录，则需要移除对应的 action
                    if (thisDir.equals(ctx.root)) {
                        String name = context.getFileName().toString();
                        Path candidate = ctx.root.resolve(name);

                        // 如果 root 下仍然存在一个同名目录
                        if (Files.isDirectory(candidate)) {
                            // 说明删的是同名文件，不是 action 目录
                            return;
                        }

                        removeAction(name);

                        AtomicReference<WatchKey> toRemove = new AtomicReference<>();
                        ctx.watchKeys.forEach((key, path) -> {
                            if (path.getFileName().toString().equals(name)) {
                                key.cancel();
                                toRemove.set(key);
                            }
                        });
                        if (toRemove.get() != null) {
                            ctx.watchKeys.remove(toRemove.get());
                        }
                        return;
                    }

                    // 如果发生在非 root 目录内且 context 不符合 action 目录特征
                    // 由于只会监听 root 目录与 action 目录
                    // 所以此时则证明当前目录对应的行动已不可靠，需要移除
                    if (!thisDir.equals(ctx.root) && !normalPath(thisDir)) {
                        // 未通过校验则删除对应的 action，并在 DynamicActonMcpServer 中删除对应的工具
                        String name = thisDir.getFileName().toString();
                        removeAction(name);
                    }
                };
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildOverflow() {
                return (thisDir, context) -> {
                    // 直接从 existedMetaActions 中拿取现有的 key，上游也从这里发现可用工具
                    Set<String> existed = existedMetaActions.keySet().stream().map(actionKey -> actionKey.split("::")[1]).collect(Collectors.toSet());
                    Set<String> currentDirs = new HashSet<>();
                    // 按照预期 root 目录下有效 path 只包括各个 action 目录
                    // 排除非目录 path
                    try (Stream<Path> stream = Files.list(ctx.root).filter(Files::isDirectory)) {
                        stream.forEach(path -> {
                            String name = path.getFileName().toString();
                            currentDirs.add(name);

                            boolean contains = existed.contains(name);
                            boolean normal = normalPath(path);

                            // 如果该目录对应 action 已被记录，且符合 action 目录要求，则无处理
                            // 如果已被记录，但不符合，则移除行动
                            // 此时必定被监听
                            if (contains && !normal) {
                                removeAction(name);
                            }

                            // 如果 action 没有记录，但符合要求，由于此时必定尚未被监听，则注册监听且添加新 action
                            // 如果未被记录，且不符合要求，则只注册监听
                            if (!contains) {
                                boolean alreadyWatching = ctx.watchKeys.values().stream()
                                        .anyMatch(p -> p.equals(path));
                                if (!alreadyWatching) {
                                    try {
                                        WatchKey watchKey = path.register(ctx.watchService, ctx.kinds.toArray(WatchEvent.Kind[]::new));
                                        ctx.watchKeys.put(watchKey, path);
                                    } catch (IOException e) {
                                        log.error("监听目录注册失败: {}", path);
                                    }
                                }

                                if (normal) {
                                    addAction(name, path);
                                }
                            }
                        });
                    } catch (IOException e) {
                        log.error("目录无法读取: {}", ctx.root);
                        return;
                    }
                    for (String existedName : existed) {
                        if (!currentDirs.contains(existedName)) {
                            removeAction(existedName);
                        }
                    }
                };
            }
        }

        private static final class Desc extends LocalWatchEventProcessor {

            private final McpStatelessAsyncServer mcpDescServer;
            private final HashMap<String, String> descCache = new HashMap<>();

            private Desc(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, McpStatelessAsyncServer mcpDescServer, WatchContext ctx) {
                super(existedMetaActions, ctx);
                this.mcpDescServer = mcpDescServer;
            }

            private McpStatelessServerFeatures.@NotNull AsyncResourceSpecification buildAsyncResourceSpecification(String name, String uri) {
                McpSchema.Resource resource = McpSchema.Resource.builder()
                        .name(name)
                        .title(name)
                        .description("Action descriptor for " + name)
                        .mimeType("application/json")
                        .uri(uri)
                        .build();
                BiFunction<McpTransportContext, McpSchema.ReadResourceRequest, Mono<McpSchema.ReadResourceResult>> readHandler = (context, request) -> {
                    String requestUri = request.uri();
                    String result = descCache.get(requestUri);
                    if (result == null) {
                        return Mono.error(new NotFoundException("未找到 Resource: " + requestUri));
                    }
                    return Mono.just(new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(requestUri, "application/json", result))));
                };
                return new McpStatelessServerFeatures.AsyncResourceSpecification(resource, readHandler);
            }

            private boolean normal(String fileName) {
                String pattern = "[a-z][A-Z]+::[a-z][A-Z]+.desk.json";
                Pattern p = Pattern.compile(pattern);
                Matcher matcher = p.matcher(fileName);
                if (!matcher.find()) {
                    log.error("文件名称不符合要求: {}", fileName);
                    return false;
                }
                return true;
            }

            private boolean normal(File file) {
                String name = file.getName();
                return normal(name);
            }

            private boolean normal(Path path) {
                return normal(path.toFile());
            }

            @SuppressWarnings("UnusedReturnValue")
            private boolean addResource(File file) {
                String name = file.getName();
                if (!normal(name)) {
                    return false;
                }
                // 读取并解析为 MetaActionInfo，存入 resources
                try {
                    MetaActionInfo info = JSONUtil.readJSONObject(file, StandardCharsets.UTF_8).toBean(MetaActionInfo.class);
                    String uri = ctx.root.resolve(name).toUri().toString();
                    descCache.put(uri, JSONObject.toJSONString(info));
                    mcpDescServer.addResource(buildAsyncResourceSpecification(name, uri)).subscribe();
                } catch (Exception e) {
                    log.error("desc.json 解析失败: {}", file.getAbsolutePath());
                    return false;
                }
                return true;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.InitLoader buildLoad() {
                return () -> {
                    // DescMcp 的加载逻辑只负责读取已有的 *.desc.json 并注册为 resources
                    // 正常来讲 root 直接对应 MCP_DESC_PATH，先检查 root 是否为目录，否则拒绝启动
                    Path root = ctx.root;
                    if (!Files.isDirectory(root)) {
                        throw new ActionInitFailedException("未找到目录: " + root);
                    }
                    File[] files = root.toFile().listFiles();
                    if (files == null) {
                        throw new ActionInitFailedException("目录无法正常读取: " + root);
                    }
                    for (File file : files) {
                        addResource(file);
                    }
                };
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildModify() {
                return (thisDir, context) -> {
                    // 排除目录事件、名称不符合要求的文件
                    String fileName = context.getFileName().toString();
                    if (!Files.isDirectory(context) || !normal(fileName)) {
                        return;
                    }

                    // 先尝试能否正常读取，再决定是否步入更新逻辑
                    MetaActionInfo info;
                    try {
                        info = JSONUtil.readJSONObject(context.toFile(), StandardCharsets.UTF_8).toBean(MetaActionInfo.class);
                    } catch (Exception e) {
                        log.warn("desc.json 加载失败: {}", context);
                        return;
                    }

                    // 要处理的 MODIFY 上下文只有一种
                    // *.desc.json 发生变更时，检查是否存在于 existedMetaActions 内部
                    // 如果存在，则读取并更新对应的 info，同时更新 descCache
                    // 如果不存在，则只更新 descCache
                    String actionKey = fileName.replace(".desc.json", "");
                    if (existedMetaActions.containsKey(actionKey)) {
                        existedMetaActions.put(actionKey, info);
                    }
                    descCache.put(context.toUri().toString(), JSONObject.toJSONString(info));
                };
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

        private static final class Common extends LocalWatchEventProcessor {

            private final Map<String, McpSyncClient> mcpClients;

            private Common(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, Map<String, McpSyncClient> mcpClients, WatchContext ctx) {
                super(existedMetaActions, ctx);
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
