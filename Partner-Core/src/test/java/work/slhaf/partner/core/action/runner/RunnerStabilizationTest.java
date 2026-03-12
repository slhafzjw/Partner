package work.slhaf.partner.core.action.runner;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class RunnerStabilizationTest {

    @Test
    void actionSerializerUsesNormalizedCodeType(@TempDir Path tempDir) throws Exception {
        ActionSerializer serializer = new ActionSerializer(tempDir.toString(), tempDir.toString());
        String builtPath = serializer.buildTmpPath("demo", "py");
        Assertions.assertTrue(builtPath.endsWith(".py"));

        MetaAction metaAction = new MetaAction("demo", false, MetaAction.Type.ORIGIN, builtPath);
        serializer.tmpSerialize(metaAction, "print('ok')", ".py");

        Assertions.assertTrue(Files.exists(Path.of(builtPath)));
        Assertions.assertEquals("print('ok')", Files.readString(Path.of(builtPath)));
        Assertions.assertThrows(Exception.class, () -> serializer.tmpSerialize(metaAction, "print('bad')", ".sh"));
    }

    @Test
    void mcpTransportConfigHasValueEquality() {
        McpTransportConfig.Stdio left = new McpTransportConfig.Stdio(30, "npx", Map.of("A", "1"), List.of("-y", "demo"));
        McpTransportConfig.Stdio right = new McpTransportConfig.Stdio(30, "npx", Map.of("A", "1"), List.of("-y", "demo"));

        Assertions.assertEquals(left, right);
        Assertions.assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    void mcpConfigWatcherReadParamsAcceptsTimeout(@TempDir Path tempDir) throws Exception {
        ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        McpConfigWatcher watcher = new McpConfigWatcher(
                tempDir,
                existedMetaActions,
                new McpClientRegistry(),
                new McpTransportFactory(),
                new McpMetaRegistry(existedMetaActions),
                executor
        );
        try {
            Method readParams = McpConfigWatcher.class.getDeclaredMethod("readParams", cn.hutool.json.JSONObject.class);
            readParams.setAccessible(true);

            cn.hutool.json.JSONObject stdioJson = cn.hutool.json.JSONUtil.parseObj("""
                    {
                      "command": "npx",
                      "args": ["-y", "demo"],
                      "env": {},
                      "timeout": 45
                    }
                    """);
            Object stdioConfig = readParams.invoke(watcher, stdioJson);

            Assertions.assertInstanceOf(McpTransportConfig.Stdio.class, stdioConfig);
            Assertions.assertEquals(45, ((McpTransportConfig.Stdio) stdioConfig).timeout());
        } finally {
            watcher.close();
            executor.shutdownNow();
        }
    }

    @Test
    void localRunnerClientCloseIsIdempotent(@TempDir Path tempDir) {
        ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        LocalRunnerClient client = new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());
        try {
            client.close();
            client.close();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void mcpActionExecutorUsesStructuredContentThenTextContent() {
        McpClientRegistry registry = new McpClientRegistry();
        McpSyncClient client = Mockito.mock(McpSyncClient.class);
        registry.register("demo", client);

        McpActionExecutor executor = new McpActionExecutor(registry);
        MetaAction metaAction = new MetaAction("tool", false, MetaAction.Type.MCP, "demo");

        Mockito.when(client.callTool(Mockito.any())).thenReturn(
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("hello")), null, null, Map.of())
        );
        RunnerClient.RunnerResponse textResponse = executor.run(metaAction);
        Assertions.assertTrue(textResponse.isOk());
        Assertions.assertEquals("hello", textResponse.getData());

        Mockito.when(client.callTool(Mockito.any())).thenReturn(
                new McpSchema.CallToolResult(List.of(), Boolean.FALSE, Map.of("k", "v"), Map.of())
        );
        RunnerClient.RunnerResponse structuredResponse = executor.run(metaAction);
        Assertions.assertTrue(structuredResponse.isOk());
        Assertions.assertEquals("{k=v}", structuredResponse.getData());
    }

    @Test
    void mcpMetaRegistryFallsBackToOriginalToolMetaAfterDescRemoval(@TempDir Path tempDir) throws Exception {
        ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
        McpMetaRegistry registry = new McpMetaRegistry(existedMetaActions);
        try {
            McpSchema.Tool tool = McpSchema.Tool.builder()
                    .name("tool")
                    .description("tool description")
                    .inputSchema(McpJsonMapper.getDefault(), "{\"type\":\"object\",\"properties\":{}}")
                    .outputSchema(Map.of("type", "string"))
                    .meta(Map.of("io", true, "pre", List.of("pre"), "post", List.of("post"), "strict", true, "tag", List.of("tag")))
                    .build();

            MetaActionInfo baseInfo = registry.buildMetaActionInfo("demo", tool);
            existedMetaActions.put("demo::tool", baseInfo);

            Path descFile = tempDir.resolve("demo::tool.desc.json");
            Files.writeString(descFile, """
                    {
                      "io": false,
                      "params": {},
                      "description": "desc override",
                      "tags": ["desc"],
                      "preActions": [],
                      "postActions": [],
                      "strictDependencies": false,
                      "responseSchema": {}
                    }
                    """);

            Assertions.assertTrue(registry.addOrUpdate(descFile));
            Assertions.assertEquals("desc override", existedMetaActions.get("demo::tool").getDescription());

            registry.remove(descFile);
            MetaActionInfo restoredInfo = existedMetaActions.get("demo::tool");
            Assertions.assertEquals("tool description", restoredInfo.getDescription());
            Assertions.assertTrue(restoredInfo.isIo());
            Assertions.assertEquals(List.of("tag"), restoredInfo.getTags());
        } finally {
            registry.close();
        }
    }
}
