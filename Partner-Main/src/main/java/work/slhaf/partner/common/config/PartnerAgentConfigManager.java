package work.slhaf.partner.common.config;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.config.exception.ConfigNotExistException;
import work.slhaf.partner.api.agent.runtime.config.FileAgentConfigManager;
import work.slhaf.partner.common.exception.ConfigLoadFailedException;

import java.io.File;
import java.nio.charset.StandardCharsets;

@EqualsAndHashCode(callSuper = true)
@Data
public final class PartnerAgentConfigManager extends FileAgentConfigManager {

    private static final String COMMON_CONFIG_FILE = CONFIG_DIR + "common_config.json";

    private Config config;

    @Override
    public void load() {
        loadWebSocketConfig();
        super.load();
    }

    private void loadWebSocketConfig() {
        File file = new File(COMMON_CONFIG_FILE);
        if (!file.exists()) {
            throw new ConfigNotExistException("Partner Config Not Exist: " + COMMON_CONFIG_FILE);
        }
        config = JSONUtil.readJSONObject(file, StandardCharsets.UTF_8).toBean(Config.class);
        if (config == null || config.getAgentId() == null) {
            throw new ConfigLoadFailedException("Partner Config Load Failed: " + COMMON_CONFIG_FILE);
        }
        if (config.getPort() <= 0 || config.getPort() > 65535) {
            throw new ConfigLoadFailedException("Invalid Websocket port: " + config.getPort());
        }
    }
}
