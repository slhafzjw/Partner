package work.slhaf.partner.common.vector;

import work.slhaf.partner.framework.agent.config.Config;

public sealed class VectorConfig extends Config permits VectorConfig.Ollama, VectorConfig.Onnx {
    final boolean enabled;
    final Type type;
    final String modelId;

    public VectorConfig(boolean enabled, Type type, String modelId) {
        this.enabled = enabled;
        this.type = type;
        this.modelId = modelId;
    }

    protected static String fallbackModelId(String modelId, String fallback) {
        return modelId == null || modelId.isBlank() ? fallback : modelId;
    }

    public enum Type {
        ONNX,
        OLLAMA
    }

    static final class Onnx extends VectorConfig {

        final String tokenizerPath;
        final String embeddingModelPath;

        public Onnx(boolean enabled, Type type, String tokenizerPath, String embeddingModelPath, String modelId) {
            super(enabled, type, fallbackModelId(modelId, embeddingModelPath));
            this.tokenizerPath = tokenizerPath;
            this.embeddingModelPath = embeddingModelPath;
        }
    }

    static final class Ollama extends VectorConfig {

        final String ollamaEmbeddingUrl;
        final String ollamaEmbeddingModel;

        public Ollama(boolean enabled, Type type, String ollamaEmbeddingUrl, String ollamaEmbeddingModel, String modelId) {
            super(enabled, type, fallbackModelId(modelId, ollamaEmbeddingModel));
            this.ollamaEmbeddingUrl = ollamaEmbeddingUrl;
            this.ollamaEmbeddingModel = ollamaEmbeddingModel;
        }
    }
}