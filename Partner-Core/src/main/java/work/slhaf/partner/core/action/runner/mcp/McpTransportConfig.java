package work.slhaf.partner.core.action.runner.mcp;

import work.slhaf.partner.common.mcp.InProcessMcpTransport;

import java.util.List;
import java.util.Map;

public sealed interface McpTransportConfig permits McpTransportConfig.Http, McpTransportConfig.Stdio, McpTransportConfig.InProcess {

    int timeout();

    record Http(int timeout, String baseUri, String endpoint,
                Map<String, String> headers) implements McpTransportConfig {
    }

    record Stdio(int timeout, String command, Map<String, String> env,
                 List<String> args) implements McpTransportConfig {
    }

    record InProcess(int timeout, InProcessMcpTransport clientTransport) implements McpTransportConfig {
    }
}
