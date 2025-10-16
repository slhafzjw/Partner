package work.slhaf.partner.common.util;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.common.config.PartnerAgentConfigManager;

import java.util.Map;

@Slf4j
public class VectorUtil {

    private static final String OLLAMA_EMBEDDING_URL = ((PartnerAgentConfigManager) AgentConfigManager.INSTANCE).getConfig().getVectorConfig().getOllamaEmbeddingUrl();
    private static final String OLLAMA_EMBEDDING_MODEL = ((PartnerAgentConfigManager) AgentConfigManager.INSTANCE).getConfig().getVectorConfig().getOllamaEmbeddingModel();

    private VectorUtil() {
    }

    /**
     * 如果计算失败将返回null
     *
     * @param input 需要计算向量的字符串
     * @return 向量计算结果
     */
    public static float[] compute(String input) {
        Map<String, String> param = Map.of("model", OLLAMA_EMBEDDING_MODEL, "input", input);
        HttpRequest request = HttpRequest.get(OLLAMA_EMBEDDING_URL).body(JSONObject.toJSONString(param));
        try (HttpResponse response = request.execute()) {
            if (!response.isOk()) return null;
            String resStr = response.body();
            EmbeddingModelResponse embeddingResponse = JSONObject.parseObject(resStr, EmbeddingModelResponse.class);
            return embeddingResponse.getEmbeddings()[0];
        } catch (Exception e) {
            log.error("嵌入模型执行出错", e);
            return null;
        }
    }

    public static double compare(float[] v1, float[] v2) {
        try (INDArray a1 = Nd4j.create(v1); INDArray a2 = Nd4j.create(v2)) {
            return Transforms.cosineSim(a1, a2);
        }
    }

    @Data
    private static class EmbeddingModelResponse {
        private String model;
        private float[][] embeddings;
        private long total_duration;
        private long load_duration;
        private int prompt_eval_count;
    }
}
