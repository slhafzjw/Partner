package work.slhaf.partner.core.action.runner.mcp;

import cn.hutool.json.JSONUtil;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.runner.LocalRunnerClient;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicy;
import work.slhaf.partner.core.action.runner.policy.RunnerExecutionPolicyListener;
import work.slhaf.partner.framework.agent.common.support.DirectoryWatchSupport;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
public class McpConfigWatcher implements AutoCloseable, RunnerExecutionPolicyListener {

    private final Path root;
    private final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions;
    private final McpClientRegistry mcpClientRegistry;
    private final McpTransportFactory mcpTransportFactory;
    private final McpMetaRegistry mcpMetaRegistry;
    private final DirectoryWatchSupport watchSupport;
    private final Map<File, McpConfigFileRecord> mcpConfigFileCache = new HashMap<>();

    public McpConfigWatcher(Path root,
                            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions,
                            McpClientRegistry mcpClientRegistry,
                            McpTransportFactory mcpTransportFactory,
                            McpMetaRegistry mcpMetaRegistry,
                            ExecutorService executor) throws IOException {
        this.root = root;
        this.existedMetaActions = existedMetaActions;
        this.mcpClientRegistry = mcpClientRegistry;
        this.mcpTransportFactory = mcpTransportFactory;
        this.mcpMetaRegistry = mcpMetaRegistry;
        this.watchSupport = new DirectoryWatchSupport(new DirectoryWatchSupport.Context(root), executor, 0, this::loadInitial)
                .onCreate(this::handleCreate)
                .onModify((thisDir, context) -> checkAndReload(true))
                .onDelete(this::handleDelete)
                .onOverflow((thisDir, context) -> checkAndReload(false));
    }

    public void start() {
        watchSupport.start();
        log.info("CommonMcp 文件监听注册完毕");
    }

