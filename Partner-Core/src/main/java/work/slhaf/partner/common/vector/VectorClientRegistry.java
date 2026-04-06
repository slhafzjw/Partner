package work.slhaf.partner.common.vector;

import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.framework.agent.config.Config;
import work.slhaf.partner.framework.agent.config.ConfigRegistration;
import work.slhaf.partner.framework.agent.config.Configurable;

import java.nio.file.Path;
import java.util.Map;

public class VectorClientRegistry implements Configurable, ConfigRegistration<VectorConfig> {

    @Override
    public void init(@NotNull VectorConfig config, @Nullable JSONObject json) {
        if (!config.enabled) {
            return;
        }
        if (config.type == null) {
            return;
        }
        if (json == null) {
            return;
        }
        config = switch (config.type) {
            case ONNX -> json.toJavaObject(VectorConfig.Onnx.class);
            case OLLAMA -> json.toJavaObject(VectorConfig.Ollama.class);
        };
        VectorClient.startClient(config);
    }

    @Override
    @NotNull
    public Class<VectorConfig> type() {
        return VectorConfig.class;
    }

    @Nullable
    @Override
    public VectorConfig defaultConfig() {
        return new VectorConfig(false, null);
    }

    @Override
    public @NotNull Map<Path, ConfigRegistration<? extends Config>> declare() {
        return Map.of(Path.of("vector", "config.json"), this);
    }

}

