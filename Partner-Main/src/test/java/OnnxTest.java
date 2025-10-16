import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class OnnxTest {
    static String tokenizer_json;
    static String base;
    static String model;

    @BeforeAll
    static void init() {
        base = "/home/slhaf/IdeaProjects/Projects/Partner/data/vector/";
        tokenizer_json = base + "tokenizer.json";
        model = base + "model_quantized.onnx";
    }

    @Test
    void tokenizerTest() throws IOException {
        long l1 = System.currentTimeMillis();
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(Path.of(tokenizer_json));
        long l2 = System.currentTimeMillis();
        Encoding encode = tokenizer.encode("test: Hello World");
        long l3 = System.currentTimeMillis();
        long[] ids = encode.getIds();
        long[] attentionMask = encode.getAttentionMask();
        log.info(Arrays.toString(ids));
        log.info("-----------------------------");
        log.info(Arrays.toString(attentionMask));
        log.info("-----------------------------");
        log.info("加载耗时: {}ms", l2 - l1);
        log.info("计算耗时: {}ms", l3 - l2);
        tokenizer.close();
        /* 输出:
         * [101, 3231, 1024, 7592, 2088, 102]
         * -----------------------------
         * [1, 1, 1, 1, 1, 1]
         * -----------------------------
         * 加载耗时: 4206ms
         * 计算耗时: 1ms
         */
    }

    @Test
    void onnxTest() throws IOException, OrtException {
        long l1 = System.currentTimeMillis();
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(Path.of(tokenizer_json));
        long l2 = System.currentTimeMillis();//tokenizer加载耗时
        Encoding encode = tokenizer.encode("test: Hello World");
        long l3 = System.currentTimeMillis();//计算耗时

        long[] ids = encode.getIds();
        long[] attentionMask = encode.getAttentionMask();

        long l4 = System.currentTimeMillis();
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions ops = new OrtSession.SessionOptions();
        OrtSession session = env.createSession(model, ops);
        long l5 = System.currentTimeMillis();//模型加载耗时

        long[][] inputIdsBatch = {ids};
        long[][] attentionMaskBatch = {attentionMask};
        long[][] tokenTypeIdsBatch = {new long[ids.length]};  // 初始化全 0
        for (int i = 0; i < ids.length; i++) tokenTypeIdsBatch[0][i] = 0;

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputIdsBatch);
        OnnxTensor maskTensor = OnnxTensor.createTensor(env, attentionMaskBatch);
        OnnxTensor tokenTypeTensor = OnnxTensor.createTensor(env, tokenTypeIdsBatch);

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", inputTensor);
        inputs.put("attention_mask", maskTensor);
        inputs.put("token_type_ids", tokenTypeTensor);

        long l6 = System.currentTimeMillis();
        OrtSession.Result result = session.run(inputs);
        long l7 = System.currentTimeMillis();//模型计算耗时
        OnnxTensor embeddingTensor = (OnnxTensor) result.get(0);
        float[] embeddings = embeddingTensor.getFloatBuffer().array();


        log.info(Arrays.toString(embeddings));
        log.info("------------------------");
        log.info("tokenizer加载耗时: {}ms", l2 - l1);
        log.info("tokenizer计算耗时: {}ms", l3 - l2);
        log.info("模型加载耗时: {}ms", l5 - l4);
        log.info("模型数据准备耗时: {}ms", l6 - l5);
        log.info("模型计算耗时: {}ms", l7 - l6);
        tokenizer.close();
    }
}
