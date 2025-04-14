package work.slhaf.agent.core.config;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import work.slhaf.agent.core.models.core.CoreModel;
import work.slhaf.agent.core.models.slice.SliceEvaluator;
import work.slhaf.agent.core.models.task.TaskTrigger;
import work.slhaf.agent.core.models.topic.TopicExtractor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Scanner;

@Data
@Slf4j
public class Config {

    private static final String CONFIG_FILE_PATH = "./data/config/config.json";
    private static Config config;

    private String agentId;

    private HashMap<String, ModelConfig> modelConfig;

    private WebSocketConfig webSocketConfig;

    public static Config load() throws IOException {
        if (config == null) {
            File file = new File(CONFIG_FILE_PATH);
            if (file.exists()) {
                config = JSONUtil.readJSONObject(file, StandardCharsets.UTF_8).toBean(Config.class);
            } else {
                Config tempConfig = new Config();
                Scanner scanner = new Scanner(System.in);

                System.out.print("输入智能体名称: ");
                tempConfig.setAgentId(scanner.nextLine());

                System.out.println("\r\n--------模型配置--------\r\n");
                HashMap<String, ModelConfig> modelConfig = new HashMap<>();
                for (int i = 0; i < 4; i++) {
                    String modelKey = switch (i) {
                        case 0 -> {
                            System.out.println("CoreModel:");
                            yield CoreModel.MODEL_KEY;
                        }
                        case 1 -> {
                            System.out.println("SliceEvaluator:");
                            yield SliceEvaluator.MODEL_KEY;
                        }
                        case 2 -> {
                            System.out.println("TaskTrigger:");
                            yield TaskTrigger.MODEL_KEY;
                        }
                        case 3 -> {
                            System.out.println("TopicExtractor:");
                            yield TopicExtractor.MODEL_KEY;
                        }
                        default -> throw new RuntimeException();
                    };
                    System.out.println(modelKey);
                    ModelConfig temp = new ModelConfig();
                    System.out.print("apikey: ");
                    temp.setApikey(scanner.nextLine());
                    System.out.print("baseUrl: ");
                    temp.setBaseUrl(scanner.nextLine());
                    System.out.print("model: ");
                    temp.setModel(scanner.nextLine());

                    modelConfig.put(modelKey, temp);
                }
                tempConfig.setModelConfig(modelConfig);

                System.out.println("\r\n--------服务配置--------\r\n");
                System.out.print("WebSocket port: ");
                WebSocketConfig wsConfig = new WebSocketConfig();
                wsConfig.setPort(scanner.nextInt());

                //保存配置文件
                String str = JSONUtil.toJsonPrettyStr(tempConfig);
                FileUtils.writeStringToFile(file,str,StandardCharsets.UTF_8);
                log.info("配置已保存");
                config = tempConfig;
            }
        }
        return config;
    }
}
