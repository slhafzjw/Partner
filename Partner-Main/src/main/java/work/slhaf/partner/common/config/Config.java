package work.slhaf.partner.common.config;

import lombok.Data;

@Data
public class Config {
    private String agentId;
    private WebSocketConfig webSocketConfig;
    private VectorConfig vectorConfig;

    @Data
    public static class VectorConfig {
        private String ollamaEmbeddingUrl;
        private String ollamaEmbeddingModel;
    }

    @Data
    public static class WebSocketConfig {
        private int port;
    }
}
