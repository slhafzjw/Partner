package experimental;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public final class InProcessMcpTransport implements McpClientTransport, McpStatelessServerTransport {

    // 每个 transport 只处理一条 inbound 流
    private final Sinks.Many<McpSchema.JSONRPCMessage> inbound =
            Sinks.many().unicast().onBackpressureBuffer();

    private final AtomicBoolean clientConnected = new AtomicBoolean(false);
    private final AtomicBoolean serverConnected = new AtomicBoolean(false);

    /**
     * 对端
     */
    private volatile InProcessMcpTransport peer;

    private volatile McpStatelessServerHandler serverHandler;

    public record Pair(InProcessMcpTransport clientSide, InProcessMcpTransport serverSide) {
    }

    public static Pair pair() {
        InProcessMcpTransport client = new InProcessMcpTransport();
        InProcessMcpTransport server = new InProcessMcpTransport();

        client.peer = server;
        server.peer = client;

        return new Pair(client, server);
    }

    /* ======================================================
     * Internal receive: peer.sendMessage -> this.receive
     * ====================================================== */
    private void receive(McpSchema.JSONRPCMessage message) {
        if (inbound.tryEmitNext(message).isFailure()) {
            throw new RuntimeException("Failed to receive message: " + message);
        }
    }

    /* ======================================================
     * Client → Server sendMessage
     * ====================================================== */
    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        InProcessMcpTransport p = this.peer;
        if (p == null) {
            return Mono.error(new IllegalStateException("Transport is not linked"));
        }
        return Mono.fromRunnable(() -> p.receive(message));
    }

    /* ======================================================
     * Client connect(handler) 处理 server → client 消息
     * ====================================================== */
    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        if (!clientConnected.compareAndSet(false, true)) {
            return Mono.error(new IllegalStateException("Client already connected"));
        }

        return inbound.asFlux()
                .concatMap(msg ->
                        handler.apply(Mono.just(msg))
                                // handler may emit response message → send back to server
                                .flatMap(resp -> resp != null ? sendMessage(resp) : Mono.empty())
                )
                .doFinally(sig -> clientConnected.set(false))
                .then();
    }

    @Override
    public void setExceptionHandler(Consumer<Throwable> handler) {
        McpClientTransport.super.setExceptionHandler(handler);
    }

    /* ======================================================
     * Server: bind stateless handler = process client → server inbound
     * ====================================================== */
    @Override
    public void setMcpHandler(McpStatelessServerHandler handler) {
        this.serverHandler = handler;

        if (!serverConnected.compareAndSet(false, true)) {
            throw new IllegalStateException("Server already connected");
        }

        // 订阅 client → server 消息
        inbound.asFlux()
                .concatMap(this::handleServerMessage)
                .doFinally(sig -> serverConnected.set(false))
                .subscribe();
    }

    /**
     * Server 端处理 JSONRPCMessage
     */
    private Mono<Void> handleServerMessage(McpSchema.JSONRPCMessage msg) {
        // 创建 transport context（简单实现即可）
        McpTransportContext ctx = key -> null;

        if (msg instanceof McpSchema.JSONRPCRequest req) {
            return serverHandler.handleRequest(ctx, req)
                    .flatMap(this::sendMessage);
        }

        if (msg instanceof McpSchema.JSONRPCNotification noti) {
            return serverHandler.handleNotification(ctx, noti);
        }

        return Mono.empty();
    }

    /* ======================================================
     * other boilerplate
     * ====================================================== */

    @Override
    public void close() {
        McpClientTransport.super.close();
    }

    @Override
    public Mono<Void> closeGracefully() {
        inbound.tryEmitComplete();
        clientConnected.set(false);
        serverConnected.set(false);
        return Mono.empty();
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
        return McpJsonMapper.getDefault().convertValue(data, typeRef);
    }

    @Override
    public List<String> protocolVersions() {
        return McpClientTransport.super.protocolVersions();
    }
}

class InProcessMcpTransportTest {
    @Test
    void inProcessMcpTransportTest() {
        InProcessMcpTransport.Pair pair = InProcessMcpTransport.pair();
        InProcessMcpTransport clientSide = pair.clientSide();
        InProcessMcpTransport serverSide = pair.serverSide();
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
