package work.slhaf.partner.core.action.runner.mcp;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicyRegistry;
import work.slhaf.partner.core.action.runner.policy.WrappedLaunchSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class McpTransportFactory {

    public McpClientTransport create(McpTransportConfig config) {
        return switch (config) {
            case McpTransportConfig.Stdio stdio -> {
                List<String> commands = new ArrayList<>();
                commands.add(stdio.command());
                commands.addAll(stdio.args());
                WrappedLaunchSpec wrapped = ExecutionPolicyRegistry.INSTANCE.prepare(commands);
                Map<String, String> env = new HashMap<>(stdio.env());
                env.putAll(wrapped.getEnvironment());
                ServerParameters serverParameters = ServerParameters.builder(wrapped.getCommand())
                        .args(wrapped.getArgs())
                        .env(env)
                        .build();
                yield new StdioClientTransport(serverParameters, McpJsonMapper.getDefault());
            }
            case McpTransportConfig.Http http -> {
                McpSyncHttpClientRequestCustomizer customizer = (builder, method, endpoint, body, context) -> http.headers().forEach(builder::setHeader);
                yield HttpClientSseClientTransport.builder(http.baseUri())
                        .httpRequestCustomizer(customizer)
                        .sseEndpoint(http.endpoint())
                        .build();
            }
            case McpTransportConfig.InProcess inProcess -> inProcess.clientTransport();
        };
    }
}
