package work.slhaf.partner.core.action.runner.execution;

import lombok.Data;
import work.slhaf.partner.core.action.runner.policy.WrappedLaunchSpec;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandExecutionService {

    public static final CommandExecutionService INSTANCE = new CommandExecutionService();

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

    public Result exec(WrappedLaunchSpec launchSpec) {
        Result result = new Result();
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        List<String> error = Collections.synchronizedList(new ArrayList<>());

        try {
            Process process = startProcess(launchSpec);

            Thread stdoutThread = Thread.startVirtualThread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.add(line);
                    }
                } catch (Exception ignored) {
                }
            });

            Thread stderrThread = Thread.startVirtualThread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.add(line);
                    }
                } catch (Exception ignored) {
                }
            });

            int exitCode = process.waitFor();
            stdoutThread.join();
            stderrThread.join();

            result.setOk(exitCode == 0);
            List<String> stdoutLines = List.copyOf(output);
            List<String> stderrLines = List.copyOf(error);
            result.setStdoutLines(stdoutLines);
            result.setStderrLines(stderrLines);
            result.setResultList(stdoutLines.isEmpty() ? stderrLines : stdoutLines);
            result.setTotal(buildDisplayText(stdoutLines, stderrLines));
        } catch (Exception e) {
            result.setOk(false);
            result.setTotal(e.getMessage());
            result.setStdoutLines(List.of());
            result.setStderrLines(List.of(e.getMessage()));
            result.setResultList(result.getStderrLines());
        }

        return result;
    }

    public Result exec(String... commands) {
        return exec(defaultLaunchSpec(commands));
    }

    public CommandSession createSessionTask(List<String> commands) {
        return createSessionTask(commands.toArray(new String[0]));
    }

    public CommandSession createSessionTask(WrappedLaunchSpec launchSpec) {
        try {
            Process process = startProcess(launchSpec);
            CommandSession session = new CommandSession();
            StringBuilder stdoutBuffer = new StringBuilder();
            StringBuilder stderrBuffer = new StringBuilder();
            session.setProcess(process);
            session.setStdoutBuffer(stdoutBuffer);
            session.setStderrBuffer(stderrBuffer);

            Thread.startVirtualThread(() -> readToBuffer(process.getInputStream(), stdoutBuffer));
            Thread.startVirtualThread(() -> readToBuffer(process.getErrorStream(), stderrBuffer));

            return session;
        } catch (Exception e) {
            throw new IllegalStateException("创建命令会话失败", e);
        }
    }

    public CommandSession createSessionTask(String... commands) {
        return createSessionTask(defaultLaunchSpec(commands));
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

    private Process startProcess(WrappedLaunchSpec launchSpec) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> command = new ArrayList<>();
        command.add(launchSpec.getCommand());
        command.addAll(launchSpec.getArgs());
        processBuilder.command(command);
        processBuilder.redirectErrorStream(false);
        if (launchSpec.getWorkingDirectory() != null && !launchSpec.getWorkingDirectory().isBlank()) {
            processBuilder.directory(new File(launchSpec.getWorkingDirectory()));
        }
        Map<String, String> environment = processBuilder.environment();
        environment.clear();
        environment.putAll(launchSpec.getEnvironment());
        return processBuilder.start();
    }

    private WrappedLaunchSpec defaultLaunchSpec(String... commands) {
        return new WrappedLaunchSpec(
                commands[0],
                List.of(commands).subList(1, commands.length),
                null,
                System.getenv()
        );
    }

    private String buildDisplayText(List<String> stdoutLines, List<String> stderrLines) {
        if (stdoutLines.isEmpty()) {
            return String.join("\n", stderrLines);
        }
        if (stderrLines.isEmpty()) {
            return String.join("\n", stdoutLines);
        }
        return String.join("\n", stdoutLines) + "\n" + String.join("\n", stderrLines);
    }

    @Data
    public static class Result {
        private boolean ok;
        private String total;
        private List<String> resultList;
        private List<String> stdoutLines;
        private List<String> stderrLines;
    }

    @Data
    public static class CommandSession {
        private Process process;
        private StringBuilder stdoutBuffer;
        private StringBuilder stderrBuffer;
    }
}
