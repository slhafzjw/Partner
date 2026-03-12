package work.slhaf.partner.core.action.runner;

import cn.hutool.json.JSONUtil;
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
import work.slhaf.partner.common.mcp.InProcessMcpTransport;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

@Slf4j
class McpMetaRegistry implements AutoCloseable {

    private final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions;
    private final ConcurrentHashMap<String, String> descCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MetaActionInfo> descInfoCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MetaActionInfo> originalInfoCache = new ConcurrentHashMap<>();
    private final McpStatelessAsyncServer descServer;
    private final InProcessMcpTransport clientTransport;

    McpMetaRegistry(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions) {
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

    McpTransportConfig.InProcess clientConfig(String serverName, int timeout) {
        return new McpTransportConfig.InProcess(timeout, clientTransport);
    }

    void loadDirectory(Path root) {
        File[] files = loadFiles(root);
        if (files == null) {
            return;
        }
        for (File file : files) {
            addOrUpdate(file);
        }
    }

    boolean addOrUpdate(Path path) {
        return addOrUpdate(path.toFile());
    }

    boolean addOrUpdate(File file) {
        String name = file.getName();
        if (!isValidDescFile(name)) {
            return false;
        }
        try {
            MetaActionInfo info = JSONUtil.readJSONObject(file, StandardCharsets.UTF_8).toBean(MetaActionInfo.class);
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

    void remove(Path path) {
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
            resetMetaActionInfo(info);
        }
    }

    void reconcile(Path root) {
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

    MetaActionInfo buildMetaActionInfo(String serverId, McpSchema.Tool tool) {
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

    boolean isValidDescFile(String fileName) {
        return fileName.endsWith(".desc.json") && fileName.contains("::");
    }

    private File[] loadFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return null;
        }
        return root.toFile().listFiles();
    }

    private void resetMetaActionInfo(@NotNull MetaActionInfo info) {
        info.setIo(false);
        if (info.getTags() != null) {
            info.getTags().clear();
        }
        if (info.getPreActions() != null) {
            info.getPreActions().clear();
        }
        if (info.getPostActions() != null) {
            info.getPostActions().clear();
        }
        info.setStrictDependencies(false);
    }

    @Override
    public void close() {
        descServer.close();
    }

    private MetaActionInfo buildToolMetaActionInfo(McpSchema.Tool tool) {
        MetaActionInfo info = new MetaActionInfo();
        info.setDescription(tool.description());
        Map<String, Object> outputSchema = tool.outputSchema();
        info.setResponseSchema(outputSchema == null ? JSONObject.of() : JSONObject.from(outputSchema));
        info.setParams(tool.inputSchema().properties());

        Map<String, Object> meta = tool.meta();
        if (meta != null) {
            JSONObject metaJson = JSONObject.from(meta);
            info.setIo(Boolean.TRUE.equals(metaJson.getBoolean("io")));
            info.setPreActions(metaJson.getList("pre", String.class));
            info.setPostActions(metaJson.getList("post", String.class));
            info.setStrictDependencies(Boolean.TRUE.equals(metaJson.getBoolean("strict")));
            info.setTags(metaJson.getList("tag", String.class));
        }
        return info;
    }

    private MetaActionInfo mergeWithOriginal(String actionKey, MetaActionInfo override) {
        MetaActionInfo original = originalInfoCache.get(actionKey);
        return override == null ? copyMetaActionInfo(original) : copyMetaActionInfo(override);
    }

    private MetaActionInfo copyMetaActionInfo(MetaActionInfo source) {
        if (source == null) {
            return null;
        }
        MetaActionInfo copy = new MetaActionInfo();
        copy.setIo(source.isIo());
        copy.setParams(source.getParams() == null ? null : new HashMap<>(source.getParams()));
        copy.setDescription(source.getDescription());
        copy.setTags(source.getTags() == null ? new ArrayList<>() : new ArrayList<>(source.getTags()));
        copy.setPreActions(source.getPreActions() == null ? new ArrayList<>() : new ArrayList<>(source.getPreActions()));
        copy.setPostActions(source.getPostActions() == null ? new ArrayList<>() : new ArrayList<>(source.getPostActions()));
        copy.setStrictDependencies(source.isStrictDependencies());
        copy.setResponseSchema(source.getResponseSchema() == null ? JSONObject.of() : JSONObject.from(source.getResponseSchema()));
        return copy;
    }
}
