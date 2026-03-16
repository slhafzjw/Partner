package work.slhaf.partner.core.action.runner.execution;

import lombok.Data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandExecutionService {

    public String[] buildFileExecutionCommands(String launcher, Map<String, Object> params, String absolutePath) {
        int paramSize = params == null ? 0 : params.size();
        String[] commands = new String[paramSize + 2];
        commands[0] = launcher;
        commands[1] = absolutePath;
        AtomicInteger paramCount = new AtomicInteger(2);
        if (params != null) {
            params.forEach((param, value) -> commands[paramCount.getAndIncrement()] = "--" + param + "=" + value);
        }
        return commands;
    }

    public Result exec(List<String> commands) {
        return exec(commands.toArray(new String[0]));
    }

    public Result exec(String... commands) {
        Result result = new Result();
        List<String> output = new ArrayList<>();
        List<String> error = new ArrayList<>();

        try {
            Process process = new ProcessBuilder(commands)
                    .redirectErrorStream(false)
                    .start();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.add(line);
                    }
                } catch (Exception ignored) {
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.add(line);
                    }
                } catch (Exception ignored) {
                }
            });

            stdoutThread.start();
            stderrThread.start();

            int exitCode = process.waitFor();
            stdoutThread.join();
            stderrThread.join();

            result.setOk(exitCode == 0);
            result.setResultList(output.isEmpty() ? error : output);
            result.setTotal(String.join("\n", output.isEmpty() ? error : output));
        } catch (Exception e) {
            result.setOk(false);
            result.setTotal(e.getMessage());
        }

        return result;
    }

    @Data
    public static class Result {
        private boolean ok;
        private String total;
        private List<String> resultList;
    }
}
