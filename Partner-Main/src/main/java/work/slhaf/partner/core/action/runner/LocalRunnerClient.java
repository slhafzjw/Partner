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
import javassist.NotFoundException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import work.slhaf.partner.common.mcp.InProcessMcpTransport;
import work.slhaf.partner.core.action.entity.ActionFileMetaData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.MetaActionType;
import work.slhaf.partner.core.action.exception.ActionInitFailedException;
import work.slhaf.partner.core.action.exception.ActionSerializeFailedException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static work.slhaf.partner.common.util.PathUtil.buildPathStr;

@Slf4j
public class LocalRunnerClient extends RunnerClient {

    private final String TMP_ACTION_PATH;
    private final String DYNAMIC_ACTION_PATH;
    private final String MCP_SERVER_PATH;
    private final String MCP_DESC_PATH;

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

    public LocalRunnerClient(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, ExecutorService executor, @Nullable String baseActionPath) {
        super(existedMetaActions, executor, baseActionPath);
        this.TMP_ACTION_PATH = buildPathStr(ACTION_PATH, "tmp");
        this.DYNAMIC_ACTION_PATH = buildPathStr(ACTION_PATH, "dynamic");
        this.MCP_SERVER_PATH = buildPathStr(ACTION_PATH, "mcp");
        this.MCP_DESC_PATH = buildPathStr(MCP_SERVER_PATH, "desc");

        createPath(TMP_ACTION_PATH);
        createPath(DYNAMIC_ACTION_PATH);
        createPath(MCP_SERVER_PATH);
        createPath(MCP_DESC_PATH);

        try {
            registerDescMcp();
            registerDynamicActionMcp();
            registerCommonMcp();
        } catch (IOException e) {
            throw new ActionInitFailedException("目录监听器启动失败", e);
        }
        setupShutdownHook();
    }

    private void registerCommonMcp() throws IOException {
        val ctx = new WatchContext(Path.of(MCP_SERVER_PATH), FileSystems.getDefault().newWatchService());
        val common = new LocalWatchEventProcessor.Common(existedMetaActions, mcpClients, ctx);
        new LocalWatchServiceBuild.BuildRegistry(ctx)
                .initialLoad(common.buildLoad())
                .registerCreate(common.buildCreate())
                .registerDelete(common.buildDelete())
                .registerModify(common.buildModify())
                .registerOverflow(common.buildOverflow())
                .commit(executor);
        log.info("CommonMcp 文件监听注册完毕");
    }

    private void registerDescMcp() throws IOException {
        InProcessMcpTransport.Pair pair = InProcessMcpTransport.pair();
        McpSchema.ServerCapabilities serverCapabilities = McpSchema.ServerCapabilities.builder()
                .resources(true, true)
                .build();
        mcpDescServer = McpServer.async(pair.serverSide())
                .capabilities(serverCapabilities)
                .jsonMapper(McpJsonMapper.getDefault())
                .build();
        registerDescMcpWatch();
        log.info("DescMcp 文件监听注册完毕");
        registerMcpClient("mcp-desc", pair.clientSide(), 10);
        log.info("DescMcp 注册完毕");

    }

    private void registerDescMcpWatch() throws IOException {
        WatchContext ctx = new WatchContext(Path.of(MCP_DESC_PATH), FileSystems.getDefault().newWatchService());
        LocalWatchEventProcessor.Desc desc = new LocalWatchEventProcessor.Desc(existedMetaActions, mcpDescServer, ctx);
        new LocalWatchServiceBuild.BuildRegistry(ctx)
                .initialLoad(desc.buildLoad())
                .registerCreate(desc.buildCreate())
                .registerDelete(desc.buildDelete())
                .registerModify(desc.buildModify())
                .registerOverflow(desc.buildOverflow())
                .watchAll(true)
                .commit(executor);
    }

    private void registerDynamicActionMcp() throws IOException {
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
        log.info("DynamicActionMcp 文件监听注册完毕");
        registerMcpClient("dynamic-action", pair.clientSide(), 10);
        log.info("DynamicActionMcp 注册完毕");
    }

