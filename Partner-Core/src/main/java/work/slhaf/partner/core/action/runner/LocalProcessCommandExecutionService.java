package work.slhaf.partner.core.action.runner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class LocalProcessCommandExecutionService implements CommandExecutionService {

    @Override
    public String[] buildCommands(String ext, Map<String, Object> params, String absolutePath) {
        String command = switch (ext) {
            case "py" -> "python";
            case "sh" -> "bash";
            default -> null;
        };
        if (command == null) {
            return null;
        }
        int paramSize = params == null ? 0 : params.size();
        String[] commands = new String[paramSize + 2];
        commands[0] = command;
        commands[1] = absolutePath;
        AtomicInteger paramCount = new AtomicInteger(2);
        if (params != null) {
            params.forEach((param, value) -> commands[paramCount.getAndIncrement()] = "--" + param + "=" + value);
        }
        return commands;
    }

    @Override
    public Result exec(String... command) {
        Result result = new Result();
        List<String> output = new ArrayList<>();
        List<String> error = new ArrayList<>();

        try {
            Process process = new ProcessBuilder(command)
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
}
