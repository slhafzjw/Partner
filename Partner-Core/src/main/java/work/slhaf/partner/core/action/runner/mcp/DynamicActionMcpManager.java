package work.slhaf.partner.core.action.runner.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import work.slhaf.partner.common.mcp.InProcessMcpTransport;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.ActionInitFailedException;
import work.slhaf.partner.core.action.runner.execution.CommandExecutionService;
import work.slhaf.partner.framework.agent.common.support.DirectoryWatchSupport;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class DynamicActionMcpManager implements AutoCloseable {

    private final Path root;
    private final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions;
    private final CommandExecutionService commandExecutionService;
    private final McpStatelessAsyncServer server;
    private final InProcessMcpTransport clientTransport;
    private final DirectoryWatchSupport watchSupport;

    public DynamicActionMcpManager(Path root,
                                   ConcurrentHashMap<String, MetaActionInfo> existedMetaActions,
                                   ExecutorService executor) throws IOException {
        this.root = root;
        this.existedMetaActions = existedMetaActions;
        this.commandExecutionService = CommandExecutionService.INSTANCE;
        InProcessMcpTransport.Pair pair = InProcessMcpTransport.pair();
        this.clientTransport = pair.clientSide();
        McpSchema.ServerCapabilities serverCapabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build();
        this.server = McpServer.async(pair.serverSide())
                .capabilities(serverCapabilities)
                .jsonMapper(McpJsonMapper.getDefault())
                .build();
        this.watchSupport = new DirectoryWatchSupport(new DirectoryWatchSupport.Context(root), executor, 1, this::loadExisting)
                .onCreate(this::handleCreate)
                .onModify(this::handleModify)
                .onDelete(this::handleDelete)
                .onOverflow((thisDir, context) -> reconcile());
    }

    public McpTransportConfig.InProcess clientConfig(int timeout) {
        return new McpTransportConfig.InProcess(timeout, clientTransport);
    }

    public void start() {
        watchSupport.start();
        log.info("DynamicActionMcp 文件监听注册完毕");
    }

    private void loadExisting() {
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
    }

    private boolean isTmp(Path context) {
        return context.getFileName().endsWith(".tmp");
    }

    private void handleModify(Path thisDir, Path context) {
        if (context == null || isTmp(context) || !normalPath(thisDir)) {
            return;
        }
        modify(thisDir, context);
    }

    private void handleCreate(Path thisDir, Path context) {
        if (context == null || isTmp(context)) {
            return;
        }
        if (thisDir.equals(root) && Files.isDirectory(context)) {
            try {
                watchSupport.registerDirectory(context);
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
            for (File file : files) {
                modify(context, file.toPath());
            }
        }
    }

    private void handleDelete(Path thisDir, Path context) {
        if (context == null || isTmp(context)) {
            return;
        }
        if (thisDir.equals(root)) {
            String name = context.getFileName().toString();
            Path candidate = root.resolve(name);
            if (Files.isDirectory(candidate)) {
                return;
            }
            removeAction(name);
            AtomicReference<java.nio.file.WatchKey> toRemove = new AtomicReference<>();
            watchSupport.context().watchKeys().forEach((key, path) -> {
                if (path.getFileName().toString().equals(name)) {
                    key.cancel();
                    toRemove.set(key);
                }
            });
            if (toRemove.get() != null) {
                watchSupport.context().watchKeys().remove(toRemove.get());
            }
            return;
        }
        if (!thisDir.equals(root) && !normalPath(thisDir)) {
            removeAction(thisDir.getFileName().toString());
        }
    }

    private void reconcile() {
        Set<String> existed = existedMetaActions.keySet().stream()
                .filter(actionKey -> actionKey.startsWith("local::"))
                .map(actionKey -> actionKey.split("::")[1])
                .collect(Collectors.toSet());
        Set<String> currentDirs = new HashSet<>();
        try (Stream<Path> stream = Files.list(root).filter(Files::isDirectory)) {
            stream.forEach(path -> {
                String name = path.getFileName().toString();
                currentDirs.add(name);
                boolean contains = existed.contains(name);
                boolean normal = normalPath(path);
                if (contains && !normal) {
                    removeAction(name);
                }
                if (!contains) {
                    boolean alreadyWatching = watchSupport.isWatching(path);
                    if (!alreadyWatching) {
                        try {
                            watchSupport.registerDirectory(path);
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
            log.error("目录无法读取: {}", root);
            return;
        }
        for (String existedName : existed) {
            if (!currentDirs.contains(existedName)) {
                removeAction(existedName);
            }
        }
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
        if (existedMetaActions.containsKey(actionKey)) {
            return;
        }
        if (!addAction(name, thisDir)) {
            removeAction(name);
        }
    }

    private void handleMetaModify(Path thisDir) {
        String name = thisDir.getFileName().toString();
        if (!addAction(name, thisDir)) {
            removeAction(name);
        }
    }

    private boolean addAction(String name, Path dir) {
        File program = null;
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                if (isTmp(path)) {
                    continue;
                }
                if (path.getFileName().toString().startsWith("run.")) {
                    program = path.toFile();
                }
            }
        } catch (Exception e) {
            log.error("添加 action 失败", e);
            return false;
        }
        MetaActionInfo info;
        try {
            info = JSON.parseObject(Files.readString(dir.resolve("desc.json"), StandardCharsets.UTF_8), MetaActionInfo.class);
        } catch (Exception e) {
            log.error("desc.json 加载失败: {}", dir);
            return false;
        }
        String actionKey = "local::" + name;
        existedMetaActions.put(actionKey, info);
        server.addTool(buildAsyncToolSpecification(info, program, actionKey, name)).subscribe();
        return true;
    }

    private void removeAction(String name) {
        existedMetaActions.remove("local::" + name);
        server.removeTool(name).subscribe();
    }

    private boolean normalPath(Path path) {
        File[] files = loadFiles(path);
        if (files == null || files.length < 2) {
            return false;
        }
        boolean desc = false;
        int run = 0;
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.endsWith(".tmp")) {
                continue;
            }
            if (fileName.equals("desc.json")) {
                desc = true;
            }
            if (fileName.startsWith("run.")) {
                run++;
            }
        }
        return run == 1 && desc;
    }

    private File[] loadFiles(Path path) {
        if (!Files.isDirectory(path)) {
            return null;
        }
        return path.toFile().listFiles();
    }

    private McpStatelessServerFeatures.AsyncToolSpecification buildAsyncToolSpecification(MetaActionInfo info, File program, String actionKey, String name) {
        Map<String, Object> additional = Map.of(
                "pre", info.getPreActions(),
                "post", info.getPostActions(),
                "strict_pre", info.getStrictDependencies(),
                "io", info.getIo()
        );
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
                .callHandler(buildToolHandler(program, info.getLauncher()))
                .build();
    }

    private BiFunction<McpTransportContext, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>> buildToolHandler(File program, String launcher) {
        return (mcpTransportContext, callToolRequest) -> {
            Map<String, Object> arguments = callToolRequest.arguments();
            if (arguments == null) {
                arguments = Map.of();
            }
            String[] commands = commandExecutionService.buildFileExecutionCommands(launcher, arguments, program.getAbsolutePath());
            if (commands == null) {
                return Mono.just(McpSchema.CallToolResult.builder()
                        .addTextContent("未知文件类型: " + program.getName())
                        .isError(true)
                        .build());
            }
            return Mono.fromCallable(() -> {
                CommandExecutionService.Result execResult = commandExecutionService.exec(commands);
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
    public void close() {
        try {
            watchSupport.close();
        } catch (IOException ignored) {
        }
        server.close();
    }
}
