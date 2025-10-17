package work.slhaf.partner.common.vector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import work.slhaf.partner.common.vector.exception.VectorClientExecuteException;
import work.slhaf.partner.common.vector.exception.VectorClientLoadFailedException;

public class OnnxVectorClient extends VectorClient {

    private String tokenizerPath;
    private String modelPath;

    private HuggingFaceTokenizer tokenizer;
    private OrtSession session;
    private OrtEnvironment env;

    protected OnnxVectorClient(String tokenizer, String model) {
        this.tokenizerPath = tokenizer;
        this.modelPath = model;

        loadTokenizer();
        loadModel();
        compute("test");
    }

    private void loadModel() {
        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions ops = new OrtSession.SessionOptions();
            session = env.createSession(modelPath, ops);
        } catch (Exception e) {
            throw new VectorClientLoadFailedException("加载ONNX模型失败", e);
        }
    }

    private void loadTokenizer() {
        try {
            tokenizer = HuggingFaceTokenizer.newInstance(Path.of(tokenizerPath));
        } catch (Exception e) {
            throw new VectorClientLoadFailedException("加载Tokenizer失败", e);
        }
    }

    @Override
    protected float[] doCompute(String input) {
        try {
            Encoding encode = tokenizer.encode(input);
            long[] ids = encode.getIds();
            long[] attentionMask = encode.getAttentionMask();

            long[][] inputIdsBatch = { ids };
            long[][] attentionMaskBatch = { attentionMask };
            long[][] tokenTypeIdsBatch = { new long[ids.length] }; // 初始化全 0
            for (int i = 0; i < ids.length; i++)
                tokenTypeIdsBatch[0][i] = 0;

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputIdsBatch);
            OnnxTensor maskTensor = OnnxTensor.createTensor(env, attentionMaskBatch);
            OnnxTensor tokenTypeTensor = OnnxTensor.createTensor(env, tokenTypeIdsBatch);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputTensor);
            inputs.put("attention_mask", maskTensor);
            inputs.put("token_type_ids", tokenTypeTensor);

            OrtSession.Result result = session.run(inputs);
            OnnxTensor embeddingTensor = (OnnxTensor) result.get(0);
            return embeddingTensor.getFloatBuffer().array();
        } catch (Exception e) {
            throw new VectorClientExecuteException("嵌入模型执行出错", e);
        }
    }

}
