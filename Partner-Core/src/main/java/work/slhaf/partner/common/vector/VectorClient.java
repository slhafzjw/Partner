package work.slhaf.partner.common.vector;

import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigLoader;
import work.slhaf.partner.common.config.Config.VectorConfig;
import work.slhaf.partner.common.config.PartnerAgentConfigLoader;
import work.slhaf.partner.common.exception.ServiceLoadFailedException;
import work.slhaf.partner.common.vector.exception.VectorClientExecuteException;
import work.slhaf.partner.common.vector.exception.VectorClientLoadFailedException;

@Slf4j
public abstract class VectorClient {

    public static boolean status;
    public static VectorClient INSTANCE;

    public static void load() {
        PartnerAgentConfigLoader configManager = (PartnerAgentConfigLoader) AgentConfigLoader.INSTANCE;
        VectorConfig vectorConfig = configManager.getConfig().getVectorConfig();
        int type = vectorConfig.getType();
        try {
            switch (type) {
                case 0:
                    status = false;
                    break;
                case 1:
                    status = true;
                    INSTANCE = new OllamaVectorClient(vectorConfig.getOllamaEmbeddingUrl(),
                            vectorConfig.getOllamaEmbeddingModel());
                    break;
                case 2:
                    status = true;
                    INSTANCE = new OnnxVectorClient(vectorConfig.getTokenizerPath(),
                            vectorConfig.getEmbeddingModelPath());
                    break;
                default:
                    throw new ServiceLoadFailedException(
                            "加载向量客户端失败! type: 0 -> 不启用语义缓存; type: 1 -> ollama; type: 2 -> ONNX RUNTIME");
            }
            log.info("向量客户端加载完毕");
        } catch (VectorClientLoadFailedException | VectorClientExecuteException exception) {
            status = false;
            log.error("向量客户端加载失败", exception);
        }
    }

    public float[] compute(String input) {
        if (!status) {
            return null;
        }
        return doCompute(input);
    }

    protected abstract float[] doCompute(String input);

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
            alpha = Math.max(0.05, Math.min(alpha, 0.5));

            // 3️⃣ 按比例混合旧向量与新向量
            INDArray updated = primary.mul(1 - alpha).add(latest.mul(alpha));

            // 4️⃣ 归一化结果（保持方向空间一致）
            updated = updated.div(updated.norm2Number());

            return updated.toFloatVector();
        }
    }
}