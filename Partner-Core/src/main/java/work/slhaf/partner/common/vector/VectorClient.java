package work.slhaf.partner.common.vector;

import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import work.slhaf.partner.common.vector.exception.VectorClientExecutionException;
import work.slhaf.partner.common.vector.exception.VectorClientStartupException;

@Slf4j
public abstract class VectorClient {

    public static boolean status = false;
    public static VectorClient INSTANCE;

    public static void startClient(VectorConfig config) {
        try {
            if (config instanceof VectorConfig.Ollama ollama) {
                INSTANCE = new OllamaVectorClient(ollama.ollamaEmbeddingUrl, ollama.ollamaEmbeddingModel);
            } else if (config instanceof VectorConfig.Onnx onnx) {
                INSTANCE = new OnnxVectorClient(onnx.tokenizerPath, onnx.embeddingModelPath);
            } else {
                return;
            }
            status = true;
        } catch (VectorClientStartupException e) {
            throw e;
        } catch (VectorClientExecutionException e) {
            throw new VectorClientStartupException(
                    "Vector client startup failed",
                    e.getClientType(),
                    "COMPUTE".equals(e.getPhase()) ? "STARTUP_SELF_TEST" : e.getPhase(),
                    e.getTarget(),
                    e
            );
        } catch (Exception e) {
            throw new VectorClientStartupException(
                    "Vector client startup failed",
                    resolveClientType(config),
                    "STARTUP",
                    resolveTarget(config),
                    e
            );
        }
    }

    public float[] compute(String input) {
        if (!status) {
            return null;
        }
        return doCompute(input);
    }

    protected abstract float[] doCompute(String input);

    private static String resolveClientType(VectorConfig config) {
        if (config instanceof VectorConfig.Onnx) {
            return "onnx";
        }
        if (config instanceof VectorConfig.Ollama) {
            return "ollama";
        }
        return "unknown";
    }

    private static String resolveTarget(VectorConfig config) {
        if (config instanceof VectorConfig.Onnx onnx) {
            return onnx.embeddingModelPath;
        }
        if (config instanceof VectorConfig.Ollama ollama) {
            return ollama.ollamaEmbeddingUrl;
        }
        return null;
    }

    public double compare(float[] v1, float[] v2) {
        if (!status) {
            return 0;
        }
        try (INDArray a1 = Nd4j.create(v1); INDArray a2 = Nd4j.create(v2)) {
            return Transforms.cosineSim(a1, a2);
        }
    }

    public float[] weightedAverage(float[] newVector, float[] primaryVector) {
        try (INDArray primary = Nd4j.create(primaryVector);
             INDArray latest = Nd4j.create(newVector)) {

            // 1️⃣ 计算余弦相似度
            double similarity = Transforms.cosineSim(primary, latest);

            // 2️⃣ 根据相似度决定更新比例 α（差异越大，新输入影响越强）
            double alpha = (1.0 - similarity) * 0.5;
            alpha = Math.clamp(alpha, 0.05, 0.5);

            // 3️⃣ 按比例混合旧向量与新向量
            INDArray updated = primary.mul(1 - alpha).add(latest.mul(alpha));

            // 4️⃣ 归一化结果（保持方向空间一致）
            updated = updated.div(updated.norm2Number());

            return updated.toFloatVector();
        }
    }
}