    private void registerDynamicActionMcpWatch() throws IOException {
        // MODIFY、CREATE、DELETE、OVERFLOW 都需要不同的处理方式
        WatchContext ctx = new WatchContext(Path.of(DYNAMIC_ACTION_PATH), FileSystems.getDefault().newWatchService());
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
        log.debug("执行行动: {}", metaAction);
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
        log.debug("行动执行结果: {}", response);
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
        log.debug("行动程序临时序列化: {}", tempAction);
        Path path = Path.of(tempAction.getLocation());
        File file = path.toFile();
        file.createNewFile();
        Files.writeString(path, code);
        log.debug("临时序列化完毕");
    }

    private static @NotNull Path createActionDir(String baseName, Path baseDir) {
        Path actionDir = null;

        // 原子地“抢占”目录名
        for (int i = 0; ; i++) {
            String dirName = (i == 0) ? baseName : baseName + "(" + i + ")";
            Path candidate = baseDir.resolve(dirName);

            try {
                Files.createDirectory(candidate); // 原子操作
                actionDir = candidate;
                break;
            } catch (FileAlreadyExistsException ignored) {
                // 继续尝试下一个名字
            } catch (IOException e) {
                throw new ActionSerializeFailedException(
                        "无法创建行动目录: " + candidate.toAbsolutePath(), e
                );
            }
        }
        return actionDir;
    }

