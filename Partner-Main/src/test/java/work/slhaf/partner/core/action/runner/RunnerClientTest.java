package work.slhaf.partner.core.action.runner;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

public class RunnerClientTest {

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

}
