package work.slhaf.partner.core.action.runner.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import javassist.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

@Slf4j
public class McpMetaRegistry implements AutoCloseable {

    private final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions;
    private final ConcurrentHashMap<String, String> descCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MetaActionInfo> descInfoCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MetaActionInfo> originalInfoCache = new ConcurrentHashMap<>();
    private final McpStatelessAsyncServer descServer;
    private final InProcessMcpTransport clientTransport;

    public McpMetaRegistry(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions) {
        this.existedMetaActions = existedMetaActions;
        InProcessMcpTransport.Pair pair = InProcessMcpTransport.pair();
        this.clientTransport = pair.clientSide();
        McpSchema.ServerCapabilities serverCapabilities = McpSchema.ServerCapabilities.builder()
                .resources(true, true)
                .build();
        this.descServer = McpServer.async(pair.serverSide())
                .capabilities(serverCapabilities)
                .jsonMapper(McpJsonMapper.getDefault())
                .build();
    }

    public McpTransportConfig.InProcess clientConfig(String serverName, int timeout) {
        return new McpTransportConfig.InProcess(timeout, clientTransport);
    }

    public void loadDirectory(Path root) {
        File[] files = loadFiles(root);
        if (files == null) {
            return;
        }
        for (File file : files) {
            addOrUpdate(file);
        }
    }

    public boolean addOrUpdate(Path path) {
        return addOrUpdate(path.toFile());
    }

    public boolean addOrUpdate(File file) {
        String name = file.getName();
        if (!isValidDescFile(name)) {
            return false;
        }
        try {
            MetaActionInfo info = JSON.parseObject(Files.readString(file.toPath(), StandardCharsets.UTF_8), MetaActionInfo.class);
            String uri = file.toPath().toUri().toString();
            descCache.put(uri, JSONObject.toJSONString(info));
            String actionKey = name.replace(".desc.json", "");
            descInfoCache.put(actionKey, copyMetaActionInfo(info));
            descServer.addResource(buildAsyncResourceSpecification(name, uri)).block();
            if (existedMetaActions.containsKey(actionKey)) {
                existedMetaActions.put(actionKey, mergeWithOriginal(actionKey, info));
            }
            return true;
        } catch (Exception e) {
            log.error("desc.json 解析失败: {}", file.getAbsolutePath());
            return false;
        }
    }

    public void remove(Path path) {
        String uri = path.toUri().toString();
        String actionKey = path.getFileName().toString().replace(".desc.json", "");
        descCache.remove(uri);
        descInfoCache.remove(actionKey);
        descServer.removeResource(uri).block();
        MetaActionInfo originalInfo = originalInfoCache.get(actionKey);
        if (originalInfo != null) {
            existedMetaActions.put(actionKey, copyMetaActionInfo(originalInfo));
            return;
        }
        MetaActionInfo info = existedMetaActions.get(actionKey);
        if (info != null) {
            existedMetaActions.put(actionKey, resetMetaActionInfo(info));
        }
    }

    public void reconcile(Path root) {
        File[] files = loadFiles(root);
        if (files == null) {
            return;
        }
        Set<String> currentUris = ConcurrentHashMap.newKeySet();
        for (File file : files) {
            if (!isValidDescFile(file.getName())) {
                continue;
            }
            currentUris.add(file.toURI().toString());
            if (!addOrUpdate(file)) {
                remove(file.toPath());
            }
        }
        List<String> serverUris = descServer.listResources()
                .map(McpSchema.Resource::uri)
                .collectList()
                .block();
        if (serverUris == null) {
            log.error("无法获取 DescMcpServer 持有的资源列表");
            return;
        }
        for (String uri : serverUris) {
            if (!currentUris.contains(uri)) {
                remove(Path.of(java.net.URI.create(uri)));
            }
        }
    }

    public MetaActionInfo buildMetaActionInfo(String serverId, McpSchema.Tool tool) {
        String actionKey = serverId + "::" + tool.name();
        MetaActionInfo baseInfo = buildToolMetaActionInfo(tool);
        originalInfoCache.put(actionKey, copyMetaActionInfo(baseInfo));
        MetaActionInfo override = descInfoCache.get(actionKey);
        return override == null ? baseInfo : mergeWithOriginal(actionKey, override);
    }