    private void loadInitial() {
        File[] files = loadFiles(root);
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!normalFile(file)) {
                continue;
            }
            loadAndRegisterMcpClientsFromFile(file);
        }
    }

    private void handleCreate(Path thisDir, Path context) {
        if (context == null) {
            return;
        }
        File file = context.toFile();
        if (!normalFile(file)) {
            return;
        }
        loadAndRegisterMcpClientsFromFile(file);
    }

    private void handleDelete(Path thisDir, Path context) {
        if (context == null) {
            return;
        }
        File file = context.toFile();
        if (!file.getName().endsWith(".json")) {
            return;
        }
        McpConfigFileRecord fileRecord = mcpConfigFileCache.remove(file);
        if (fileRecord == null) {
            return;
        }
        for (String clientId : fileRecord.paramsCacheMap().keySet()) {
            McpSyncClient client = mcpClientRegistry.detach(clientId);
            if (client == null) {
                continue;
            }
            for (McpSchema.Tool tool : client.listTools().tools()) {
                existedMetaActions.remove(clientId + "::" + tool.name());
            }
            client.close();
        }
    }

    private boolean normalFile(File file) {
        return file.exists() && file.isFile() && file.getName().endsWith(".json");
    }

    private void registerMcpClient(String id, McpTransportConfig transportConfig) {
        McpSyncClient client = McpClient.sync(mcpTransportFactory.create(transportConfig))
                .requestTimeout(Duration.ofSeconds(transportConfig.timeout()))
                .clientInfo(new McpSchema.Implementation(id, "PARTNER"))
                .build();
        try {
            for (McpSchema.Tool tool : client.listTools().tools()) {
                existedMetaActions.put(id + "::" + tool.name(), mcpMetaRegistry.buildMetaActionInfo(id, tool));
            }
            mcpClientRegistry.register(id, client);
        } catch (Exception e) {
            log.warn("[{}] MCP client init failed, skipped (probably non-stdio-safe)", id, e);
            client.close();
        }
    }

    private cn.hutool.json.JSONObject readJson(File file) {
        try {
            return JSONUtil.readJSONObject(file, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
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
    private McpTransportConfig readParams(cn.hutool.json.JSONObject mcp) {
        Set<String> keys = mcp.keySet();
        int timeout = mcp.getInt("timeout", 30);

        if (matchesKeys(keys, Set.of("command", "args", "env"), Set.of("timeout"))) {
            String command = mcp.getStr("command");
            Map<String, String> env = mcp.getBean("env", Map.class);
            java.util.List<String> args = mcp.getBeanList("args", String.class);
            if (command == null || env == null || args == null) {
                return null;
            }
            return new McpTransportConfig.Stdio(timeout, command, env, args);
        }
        if (matchesKeys(keys, Set.of("uri", "endpoint", "headers"), Set.of("timeout"))) {
            String uri = mcp.getStr("uri");
            String endpoint = mcp.getStr("endpoint");
            Map<String, String> headers = mcp.getBean("headers", Map.class);
            if (uri == null || endpoint == null || headers == null) {
                return null;
            }
            return new McpTransportConfig.Http(timeout, uri, endpoint, headers);
        }
        if (matchesKeys(keys, Set.of("url"), Set.of("timeout"))) {
            String url = mcp.getStr("url");
            if (url == null) {
                return null;
            }
            return new McpTransportConfig.Http(timeout, url, "", Map.of());
        }
        return null;
    }

    private boolean matchesKeys(Set<String> actualKeys, Set<String> requiredKeys, Set<String> optionalKeys) {
        if (!actualKeys.containsAll(requiredKeys)) {
            return false;
        }
        Set<String> allowedKeys = new HashSet<>(requiredKeys);
        allowedKeys.addAll(optionalKeys);
        return allowedKeys.containsAll(actualKeys);
    }

    private void checkAndReload(boolean trustCache) {
        HashMap<String, McpTransportConfig> changedMap = new HashMap<>();
        HashSet<String> existingMcpIdSet = new HashSet<>();

        File[] files = loadFiles(root);
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!normalFile(file)) {
                continue;
            }
            McpConfigFileRecord fileRecord = mcpConfigFileCache.get(file);
            boolean fileRecordExists = fileRecord != null;
            if (fileRecordExists && !fileChanged(file, fileRecord) && trustCache) {
                existingMcpIdSet.addAll(fileRecord.paramsCacheMap().keySet());
                continue;
            }

            cn.hutool.json.JSONObject mcpConfigJson = readJson(file);
            if (mcpConfigJson == null) {
                if (fileRecordExists) {
                    existingMcpIdSet.addAll(fileRecord.paramsCacheMap().keySet());
                }
                continue;
            }

            McpConfigFileRecord newFileRecord = new McpConfigFileRecord(file.lastModified(), file.length(), new HashMap<>());
            for (String id : mcpConfigJson.keySet()) {
                cn.hutool.json.JSONObject mcp = readMcp(mcpConfigJson, id);
                if (mcp == null) {
                    continue;
                }
                McpTransportConfig params = readParams(mcp);
                if (params == null) {
                    continue;
                }
                existingMcpIdSet.add(id);
                newFileRecord.paramsCacheMap().put(id, params);
                if (fileRecordExists) {
                    McpTransportConfig paramsCache = fileRecord.paramsCacheMap().get(id);
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

    private void updateMcpClients(HashMap<String, McpTransportConfig> changedMap, HashSet<String> existingMcpIdSet) {
        changedMap.forEach(this::registerMcpClient);
        for (String clientId : mcpClientRegistry.listIds()) {
            if (clientId.equals(LocalRunnerClient.MCP_NAME_DESC) || clientId.equals(LocalRunnerClient.MCP_NAME_DYNAMIC)) {
                continue;
            }
            if (!existingMcpIdSet.contains(clientId)) {
                mcpClientRegistry.remove(clientId);
            }
        }
        existedMetaActions.keySet().removeIf(actionKey -> {
            String serverId = actionKey.split("::")[0];
            return !serverId.equals("local")
                    && !serverId.equals(LocalRunnerClient.MCP_NAME_DESC)
                    && !serverId.equals(LocalRunnerClient.MCP_NAME_DYNAMIC)
                    && !existingMcpIdSet.contains(serverId);
        });
    }

    private boolean fileChanged(File file, McpConfigFileRecord fileRecord) {
        return fileRecord.lastModified() != file.lastModified() || fileRecord.length() != file.length();
    }

    private void loadAndRegisterMcpClientsFromFile(File file) {
        cn.hutool.json.JSONObject mcpConfigJson = readJson(file);
        if (mcpConfigJson == null) {
            return;
        }
        McpConfigFileRecord newFileRecord = new McpConfigFileRecord(file.lastModified(), file.length());
        for (String id : mcpConfigJson.keySet()) {
            cn.hutool.json.JSONObject mcp = readMcp(mcpConfigJson, id);
            if (mcp == null) {
                continue;
            }
            McpTransportConfig params = readParams(mcp);
            if (params == null) {
                continue;
            }
            registerMcpClient(id, params);
            newFileRecord.paramsCacheMap().put(id, params);
        }
        mcpConfigFileCache.put(file, newFileRecord);
    }

    private File[] loadFiles(Path path) {
        if (!path.toFile().isDirectory()) {
            return null;
        }
        return path.toFile().listFiles();
    }

    @Override
    public void close() throws Exception {
        watchSupport.close();
    }

    @Override
    public void onPolicyChanged(@NotNull ExecutionPolicy policy) {
        checkAndReload(false);
    }

    private record McpConfigFileRecord(long lastModified, long length, Map<String, McpTransportConfig> paramsCacheMap) {
        private McpConfigFileRecord(long lastModified, long length) {
            this(lastModified, length, new HashMap<>());
        }
    }
}
