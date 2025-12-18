package work.slhaf.partner.core.action.runner;

import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.action.entity.McpData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class RunnerClientTest {

    @Test
    void httpMcpClientTest() {
        TestRunnerClient testClient = new TestRunnerClient();
        RunnerClient.HttpMcpServerParams params = new RunnerClient.HttpMcpServerParams(20, "https://dashscope.aliyuncs.com", "/api/v1/mcps/WebSearch/sse", Map.of("Authorization", "Bearer sk-xxx"));
        testClient.registerMcpClient("test", params);
        McpSyncClient client = testClient.mcpClients.values().stream().toList().getFirst();
        List<McpSchema.Tool> tools = client.listTools().tools();
        System.out.println(tools);
        McpSchema.CallToolResult query = client.callTool(McpSchema.CallToolRequest.builder().name(tools.getFirst().name()).arguments(Map.of("query", "123")).build());
        for (McpSchema.Content content : query.content()) {
            System.out.println("\r\n---\r\n");
            System.out.println(content);
        }
    }

    @Test
    void stdioMcpClientTest() {
        TestRunnerClient testClient = new TestRunnerClient();
        RunnerClient.StdioMcpServerParams params = new RunnerClient.StdioMcpServerParams(20, "uvx", Map.of("http_proxy", "http://127.0.0.1:7897", "https_proxy", "http://127.0.0.1:7897"), List.of("mcp-server-fetch"));
        testClient.registerMcpClient("test", params);
        McpSyncClient client = testClient.mcpClients.values().stream().toList().getFirst();
        List<McpSchema.Tool> tools = client.listTools().tools();
        System.out.println(tools);
        McpSchema.CallToolResult query = client.callTool(McpSchema.CallToolRequest.builder().name(tools.getFirst().name()).arguments(Map.of("url", "https://gitea.slhaf.work")).build());
        System.out.println(query.toString());
    }

    @Test
    void inProcessMcpTransportTest() {
        RunnerClient.InProcessMcpTransport.Pair pair = RunnerClient.InProcessMcpTransport.pair();
        RunnerClient.InProcessMcpTransport clientSide = pair.clientSide();
        RunnerClient.InProcessMcpTransport serverSide = pair.serverSide();
        McpStatelessSyncServer server = McpServer.sync(serverSide)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();
        server.addTool(McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder().name("111").build()).callHandler((mcpTransportContext, callToolRequest) -> {
                    System.out.println(111);
                    return McpSchema.CallToolResult.builder().addContent(new McpSchema.TextContent("111")).build();
                }).build());
        McpSyncClient client = McpClient.sync(clientSide)
                .build();

        List<McpSchema.Tool> tools = client.listTools().tools();
        McpSchema.Tool tool = tools.getFirst();
        System.out.println(tool.toString());

        McpSchema.CallToolResult callToolResult = client.callTool(McpSchema.CallToolRequest.builder().name(tool.name()).build());
        System.out.println(callToolResult.content().toString());

        client.close();
        server.close();
    }

    private static class TestRunnerClient extends RunnerClient {

        public TestRunnerClient() {
            super(Map.of(), Executors.newVirtualThreadPerTaskExecutor());
        }

        @Override
        protected RunnerResponse doRun(MetaAction metaAction) {
            return null;
        }

        @Override
        public Path buildTmpPath(MetaAction tempAction, String codeType) {
            return null;
        }

        @Override
        public void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException {

        }

        @Override
        public void persistSerialize(MetaActionInfo metaActionInfo, McpData mcpData) {

        }

        @Override
        public JSONObject listSysDependencies() {
            return null;
        }
    }
}
