package work.slhaf.agent.common.config;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import work.slhaf.agent.module.modules.core.CoreModel;
import work.slhaf.agent.module.modules.memory.selector.MemorySelector;
import work.slhaf.agent.module.modules.memory.selector.evaluator.SliceSelectEvaluator;
import work.slhaf.agent.module.modules.memory.selector.extractor.MemorySelectExtractor;
import work.slhaf.agent.module.modules.memory.updater.MemoryUpdater;
import work.slhaf.agent.module.modules.memory.updater.static_extractor.StaticMemoryExtractor;
import work.slhaf.agent.module.modules.memory.updater.summarizer.MemorySummarizer;
import work.slhaf.agent.module.modules.task.TaskEvaluator;

import java.io.File;
import java.io.IOException;
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
    private String basicCharacter;

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

                System.out.print("输入智能体基础角色设定: ");
                config.setBasicCharacter(scanner.nextLine());

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
            }else if (input.equals("n")) {
                return false;
            }else {
                System.out.println("请输入y或n");
            }
        }
    }

    private static void generatePipelineConfig() {
        List<ModuleConfig> moduleConfigList = List.of(
                new ModuleConfig(MemorySelector.class.getName(), ModuleConfig.Constant.INTERNAL, null),
                new ModuleConfig(CoreModel.class.getName(),ModuleConfig.Constant.INTERNAL,null),
                new ModuleConfig(MemoryUpdater.class.getName(),ModuleConfig.Constant.INTERNAL,null)
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
        System.out.print("各模块是否配置为同一个LLM? (y/n, 建议选'y'，后续自行调整单独模块的配置): ");
        String input;
        while (true) {
            input = scanner.nextLine();
            if (input.equals("y") || input.equals("n")){
                break;
            }
            System.out.println("请输入y或n");
        }
        boolean singleModel = input.equals("y");

        ModelConfig modelConfig = new ModelConfig();
        if (singleModel) {
            System.out.println("输入模型配置: ");
            System.out.print("apikey: ");
            modelConfig.setApikey(scanner.nextLine());
            System.out.print("baseUrl: ");
            modelConfig.setBaseUrl(scanner.nextLine());
            System.out.print("model: ");
            modelConfig.setModel(scanner.nextLine());

        }
        for (int i = 0; i < 6; i++) {
            String modelKey = switch (i) {
                case 0 -> {
                    System.out.println("CoreModel:");
                    yield CoreModel.MODEL_KEY;
                }
                case 1 -> {
                    System.out.println("SliceEvaluator:");
                    yield SliceSelectEvaluator.MODEL_KEY;
                }
                case 2 -> {
                    System.out.println("TaskEvaluator:");
                    yield TaskEvaluator.MODEL_KEY;
                }
                case 3 -> {
                    System.out.println("TopicExtractor:");
                    yield MemorySelectExtractor.MODEL_KEY;
                }
                case 4 -> {
                    System.out.println("MemorySummarizer:");
                    yield MemorySummarizer.MODEL_KEY;
                }
                case 5 -> {
                    System.out.println("StaticMemoryExtractor:");
                    yield StaticMemoryExtractor.MODEL_KEY;
                }
                default -> throw new RuntimeException();
            };
            if (!singleModel) {
                modelConfig = new ModelConfig();
                System.out.print("apikey: ");
                modelConfig.setApikey(scanner.nextLine());
                System.out.print("baseUrl: ");
                modelConfig.setBaseUrl(scanner.nextLine());
                System.out.print("model: ");
                modelConfig.setModel(scanner.nextLine());
            }
            modelConfig.generateConfig(modelKey);
        }
    }

}