    @Override
    public void persistSerialize(MetaActionInfo metaActionInfo, ActionFileMetaData fileMetaData) {
        log.debug("行动程序持久序列化: {}", metaActionInfo);
        val baseDir = Path.of(DYNAMIC_ACTION_PATH);

        if (!Files.isDirectory(baseDir)) {
            throw new ActionSerializeFailedException(
                    "目录不存在或不可用: " + baseDir.toAbsolutePath()
            );
        }

        val baseName = fileMetaData.getName();
        val ext = fileMetaData.getExt();

        val actionDir = createActionDir(baseName, baseDir);

        // 使用临时文件写入内容
        val runTmp = actionDir.resolve("run." + ext + ".tmp");
        val descTmp = actionDir.resolve("desc.json.tmp");

        val runFinal = actionDir.resolve("run." + ext);
        val descFinal = actionDir.resolve("desc.json");

        try {
            Files.writeString(runTmp, fileMetaData.getContent());
            Files.writeString(descTmp, JSONObject.toJSONString(metaActionInfo));

            // 原子提交
            Files.move(runTmp, runFinal, StandardCopyOption.ATOMIC_MOVE);
            Files.move(descTmp, descFinal, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // 失败清理
            safeDelete(runTmp);
            safeDelete(descTmp);
            safeDelete(runFinal);
            safeDelete(descFinal);
            safeDelete(actionDir);
            throw new ActionSerializeFailedException("行动文件写入失败", e);
        }
        log.debug("持久序列化结束");
    }

    private void safeDelete(Path path) {
        try {
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException ignored) {
        }
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

    private void registerMcpClient(String id, McpClientTransport clientTransport, int timeout) {
        McpSyncClient client = McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofSeconds(timeout))
                .clientInfo(new McpSchema.Implementation(id, "PARTNER"))
                // 行动程序(现 MCP Tool)的描述文本将直接由resources返回
                // 原因: ToolChange 发送的内容侧重调用，缺少可承担描述文本的字段
                //       ResourcesChange 事件传递的 Resource 可以由 Client 读取内容
                //       预计在 Server 侧，收到客户端发送的新的行动程序信息，该信息由客户端处补充后，将其放置在指定位置
                //       并写入描述文件、发起 ResourcesChange 事件
                .build();
        mcpClients.put(id, client);
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
                        log.debug("注册目录监听: {}", dir);
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
                        WatchKey key = null;
                        try {
                            key = ctx.watchService.take();
                            List<WatchEvent<?>> events = key.pollEvents();
                            for (WatchEvent<?> e : events) {
                                WatchEvent.Kind<?> kind = e.kind();
                                Object context = e.context();
                                log.debug("文件目录监听事件: {} - {} - {}", rootStr, kind.name(), context);
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
                        } finally {
                            if (key != null) {
                                // reset 返回 false 表示该 key 已失效（目录被删、不可访问等）
                                boolean valid = key.reset();
                                if (!valid) {
                                    log.info("WatchKey 已失效，停止监听该目录: {}", key.watchable());
                                }
                            }
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

        protected File[] loadFiles(Path root) {
            // 在批量删除场景下，在接收到事件时目录等内容可能已被删除，此时不应该报错，而是返回一个‘异常值’
            if (!Files.isDirectory(root)) {
                return null;
            }
            return root.toFile().listFiles();
        }

        @SuppressWarnings("LoggingSimilarMessage")
        private static final class Dynamic extends LocalWatchEventProcessor {

            private final McpStatelessAsyncServer dynamicActionMcpServer;

            private Dynamic(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, McpStatelessAsyncServer dynamicActionMcpServer, WatchContext ctx) {
                super(existedMetaActions, ctx);
                this.dynamicActionMcpServer = dynamicActionMcpServer;
            }

            @SuppressWarnings("BooleanMethodIsAlwaysInverted")
            private boolean normalPath(Path path) {
                val files = loadFiles(path);
                if (files == null || files.length < 2) {
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
            private final ConcurrentHashMap<String, String> descCache = new ConcurrentHashMap<>();

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
                return fileName.endsWith(".desc.json") && fileName.contains("::");
            }

            private boolean normal(File file) {
                String name = file.getName();
                return normal(name);
            }

            private boolean normal(Path path) {
                return normal(path.toFile());
            }

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
                    mcpDescServer.addResource(buildAsyncResourceSpecification(name, uri)).block();
                    String actionKey = name.replace(".desc.json", "");
                    if (existedMetaActions.containsKey(actionKey)) {
                        existedMetaActions.put(actionKey, info);
                    }
                } catch (Exception e) {
                    log.error("desc.json 解析失败: {}", file.getAbsolutePath());
                    return false;
                }
                return true;
            }

            private void removeResource(Path path) {
                String uri = path.toUri().toString();
                String actionKey = path.getFileName().toString().replace(".desc.json", "");

                descCache.remove(uri);
                mcpDescServer.removeResource(uri).block();
                if (existedMetaActions.containsKey(actionKey)) {
                    resetMetaActionInfo(existedMetaActions.get(actionKey));
                }
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.InitLoader buildLoad() {
                return () -> {
                    // DescMcp 的加载逻辑只负责读取已有的 *.desc.json 并注册为 resources
                    // 正常来讲 root 直接对应 MCP_DESC_PATH，先检查 root 是否为目录，否则拒绝启动
                    val files = loadFiles(ctx.root);
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
                    if (!Files.isRegularFile(context) || !normal(fileName)) {
                        return;
                    }

                    if (!addResource(context.toFile())) {
                        removeResource(context);
                    }
                };
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildCreate() {
                return buildModify();
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildDelete() {
                return (thisDir, context) -> {
                    // 排除被删除文件名称不符合要求的事件
                    String fileName = context.getFileName().toString();
                    if (!normal(fileName)) {
                        return;
                    }

                    // DELETE 事件发生后，需要移除对应的 descCache 条目;
                    // 如果存在对应的 info,也需要将其中的额外信息进行重置，只保留 Tools 自身的信息
                    removeResource(context);
                };
            }

            private void resetMetaActionInfo(MetaActionInfo info) {
                info.setIo(false);
                info.getTags().clear();
                info.getPreActions().clear();
                info.getPostActions().clear();
                info.setStrictDependencies(false);
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildOverflow() {
                return (thisDir, context) -> {
                    // 对于 OVERFLOW 事件，需要依据当前目录下的所有 *.desc.json 针对现有内容进行修复
                    List<File> files;
                    try (Stream<Path> stream = Files.list(ctx.root)) {
                        files = stream.filter(Files::isRegularFile).filter(this::normal).map(Path::toFile).toList();
                    } catch (IOException e) {
                        log.error("目录无法访问: {}", ctx.root);
                        return;
                    }
                    Set<String> currentUriStr = new HashSet<>();

                    for (File file : files) {
                        MetaActionInfo info = null;
                        try {
                            info = JSONUtil.readJSONObject(file, StandardCharsets.UTF_8).toBean(MetaActionInfo.class);
                        } catch (Exception e) {
                            log.warn("desc.json 读取失败: {}", file.toPath());
                        }

                        boolean available = info != null;

                        // 如果读取成功，则更新 descCache
                        // 若在 existedMetaActions 中存在，则更新对应 info
                        String uriStr = file.toURI().toString();
                        currentUriStr.add(uriStr);

                        if (available) {
                            // 由于涉及内容均为 map，所以额外判断没有必要，直接进行 add 行为即可
                            addResource(file);
                        } else {
                            // 如果读取失败，则移除对应 descCache 条目
                            // 若在 existedMetaActions 中存在，则重置对应 info
                            removeResource(file.toPath());
                        }

                    }

                    List<String> serverUris = mcpDescServer.listResources()
                            .map(McpSchema.Resource::uri)
                            .collectList()
                            .block();
                    if (serverUris == null) {
                        log.error("无法获取 DescMcpServer 持有的资源列表");
                        return;
                    }
                    for (String uri : serverUris) {
                        if (!currentUriStr.contains(uri)) {
                            removeResource(Paths.get(URI.create(uri)));
                        }
                    }
                };
            }
        }

        private static final class Common extends LocalWatchEventProcessor {

            private final Map<String, McpSyncClient> mcpClients;
            private final Map<File, McpConfigFileRecord> mcpConfigFileCache = new HashMap<>();

            @SuppressWarnings("BooleanMethodIsAlwaysInverted")
            private boolean normalFile(File file) {
                return file.exists() && file.isFile() && file.getName().endsWith(".json");
            }

            private Common(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, Map<String, McpSyncClient> mcpClients, WatchContext ctx) {
                super(existedMetaActions, ctx);
                this.mcpClients = mcpClients;
            }

            /**
             * 该部分主要发生在扫描到新的MCP Server描述文件时出现的注册逻辑
             *
             * @param id                       MCP Client 的 id
             * @param mcpClientTransportParams MCP Server 的参数
             */
            private void registerMcpClient(String id, McpClientTransportParams mcpClientTransportParams) {
                val clientTransport = createTransport(mcpClientTransportParams);
                val timeout = mcpClientTransportParams.timeout;
                val client = McpClient.sync(clientTransport)
                        .requestTimeout(Duration.ofSeconds(timeout))
                        .clientInfo(new McpSchema.Implementation(id, "PARTNER"))
                        .build();
                mcpClients.put(id, client);

                for (McpSchema.Tool tool : client.listTools().tools()) {
                    val metaActionInfo = buildMetaActionInfo(tool);
                    existedMetaActions.put(id + "::" + tool.name(), metaActionInfo);
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
                        val serverParameters = ServerParameters.builder(params.command).env(params.env).args(params.args).build();
                        yield new StdioClientTransport(serverParameters, McpJsonMapper.getDefault());
                    }
                    case McpClientTransportParams.Http params -> {
                        val customizer = new McpSyncHttpClientRequestCustomizer() {
                            @Override
                            public void customize(HttpRequest.Builder builder, String method, URI endpoint, String body, McpTransportContext context) {
                                params.headers.forEach(builder::setHeader);
                            }
                        };
                        yield HttpClientSseClientTransport.builder(params.baseUri).httpRequestCustomizer(customizer).sseEndpoint(params.endpoint).build();
                    }
                };
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.InitLoader buildLoad() {
                return () -> {
                    // For CommonMcp, we need to list all files in MCP_SERVER_PATH,
                    // and search for files with extend name .json,
                    // and then reading them as JSONObject to get McpClientTransportParams.
                    val files = loadFiles(ctx.root);

                    for (File file : files) {
                        if (!normalFile(file)) {
                            continue;
                        }
                        loadAndRegisterMcpClientsFromFile(file);
                    }
                };
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildModify() {
                /*
                发现文件更改事件时，读取该文件中存放的mcp配置，与现有的 MCP 记录对比
                根据其是否发生配置变动等，针对对应的 client 进行调整
                如果额外维护一个 文件-clientIds 的映射，可以解决删除某一mcp的情况
                但如果对于极端场景：从某一文件剪切并粘贴至另一文件，但后者先于前者保存
                此时就会出现问题，重复client无法被注册
                建议对于这种‘分布式’的配置存放方式，每个文件变更最好都触发全量加载
                */
                return (thisDir, context) -> checkAndReload(true);
            }

            private cn.hutool.json.JSONObject readJson(File file) {
                try {
                    return JSONUtil.readJSONObject(file, StandardCharsets.UTF_8);
                } catch (IORuntimeException ignored) {
                    return null;
                }
            }

            private cn.hutool.json.JSONObject readMcp(cn.hutool.json.JSONObject json, String id) {
                try {
                    return json.getJSONObject(id);
                } catch (Exception ignored) {
                    return null;
                }
            }

            @SuppressWarnings("unchecked")
            private McpClientTransportParams readParams(cn.hutool.json.JSONObject mcp) {
                val stdioKeys = Set.of("command", "args", "env");
                val httpKeys = Set.of("uri", "endpoint", "headers");
                val httpKey = Set.of("url");
                val keys = mcp.keySet();
                val timeout = mcp.getInt("timeout", 10);

                if (keys.equals(stdioKeys)) {
                    val command = mcp.getStr("command");
                    val env = mcp.getBean("env", Map.class);
                    val args = mcp.getBeanList("args", String.class);
                    if (command == null || env == null || args == null) {
                        return null;
                    }
                    return new McpClientTransportParams.Stdio(timeout, command, env, args);
                }

                if (keys.equals(httpKeys)) {
                    val uri = mcp.getStr("uri");
                    val endpoint = mcp.getStr("endpoint");
                    val headers = mcp.getBean("headers", Map.class);
                    if (uri == null || endpoint == null || headers == null) {
                        return null;
                    }
                    return new McpClientTransportParams.Http(timeout, uri, endpoint, headers);
                }

                if (keys.equals(httpKey)) {
                    val url = mcp.getStr("url");
                    if (url == null) {
                        return null;
                    }
                    return new McpClientTransportParams.Http(timeout, url, "", Map.of());
                }

                return null;
            }

            private void checkAndReload(boolean trustCache) {
                /*
                    for each file cannot present all mcp configurations,
                    we need to load all at once, and then compare them with existed records.
                    we will record existing mcp paramsCacheMap and id-params map for which is changed.

                    recording changedMap only cannot figure out which mcp was deleted,
                    so existingMcpIdSet attr is required
                     */
                val changedMap = new HashMap<String, McpClientTransportParams>();
                val existingMcpIdSet = new HashSet<String>();

                val files = loadFiles(ctx.root);
                for (File file : files) {
                    if (!normalFile(file)) {
                        continue;
                    }

                    // check if necessary stats changed, null record is seen as file changed
                    val fileRecord = mcpConfigFileCache.get(file);
                    boolean fileRecordExists = fileRecord != null;
                    if (fileRecordExists && !fileChanged(file, fileRecord) && trustCache) {
                        existingMcpIdSet.addAll(fileRecord.paramsCacheMap().keySet());
                        continue;
                    }

                    // if changed, read file and load mcp configurations
                    val mcpConfigJson = readJson(file);
                    if (mcpConfigJson == null) {
                        // uses old records to avoid abnormal deletion
                        if (fileRecordExists) {
                            existingMcpIdSet.addAll(fileRecord.paramsCacheMap().keySet());
                        }
                        continue;
                    }

                    val newFileRecord = new McpConfigFileRecord(file.lastModified(), file.length(), new HashMap<>());
                    for (String id : mcpConfigJson.keySet()) {
                        val mcp = readMcp(mcpConfigJson, id);
                        if (mcp == null) {
                            continue;
                        }

                        val params = readParams(mcp);
                        if (params == null) {
                            continue;
                        }

                        existingMcpIdSet.add(id);
                        newFileRecord.paramsCacheMap().put(id, params);

                        if (fileRecordExists) {
                            val paramsCache = fileRecord.paramsCacheMap().get(id);
                            if (paramsCache != null && paramsCache.equals(params)) {
                                continue;
                            }
                        }

                        changedMap.put(id, params);
                    }
                    mcpConfigFileCache.put(file, newFileRecord);
                }

                updateMcpClients(changedMap, existingMcpIdSet);
            }

            private void updateMcpClients(HashMap<String, McpClientTransportParams> changedMap, HashSet<String> existingMcpIdSet) {
                // following attr changedMap, update or insert mcp clients
                changedMap.forEach((id, params) -> {
                    // close outdated clients if exists
                    val oldClient = mcpClients.get(id);
                    if (oldClient != null) {
                        oldClient.close();
                    }
                    // create new clients
                    registerMcpClient(id, params);
                });

                // following attr existingMcpIdSet, align mcp clients
                // new mcp clients and outdated clients has been updated in above logic
                // this part focus on removing non-existing mcp
                mcpClients.keySet().removeIf(id -> !existingMcpIdSet.contains(id));
            }

            private boolean fileChanged(File file, McpConfigFileRecord fileRecord) {
                val lastModified = file.lastModified();
                val length = file.length();

                return fileRecord.lastModified() != lastModified || fileRecord.length() != length;
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildOverflow() {
                return (thisDir, context) -> checkAndReload(false);
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildCreate() {
                return (thisDir, context) -> {
                    val file = context.toFile();
                    if (!normalFile(file)) {
                        return;
                    }

                    loadAndRegisterMcpClientsFromFile(file);
                };
            }

            private void loadAndRegisterMcpClientsFromFile(File file) {
                val mcpConfigJson = readJson(file);
                if (mcpConfigJson == null) {
                    return;
                }

                val newFileRecord = new McpConfigFileRecord(file.lastModified(), file.length());
                for (String id : mcpConfigJson.keySet()) {
                    val mcp = readMcp(mcpConfigJson, id);
                    if (mcp == null) {
                        continue;
                    }

                    val params = readParams(mcp);
                    if (params == null) {
                        continue;
                    }

                    registerMcpClient(id, params);
                    newFileRecord.paramsCacheMap().put(id, params);
                }
                mcpConfigFileCache.put(file, newFileRecord);
            }

            @Override
            @NotNull
            protected LocalWatchServiceBuild.EventHandler buildDelete() {
                return (thisDir, context) -> {
                    val file = context.toFile();
                    if (file.isFile() && file.getName().endsWith(".json")) {
                        return;
                    }

                    val fileRecord = mcpConfigFileCache.remove(file);
                    if (fileRecord == null) {
                        return;
                    }

                    // clear from existedMetaActions and mcpClients
                    // client id comes from fileRecord.paramsCache
                    // actionKey from `id::toolName`
                    val clientIdSet = fileRecord.paramsCacheMap().keySet();
                    for (String clientId : clientIdSet) {
                        val client = mcpClients.remove(clientId);
                        if (client == null) {
                            continue;
                        }

                        val tools = client.listTools().tools();
                        for (McpSchema.Tool tool : tools) {
                            val actionKey = clientId + "::" + tool.name();
                            existedMetaActions.remove(actionKey);
                        }
                        client.close();
                    }
                };
            }

            private record McpConfigFileRecord(long lastModified, long length,
                                               Map<String, McpClientTransportParams> paramsCacheMap) {
                public McpConfigFileRecord(long lastModified, long length) {
                    this(lastModified, length, new HashMap<>());
                }
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
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            output.add(line);
                        }
                    } catch (Exception ignored) {
                    }
                });

                Thread stderrThread = new Thread(() -> {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
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
                result.setTotal(String.join("\n", output.isEmpty() ? error : output));

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
