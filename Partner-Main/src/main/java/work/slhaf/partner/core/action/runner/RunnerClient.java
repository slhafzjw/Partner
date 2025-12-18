package work.slhaf.partner.core.action.runner;

import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import work.slhaf.partner.core.action.entity.McpData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaAction.Result;
import work.slhaf.partner.core.action.entity.MetaAction.ResultStatus;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
public abstract class RunnerClient {

    protected final Map<String, MetaActionInfo> existedMetaActions;
    protected final ExecutorService executor;
    protected final Map<String, McpSyncClient> mcpClients = new HashMap<>();
    protected final Map<String, McpStatelessAsyncServer> localMcpServers = new HashMap<>();

    /**
     * ActionCore 将注入虚拟线程池
     */
    public RunnerClient(Map<String, MetaActionInfo> existedMetaActions, ExecutorService executor) {
        this.existedMetaActions = existedMetaActions;
        this.executor = executor;
        setupShutdownHook();
    }

    protected void setupShutdownHook() {
        this.mcpClients.forEach((id, client) -> {
            client.close();
            log.info("[{}] MCP-Client 已关闭", id);
        });
        this.localMcpServers.forEach((id, server) -> {
            server.close();
            log.info("[{}] MCP-Server 已关闭", id);
        });
    }

    /**
     * 执行行动程序
     */
    public void run(MetaAction metaAction) {
        // 获取已存在行动列表
        Result result = metaAction.getResult();
        if (!result.getStatus().equals(ResultStatus.WAITING)) {
            return;
        }
        RunnerResponse response = doRun(metaAction);
        result.setData(response.getData());
        result.setStatus(response.isOk() ? ResultStatus.SUCCESS : ResultStatus.FAILED);
    }

    protected void registerMcpClient(String id, McpServerParams mcpServerParams) {
        McpClientTransport clientTransport = createTransport(mcpServerParams);
        McpSyncClient client = McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofSeconds(mcpServerParams.timeout))
                .clientInfo(new McpSchema.Implementation(id, "PARTNER"))
                // 行动程序(现 MCP Tool)的描述文本将直接由resources返回
                // 原因: ToolChange 发送的内容侧重调用，缺少可承担描述文本的字段
                //       ResourcesChange 事件传递的 Resource 可以由 Client 读取内容
                //       预计在 Server 侧，收到客户端发送的新的行动程序信息，该信息由客户端处补充后，将其放置在指定位置
                //       并写入描述文件、发起 ResourcesChange 事件
                .resourcesChangeConsumer(resources -> updateExistedMetaActions(id, resources))
                .build();
        mcpClients.put(id, client);
    }

    private void updateExistedMetaActions(String id, List<McpSchema.Resource> resources) {
        synchronized (existedMetaActions) {
            McpSyncClient client = mcpClients.get(id);
            for (McpSchema.Resource resource : resources) {
                McpSchema.ReadResourceResult resourceResult = client.readResource(resource);
                for (McpSchema.ResourceContents resourceContent : resourceResult.contents()) {
                    // 忽略非文本类型，行动描述信息只会以文本形式存在
                    if (resourceContent instanceof McpSchema.TextResourceContents content) {
                        MetaActionInfo metaActionInfo = JSONObject.parseObject(content.text(), MetaActionInfo.class);
                        existedMetaActions.put(id + "::" + metaActionInfo.getKey(), metaActionInfo);
                    }
                }
            }
        }
    }

    private McpClientTransport createTransport(McpServerParams mcpServerParams) {
        return switch (mcpServerParams) {
            case InProcessMcpServerParams params -> {
                InProcessMcpTransport.Pair pair = InProcessMcpTransport.pair();
                createInProcessMcpServer(params.id, pair.serverSide);
                yield pair.clientSide;
            }
            case StdioMcpServerParams params -> {
                ServerParameters serverParameters = ServerParameters.builder(params.command)
                        .env(params.env)
                        .args(params.args)
                        .build();
                yield new StdioClientTransport(serverParameters, McpJsonMapper.getDefault());
            }
            case HttpMcpServerParams params -> {
                McpSyncHttpClientRequestCustomizer customizer = (builder, method, endpoint, body, context) -> {
                    params.headers.forEach(builder::setHeader);
                };
                yield HttpClientSseClientTransport.builder(params.baseUri)
                        .httpRequestCustomizer(customizer)
                        .sseEndpoint(params.endpoint)
                        .build();
            }
        };
    }

    private void createInProcessMcpServer(String id, InProcessMcpTransport serverSide) {
        McpSchema.ServerCapabilities serverCapabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .resources(true, true)
                .build();

        McpStatelessAsyncServer server = McpServer.async(serverSide)
                .capabilities(serverCapabilities)
                .serverInfo(id, "PARTNER")
                .build();

        localMcpServers.put(id, server);
    }

    protected abstract RunnerResponse doRun(MetaAction metaAction);

    public abstract Path buildTmpPath(MetaAction tempAction, String codeType);

    public abstract void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException;

    public abstract void persistSerialize(MetaActionInfo metaActionInfo, McpData mcpData);

    /**
     * 列出执行环境下的系统依赖情况
     */
    public abstract JSONObject listSysDependencies();

    @Data
    protected static class RunnerResponse {
        private boolean ok;
        private String data;
    }

    protected sealed abstract static class McpServerParams permits HttpMcpServerParams, InProcessMcpServerParams, StdioMcpServerParams {
        private final int timeout;

        private McpServerParams(int timeout) {
            this.timeout = timeout;
        }
    }

    protected final static class HttpMcpServerParams extends McpServerParams {
        private final String baseUri;
        private final String endpoint;
        private final Map<String, String> headers;

        protected HttpMcpServerParams(int timeout, String baseUri, String endpoint, Map<String, String> header) {
            super(timeout);
            this.baseUri = baseUri;
            this.endpoint = endpoint;
            this.headers = header;
        }
    }

    protected final static class StdioMcpServerParams extends McpServerParams {
        private final String command;
        private final Map<String, String> env;
        private final List<String> args;

        protected StdioMcpServerParams(int timeout, String command, Map<String, String> env, List<String> args) {
            super(timeout);
            this.command = command;
            this.env = env;
            this.args = args;
        }
    }

    protected final static class InProcessMcpServerParams extends McpServerParams {
        private final String id;

        protected InProcessMcpServerParams(int timeout, String id) {
            super(timeout);
            this.id = id;
        }
    }

    protected enum McpServerType {
        HTTP,
        STDIO,
        /**
         * 对应 Partner 内部的 Server 创建方式
         */
        SELF
    }

    public static final class InProcessMcpTransport implements McpClientTransport, McpStatelessServerTransport {

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

}
