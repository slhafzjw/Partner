package work.slhaf.partner.core.action.runner.execution;

import lombok.Data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandExecutionService {

    public static final CommandExecutionService INSTANCE = new CommandExecutionService();

    private final ExecutorService readerExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private CommandExecutionService() {
    }

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

            readerExecutor.execute(stdoutThread);
            readerExecutor.execute(stderrThread);

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

    public CommandSession createSessionTask(List<String> commands) {
        return createSessionTask(commands.toArray(new String[0]));
    }

    public CommandSession createSessionTask(String... commands) {
        try {
            Process process = new ProcessBuilder(commands)
                    .redirectErrorStream(false)
                    .start();
            CommandSession session = new CommandSession();
            StringBuilder stdoutBuffer = new StringBuilder();
            StringBuilder stderrBuffer = new StringBuilder();
            session.setProcess(process);
            session.setStdoutBuffer(stdoutBuffer);
            session.setStderrBuffer(stderrBuffer);

            readerExecutor.execute(() -> readToBuffer(process.getInputStream(), stdoutBuffer));
            readerExecutor.execute(() -> readToBuffer(process.getErrorStream(), stderrBuffer));

            return session;
        } catch (Exception e) {
            throw new IllegalStateException("创建命令会话失败", e);
        }
    }

    private void readToBuffer(java.io.InputStream inputStream, StringBuilder buffer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (buffer) {
                    if (!buffer.isEmpty()) {
                        buffer.append('\n');
                    }
                    buffer.append(line);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Data
    public static class Result {
        private boolean ok;
        private String total;
        private List<String> resultList;
    }

    @Data
    public static class CommandSession {
        private Process process;
        private StringBuilder stdoutBuffer;
        private StringBuilder stderrBuffer;
    }
}
