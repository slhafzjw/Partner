package work.slhaf.partner.runtime.interaction;

import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.framework.agent.config.Config;
import work.slhaf.partner.framework.agent.config.ConfigDoc;
import work.slhaf.partner.framework.agent.config.ConfigRegistration;
import work.slhaf.partner.framework.agent.config.Configurable;

import java.nio.file.Path;
import java.util.Map;

public class WebSocketGatewayRegistry implements Configurable {
    // TODO 在 Agent 入口处，针对这类内容提供统一注册
    @Override
    public @NotNull Map<Path, ConfigRegistration<? extends Config>> declare() {
        return Map.of(Path.of("gateway", "websocket.json"), new WebSocketRegistration());
    }

    static class WebSocketRegistration implements ConfigRegistration<WebSocketConfig> {

        @Override
        @NotNull
        public Class<WebSocketConfig> type() {
            return WebSocketConfig.class;
        }

        @Override
        public void init(@NotNull WebSocketConfig config, JSONObject json) {
            new WebSocketGateway(config.port, config.heartbeatInterval);
        }

        @Nullable
        @Override
        public WebSocketConfig defaultConfig() {
            return new WebSocketConfig(29600, 10_000);
        }
    }

    static class WebSocketConfig extends Config {
        @ConfigDoc(description = "WebSocket 监听端口")
        final int port;
        @ConfigDoc(description = "WebSocket 心跳间隔", unit = "ms", constraint = "> 0", example = "10000")
        final int heartbeatInterval;

        WebSocketConfig(int port, int heartbeatInterval) {
            this.port = port;
            this.heartbeatInterval = heartbeatInterval;
        }
    }
}
