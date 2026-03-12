package work.slhaf.partner.core.action.runner;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import work.slhaf.partner.core.action.entity.MetaAction;

import java.util.List;
import java.util.stream.Collectors;

class McpActionExecutor {

    private final McpClientRegistry mcpClientRegistry;

    McpActionExecutor(McpClientRegistry mcpClientRegistry) {
        this.mcpClientRegistry = mcpClientRegistry;
    }

    RunnerClient.RunnerResponse run(MetaAction metaAction) {
        RunnerClient.RunnerResponse response = new RunnerClient.RunnerResponse();
        McpSyncClient mcpClient = mcpClientRegistry.get(metaAction.getLocation());
        if (mcpClient == null) {
            response.setOk(false);
            response.setData("MCP client not found: " + metaAction.getLocation());
            return response;
        }
        McpSchema.CallToolRequest callToolRequest = McpSchema.CallToolRequest.builder()
                .name(metaAction.getName())
                .arguments(metaAction.getParams())
                .build();
        McpSchema.CallToolResult callToolResult = mcpClient.callTool(callToolRequest);
        Boolean error = callToolResult.isError();
        response.setOk(error == null || !error);
        response.setData(extractResponseData(callToolResult));
        return response;
    }

    private String extractResponseData(McpSchema.CallToolResult callToolResult) {
        Object structuredContent = callToolResult.structuredContent();
        if (structuredContent != null) {
            return String.valueOf(structuredContent);
        }

        List<McpSchema.Content> contents = callToolResult.content();
        if (contents != null && !contents.isEmpty()) {
            String contentSummary = contents.stream()
                    .map(this::renderContent)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n"));
            if (!contentSummary.isBlank()) {
                return contentSummary;
            }
        }

        return callToolResult.toString();
    }

    private String renderContent(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        return String.valueOf(content);
    }
}
