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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import work.slhaf.partner.core.action.entity.McpData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaAction.Result;
import work.slhaf.partner.core.action.entity.MetaAction.ResultStatus;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 执行客户端抽象类
 * <br/>
 * 只负责暴露序列化、执行等相应接口，具体逻辑交给下游实现
 * <br/>
 * 默认存在两类实现，{@link LocalRunnerClient} 和 {@link SandboxRunnerClient}
 * <ol>
 *     LocalRunnerClient:
 *     <li>
 *         对应本地运行环境，可在本地启动 MCP 客户端将 RunnerClient 暴露的能力接口转发至本地 MCP Client 并执行
 *     </li>
 *     SandboxRunnerClient:
 *     <li>
 *         对应沙盒运行环境，该 Client 仅作为沙盒环境的客户端，不持有额外能力，仅保持远端连接已存在行动的内容更新
 *     </li>
 * </ol>
 */
@Slf4j
public abstract class RunnerClient {

    protected final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions;
    protected final ExecutorService executor;
    protected final Map<String, McpSyncClient> mcpClients = new HashMap<>();
    protected final Map<String, McpStatelessAsyncServer> localMcpServers = new HashMap<>();

    /**
     * ActionCore 将注入虚拟线程池
     */
    public RunnerClient(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions, ExecutorService executor) {
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
                .toolsChangeConsumer(tools -> updateExistedMetaActions(id, tools))
                .build();
        mcpClients.put(id, client);
    }

    private void updateExistedMetaActions(String id, @UnknownNullability List<McpSchema.Tool> tools) {
        for (McpSchema.Tool tool : tools) {
            MetaActionInfo info = buildMetaActionInfo(tool);
            String actionKey = id + "::" + tool.name();
            existedMetaActions.put(actionKey, info);
        }
    }

    private static @NotNull MetaActionInfo buildMetaActionInfo(McpSchema.Tool tool) {
        MetaActionInfo info = new MetaActionInfo();
        info.setDescription(tool.description());
        Map<String, Object> outputSchema = tool.outputSchema();
        info.setResponseSchema(outputSchema == null ? JSONObject.of() : JSONObject.from(outputSchema));
        info.setParams(tool.inputSchema().properties());

        JSONObject meta = JSONObject.from(tool.meta());
        info.setIo(meta.getBoolean("io"));
        info.setPreActions(meta.getList("pre", String.class));
        info.setPostActions(meta.getList("post", String.class));
        info.setStrictDependencies(meta.getBoolean("strict"));
        info.setTags(meta.getList("tag", String.class));
        return info;
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

    public abstract String buildTmpPath(MetaAction tempAction, String codeType);

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
