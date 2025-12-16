package experimental;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.runner.LocalRunnerClient;
import work.slhaf.partner.core.action.runner.RunnerClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SystemTest {
    @Test
    void execTest() {
        // exec("pwd");
        // exec("ls", "-la");
        String r = exec("pip", "st", "--format=freeze");
        System.out.println(r);
    }

    private String exec(String... command) {
        StringBuilder s = new StringBuilder();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            Process process = processBuilder.start();
            java.io.InputStream inputStream = process.getInputStream();
            java.util.Scanner scanner = new java.util.Scanner(inputStream).useDelimiter("\\A");
            if (scanner.hasNext()) {
                s.append(scanner.next());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return s.toString();
    }

    @Test
    void localRunnerClientTest() {
        Map<String, MetaActionInfo> existedMetaActions = new HashMap<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        RunnerClient client = new LocalRunnerClient(existedMetaActions, executor, null);
        JSONObject res = client.listSysDependencies();
        System.out.println(res.toString());
    }

}