    private McpStatelessServerFeatures.AsyncResourceSpecification buildAsyncResourceSpecification(String name, String uri) {
        McpSchema.Resource resource = McpSchema.Resource.builder()
                .name(name)
                .title(name)
                .description("Action descriptor for " + name)
                .mimeType("application/json")
                .uri(uri)
                .build();
        BiFunction<McpTransportContext, McpSchema.ReadResourceRequest, Mono<McpSchema.ReadResourceResult>> readHandler = (context, request) -> {
            String result = descCache.get(request.uri());
            if (result == null) {
                return Mono.error(new NotFoundException("未找到 Resource: " + request.uri()));
            }
            return Mono.just(new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(request.uri(), "application/json", result)
            )));
        };
        return new McpStatelessServerFeatures.AsyncResourceSpecification(resource, readHandler);
    }

    public boolean isValidDescFile(String fileName) {
        return fileName.endsWith(".desc.json") && fileName.contains("::");
    }

    private File[] loadFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return null;
        }
        return root.toFile().listFiles();
    }

    private MetaActionInfo resetMetaActionInfo(@NotNull MetaActionInfo info) {
        return new MetaActionInfo(
                false,
                info.getLauncher(),
                copyParams(info.getParams()),
                info.getDescription(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                false,
                copyResponseSchema(info.getResponseSchema())
        );
    }

    @Override
    public void close() {
        descServer.close();
    }

    private MetaActionInfo buildToolMetaActionInfo(McpSchema.Tool tool) {
        Map<String, Object> outputSchema = tool.outputSchema();
        boolean io = false;
        Set<String> preActions = new LinkedHashSet<>();
        Set<String> postActions = new LinkedHashSet<>();
        boolean strictDependencies = false;
        Set<String> tags = new LinkedHashSet<>();
        Map<String, Object> meta = tool.meta();
        if (meta != null) {
            JSONObject metaJson = JSONObject.from(meta);
            io = Boolean.TRUE.equals(metaJson.getBoolean("io"));
            preActions = toOrderedSet(metaJson.getList("pre", String.class));
            postActions = toOrderedSet(metaJson.getList("post", String.class));
            strictDependencies = Boolean.TRUE.equals(metaJson.getBoolean("strict"));
            tags = toOrderedSet(metaJson.getList("tag", String.class));
        }
        return new MetaActionInfo(
                io,
                null,
                copyParams(tool.inputSchema().properties()),
                tool.description(),
                tags,
                preActions,
                postActions,
                strictDependencies,
                outputSchema == null ? JSONObject.of() : JSONObject.from(outputSchema)
        );
    }

    private MetaActionInfo mergeWithOriginal(String actionKey, MetaActionInfo override) {
        MetaActionInfo original = originalInfoCache.get(actionKey);
        return override == null ? copyMetaActionInfo(original) : copyMetaActionInfo(override);
    }

    private MetaActionInfo copyMetaActionInfo(MetaActionInfo source) {
        if (source == null) {
            return null;
        }
        return new MetaActionInfo(
                source.getIo(),
                source.getLauncher(),
                copyParams(source.getParams()),
                source.getDescription(),
                toOrderedSet(source.getTags()),
                toOrderedSet(source.getPreActions()),
                toOrderedSet(source.getPostActions()),
                source.getStrictDependencies(),
                copyResponseSchema(source.getResponseSchema())
        );
    }

    private <T> LinkedHashSet<T> toOrderedSet(Collection<T> source) {
        return source == null ? new LinkedHashSet<>() : new LinkedHashSet<>(source);
    }

    private Map<String, String> copyParams(Map<String, ?> params) {
        if (params == null) {
            return null;
        }
        Map<String, String> copied = new LinkedHashMap<>();
        params.forEach((key, value) -> copied.put(key, value == null ? null : String.valueOf(value)));
        return copied;
    }

    private JSONObject copyResponseSchema(JSONObject responseSchema) {
        return responseSchema == null ? JSONObject.of() : JSONObject.from(responseSchema);
    }
}
