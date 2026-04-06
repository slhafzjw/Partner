package work.slhaf.partner.common.vector;

import work.slhaf.partner.api.agent.runtime.config.Config;

public sealed class VectorConfig extends Config permits VectorConfig.Ollama, VectorConfig.Onnx {
    final boolean enabled;
    final Type type;

    public VectorConfig(boolean enabled, Type type) {
        this.enabled = enabled;
        this.type = type;
    }

    public enum Type {
        ONNX,
        OLLAMA
    }

    static final class Onnx extends VectorConfig {

        final String tokenizerPath;
        final String embeddingModelPath;

        public Onnx(boolean enabled, Type type, String tokenizerPath, String embeddingModelPath) {
            super(enabled, type);
            this.tokenizerPath = tokenizerPath;
            this.embeddingModelPath = embeddingModelPath;
        }
    }

    static final class Ollama extends VectorConfig {

        final String ollamaEmbeddingUrl;
        final String ollamaEmbeddingModel;

        public Ollama(boolean enabled, Type type, String ollamaEmbeddingUrl, String ollamaEmbeddingModel) {
            super(enabled, type);
            this.ollamaEmbeddingUrl = ollamaEmbeddingUrl;
            this.ollamaEmbeddingModel = ollamaEmbeddingModel;
        }
    }
}


