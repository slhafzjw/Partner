package work.slhaf.partner.core.action.runner.mcp;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import work.slhaf.partner.core.action.runner.policy.RunnerExecutionPolicy;

import java.net.URI;
import java.net.http.HttpRequest;

public class McpTransportFactory {

    public McpClientTransport create(McpTransportConfig config, RunnerExecutionPolicy policy) {
        return switch (config) {
            case McpTransportConfig.Stdio stdio -> {
                ServerParameters serverParameters = ServerParameters.builder(stdio.command())
                        .env(stdio.env())
                        .args(stdio.args())
                        .build();
                yield new StdioClientTransport(serverParameters, McpJsonMapper.getDefault());
            }
            case McpTransportConfig.Http http -> {
                McpSyncHttpClientRequestCustomizer customizer = new McpSyncHttpClientRequestCustomizer() {
                    @Override
                    public void customize(HttpRequest.Builder builder, String method, URI endpoint, String body, McpTransportContext context) {
                        http.headers().forEach(builder::setHeader);
                    }
                };
                yield HttpClientSseClientTransport.builder(http.baseUri())
                        .httpRequestCustomizer(customizer)
                        .sseEndpoint(http.endpoint())
                        .build();
            }
            case McpTransportConfig.InProcess inProcess -> inProcess.clientTransport();
        };
    }
}
