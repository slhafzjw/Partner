package work.slhaf.partner.runtime.interaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.api.agent.runtime.config.Config;
import work.slhaf.partner.api.agent.runtime.config.ConfigRegistration;
import work.slhaf.partner.api.agent.runtime.config.Configurable;

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
        public void init(@NotNull WebSocketConfig config) {
            new WebSocketGateway(config.port, config.heartbeatInterval);
        }

        @Nullable
        @Override
        public WebSocketConfig defaultConfig() {
            return new WebSocketConfig(29600, 10_000);
        }
    }

    static class WebSocketConfig extends Config {
        final int port;
        final int heartbeatInterval;

        WebSocketConfig(int port, int heartbeatInterval) {
            this.port = port;
            this.heartbeatInterval = heartbeatInterval;
        }
    }
}
