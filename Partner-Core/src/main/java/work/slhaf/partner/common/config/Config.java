package work.slhaf.partner.common.config;

import lombok.Data;

@Data
public class Config {
    private String agentId;
    private WebSocketConfig webSocketConfig;
    private VectorConfig vectorConfig;

    @Data
    public static class VectorConfig {
        private int type;
        private String ollamaEmbeddingUrl;
        private String ollamaEmbeddingModel;
        private String tokenizerPath;
        private String embeddingModelPath;
    }

    @Data
    public static class WebSocketConfig {
        private int port;
    }
}
