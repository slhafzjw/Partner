package work.slhaf.agent.common.config;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONArray;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import work.slhaf.agent.module.modules.core.CoreModel;
import work.slhaf.agent.module.modules.memory.selector.MemorySelector;
import work.slhaf.agent.module.modules.memory.updater.MemoryUpdater;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

@Data
@Slf4j
public class Config {

    private static final String CONFIG_FILE_PATH = "./config/config.json";
    private static final String LOG_FILE_PATH = "./data/log";
    private static Config config;

    private String agentId;
//    private String basicCharacter;

    private WebSocketConfig webSocketConfig;

    private List<ModuleConfig> moduleConfigList;

    private Config() {
    }

    public static Config getConfig() throws IOException {
        if (config == null) {
            File file = new File(CONFIG_FILE_PATH);
            if (file.exists()) {
                config = JSONUtil.readJSONObject(file, StandardCharsets.UTF_8).toBean(Config.class);
            } else {
                config = new Config();
                Scanner scanner = new Scanner(System.in);

                System.out.print("输入智能体名称: ");
                config.setAgentId(scanner.nextLine());

                System.out.println("(注意! 设定角色之后修改主配置文件将不会影响现有记忆，除非同时更换agentId)");

                System.out.println("\r\n--------模型配置--------\r\n");
                generateModelConfig(scanner);

                System.out.println("\r\n--------服务配置--------\r\n");
                generateWsSocketConfig(scanner);

                System.out.println("\r\n--------模块链配置--------\r\n");
                generatePipelineConfig();

                boolean launchOrNot = getLaunchOrNot(scanner);

                //保存配置文件
                String str = JSONUtil.toJsonPrettyStr(config);
                FileUtils.writeStringToFile(file, str, StandardCharsets.UTF_8);
                log.info("配置已保存");

                if (!launchOrNot) {
                    System.exit(0);
                }
            }
            config.generateCommonDirs();
        }
        return config;
    }

    private void generateCommonDirs() throws IOException {
        Files.createDirectories(Paths.get(LOG_FILE_PATH));
    }

    private static boolean getLaunchOrNot(Scanner scanner) {
        System.out.print("是否直接启动Partner?(y/n): ");
        String input;
        while (true) {
            input = scanner.nextLine();
            if (input.equals("y")) {
                return true;
            } else if (input.equals("n")) {
                return false;
            } else {
                System.out.println("请输入y或n");
            }
        }
    }

    private static void generatePipelineConfig() {
        List<ModuleConfig> moduleConfigList = List.of(
                new ModuleConfig(MemorySelector.class.getName(), ModuleConfig.Constant.INTERNAL, null),
                new ModuleConfig(CoreModel.class.getName(), ModuleConfig.Constant.INTERNAL, null),
                new ModuleConfig(MemoryUpdater.class.getName(), ModuleConfig.Constant.INTERNAL, null)
//                new ModuleConfig(TaskScheduler.class.getName(), ModuleConfig.Constant.INTERNAL, null)
        );
        config.setModuleConfigList(moduleConfigList);
    }

    private static void generateWsSocketConfig(Scanner scanner) {
        System.out.print("WebSocket port: ");
        WebSocketConfig wsConfig = new WebSocketConfig();
        wsConfig.setPort(scanner.nextInt());
        config.setWebSocketConfig(wsConfig);
    }

    private static void generateModelConfig(Scanner scanner) throws IOException {
        System.out.println("配置LLM APi:");
        System.out.println("经测试, 目前只建议选择Qwen3: qwen-plus-latest或qwen-max-latest");
        System.out.print("base_url: ");
        String baseUrl = scanner.nextLine();
        System.out.print("apikey: ");
        String apikey = scanner.nextLine();
        System.out.print("model: ");
        String model = scanner.nextLine();

        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setBaseUrl(baseUrl);
        modelConfig.setApikey(apikey);
        modelConfig.setModel(model);

        InputStream stream = Config.class.getClassLoader().getResourceAsStream("modules/default_activated_model.json");
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        stream.close();
        for (String s : JSONArray.parseArray(content, String.class)) {
            modelConfig.generateConfig(s);
        }
    }

}
