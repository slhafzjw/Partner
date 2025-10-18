package work.slhaf.partner.common.vector;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.common.vector.exception.VectorClientExecuteException;

import java.util.Map;

@Slf4j
public class OllamaVectorClient extends VectorClient {

    private String ollamaEmbeddingUrl;
    private String ollamaEmbeddingModel;

    protected OllamaVectorClient(String url, String model) {
        this.ollamaEmbeddingUrl = url;
        this.ollamaEmbeddingModel = model;

        compute("test");
    }

    @Override
    protected float[] doCompute(String input) {
        Map<String, String> param = Map.of("model", ollamaEmbeddingModel, "input", input);
        HttpRequest request = HttpRequest.get(ollamaEmbeddingUrl).body(JSONObject.toJSONString(param));
        try (HttpResponse response = request.execute()) {
            if (!response.isOk())
                throw new VectorClientExecuteException("嵌入模型执行出错");
            String resStr = response.body();
            EmbeddingModelResponse embeddingResponse = JSONObject.parseObject(resStr, EmbeddingModelResponse.class);
            return embeddingResponse.getEmbeddings()[0];
        } catch (Exception e) {
            throw new VectorClientExecuteException("嵌入模型执行出错", e);
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
