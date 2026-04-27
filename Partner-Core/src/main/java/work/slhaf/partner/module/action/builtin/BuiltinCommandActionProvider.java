package work.slhaf.partner.module.action.builtin;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.runner.execution.CommandExecutionService;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicyRegistry;
import work.slhaf.partner.core.action.runner.policy.WrappedLaunchSpec;
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static work.slhaf.partner.core.action.ActionCore.BUILTIN_LOCATION;

@AgentComponent
class BuiltinCommandActionProvider implements BuiltinActionProvider {

    private static final String COMMAND_LOCATION = BUILTIN_LOCATION + "::" + "command";
    private static final String COMMAND_ARG_PREFIX = "arg";
    private static final int DEFAULT_READ_LIMIT = 4096;
    private static final int SUMMARY_MAX_LINES = 5;
    private static final int SUMMARY_MAX_LENGTH = 2048;
    private static final Duration COMMAND_SESSION_TTL = Duration.ofMinutes(10);
    private static final int COMMAND_SESSION_TAIL_LIMIT = 64 * 1024;
    private static final Duration OUTPUT_FLUSH_INTERVAL = Duration.ofMillis(100);
    private static final Path COMMAND_SESSION_LOG_DIR = Path.of(System.getProperty("java.io.tmpdir"), "partner-command-sessions");

    private final Set<String> basicTags = Set.of("Builtin MetaAction", "System Command Tool");

    private final ConcurrentHashMap<String, CommandHandle> commandHandles = new ConcurrentHashMap<>();
    private final CommandExecutionService commandExecutionService = CommandExecutionService.INSTANCE;

    @InjectModule
    private BuiltinActionRegistry builtinActionRegistry;

    @Init
    public void init() {
        builtinActionRegistry.register(this);
    }

    private Map<String, String> commandParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("arg", param("required", "string", "Command executable name or path."));
        params.put("arg1", param("optional", "string", "First command argument after the executable."));
        params.put("arg2", param("optional", "string", "Second command argument after the executable."));
        params.put("argN", param("optional", "string", "Additional command arguments after arg2. Use consecutive numeric keys such as arg3, arg4, arg5, ... when more arguments are needed."));
        return params;
    }

    private Map<String, String> commandSessionParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("desc", param("required", "string", "Short human-readable description of why this background command session exists."));
        params.putAll(commandParams());
        return params;
    }

    @Override
    public List<BuiltinActionRegistry.BuiltinActionDefinition> provideBuiltinActions() {
        return List.of(
                buildCommandExecuteDefinition(),
                buildCommandStartDefinition(),
                buildCommandInspectDefinition(),
                buildCommandReadDefinition(),
                buildCommandCancelDefinition(),
                buildCommandOverviewDefinition()
        );
    }

    /**
     * 用于直接执行的 Builtin MetaAction
     *
     * @return 内建 MetaAction 定义数据，参数为常规命令列表，返回值为该命令的响应内容
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildCommandExecuteDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Command Execution");
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                commandParams(),
                "Purpose: execute a short, non-interactive system command synchronously and return its completed output. Inputs: argv entries through arg/argN. Returns: JSON with result containing stdout/stderr/exit summary text. Use when: the task can finish quickly in one process call. Notes: do not use for long-running services, interactive programs, streaming output, or processes that need later inspection.",
                tags,
                Set.of(),
                Set.of(createActionKey("inspect")),
                false,
                JSONObject.of("result", "Command execution result text.")
        );
        Function<Map<String, Object>, String> invoker = params -> {
            List<String> commands = requireCommandArguments(params);
            CommandExecutionService.Result result = commandExecutionService.exec(wrapCommands(commands));
            return JSONObject.of("result", result.getTotal()).toJSONString();
        };
        return new BuiltinActionRegistry.BuiltinActionDefinition(
                createActionKey("execute"),
                info,
                invoker
        );
    }

    /**
     * 用于启动后台命令的 Builtin MetaAction，后台将持续接收 stdout/stderr
     *
     * @return 内建 MetaAction 定义数据，参数为命令列表及进程描述，返回值为进程句柄 id
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildCommandStartDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Command Session");
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                commandSessionParams(),
                "Purpose: start a long-running or streaming command as a background session. Inputs: session description and argument entries. Returns: JSON with executionId; pass it as id to inspect, read, or cancel. Use when: the command may run for a while, produce incremental output, or need later control. Notes: usually follow with builtin::command::inspect or builtin::command::read.",
                tags,
                Set.of(),
                Set.of(createActionKey("inspect")),
                false,
                JSONObject.of("executionId", "Command execution session id.")
        );
        Function<Map<String, Object>, String> invoker = params -> {
            cleanupExpiredHandles();
            String desc = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "desc");
            List<String> commands = requireCommandArguments(params);
            CommandExecutionService.CommandSession session = commandExecutionService.createSessionTask(wrapCommands(commands));
            String executionId = UUID.randomUUID().toString();
            Path stdoutLogPath = createSessionLogPath(executionId, "stdout.log");
            Path stderrLogPath = createSessionLogPath(executionId, "stderr.log");
            CommandHandle handle = new CommandHandle(
                    executionId,
                    desc,
                    commands,
                    Instant.now(),
                    session.getProcess(),
                    session.getStdoutBuffer(),
                    session.getStderrBuffer(),
                    new StringBuilder(),
                    new StringBuilder(),
                    stdoutLogPath,
                    stderrLogPath,
                    null,
                    null
            );
            commandHandles.put(executionId, handle);
            startOutputFlusher(handle);
            monitorProcess(handle);
            return JSONObject.of("executionId", executionId).toJSONString();
        };
        return new BuiltinActionRegistry.BuiltinActionDefinition(createActionKey("start"), info, invoker);
    }

    /**
     * 用于返回指定后台 Builtin MetaAction 的摘要内容
     *
     * @return 内建 MetaAction 定义数据，参数为进程 id，返回值为摘要内容 JSON
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildCommandInspectDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Command Session");
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of("id", param("required", "string", "Command executionId returned by builtin::command::start.")),
                "Purpose: inspect status and short output summaries for an existing background command session. Inputs: id from command::start. Returns: JSON with executionId, desc, exitCode, stdoutSize, stderrSize, stdoutSummary, stderrSummary, startAt, and endAt. Use when: deciding whether a background command is still running, failed, or needs a full output read. Notes: use builtin::command::read for complete or paginated output.",
                tags,
                Set.of(createActionKey("overview")),
                Set.of(),
                false,
                JSONObject.of(
                        "executionId", "Command execution session id.",
                        "desc", "Command session description.",
                        "exitCode", "Process exit code. Null when the process is still running.",
                        "stdoutSize", "Current stdout buffer size in characters.",
                        "stderrSize", "Current stderr buffer size in characters.",
                        "stdoutSummary", "Summary text for stdout output.",
                        "stderrSummary", "Summary text for stderr output.",
                        "startAt", "Command session start time.",
                        "endAt", "Command session end time. Null when the process is still running."
                )
        );
        Function<Map<String, Object>, String> invoker = params -> {
            cleanupExpiredHandles();
            CommandHandle handle = requireHandle(BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "id"));
            flushHandleBuffers(handle);
            return JSONObject.of(
                    "executionId", handle.executionId,
                    "desc", handle.desc,
                    "exitCode", handle.exitCode,
                    "stdoutSize", streamLength(handle.stdoutLogPath, handle.stdoutSourceBuffer),
                    "stderrSize", streamLength(handle.stderrLogPath, handle.stderrSourceBuffer),
                    "stdoutSummary", summarizeBuffer(handle.stdoutBuffer),
                    "stderrSummary", summarizeBuffer(handle.stderrBuffer),
                    "startAt", handle.startAt,
                    "endAt", handle.exitAt
            ).toJSONString();
        };
        return new BuiltinActionRegistry.BuiltinActionDefinition(createActionKey("inspect"), info, invoker);
    }

    /**
     * 用于读取指定后台 Builtin MetaAction 的输出内容
     *
     * @return 内建 MetaAction 定义数据，参数为进程 id 与读取流(stdout/stderr)，返回值为读取内容 JSON
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildCommandReadDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Command Session");
        tags.add("Command Read");
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(
                        "id", param("required", "string", "Command executionId returned by builtin::command::start."),
                        "stream", param("optional", "string", "Target stream to read. Allowed values: stdout, stderr. Default: stdout."),
                        "offset", param("optional", "int", "Start byte offset in the persisted stream log. Default: 0. Use nextOffset from a previous read to continue."),
                        "limit", param("optional", "int", "Maximum bytes to read from the persisted stream log. Default: 4096. Must be greater than 0.")
                ),
                "Purpose: read stdout or stderr content from a background command session with offset pagination. Returns: JSON with content, contentTruncated, nextOffset, and eof. Use when: inspect summaries are insufficient or a command failed and stderr/stdout details are needed. Notes: repeat with nextOffset until eof or enough evidence is collected.",
                tags,
                Set.of(createActionKey("overview")),
                Set.of(),
                false,
                JSONObject.of(
                        "executionId", "Command execution session id.",
                        "desc", "Command session description.",
                        "stream", "The stream that was read, stdout or stderr.",
                        "content", "Content read from the selected stream in this call.",
                        "contentTruncated", "Whether the content was truncated because of the read limit.",
                        "offset", "Read start offset used in this call.",
                        "nextOffset", "Suggested next offset for the following read.",
                        "eof", "Whether the stream has reached end-of-file and the process has already exited."
                )
        );
        Function<Map<String, Object>, String> invoker = params -> {
            cleanupExpiredHandles();
            CommandHandle handle = requireHandle(BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "id"));
            flushHandleBuffers(handle);
            String stream = BuiltinActionRegistry.BuiltinActionDefinition.optionalString(params, "stream", "stdout");
            if (!"stdout".equals(stream) && !"stderr".equals(stream)) {
                throw new IllegalArgumentException("参数 stream 只能为 stdout 或 stderr");
            }
            int offset = BuiltinActionRegistry.BuiltinActionDefinition.optionalInt(params, "offset", 0);
            int limit = BuiltinActionRegistry.BuiltinActionDefinition.optionalInt(params, "limit", DEFAULT_READ_LIMIT);
            if (offset < 0) {
                throw new IllegalArgumentException("参数 offset 必须大于等于 0");
            }
            if (limit <= 0) {
                throw new IllegalArgumentException("参数 limit 必须大于 0");
            }

            Path logPath = "stderr".equals(stream) ? handle.stderrLogPath : handle.stdoutLogPath;
            StreamChunk chunk = readChunk(logPath, offset, limit);
            boolean eof = !handle.isRunning() && chunk.nextOffset >= chunk.totalLength;

            return JSONObject.of(
                    "executionId", handle.executionId,
                    "desc", handle.desc,
                    "stream", stream,
                    "content", chunk.content,
                    "contentTruncated", chunk.truncated,
                    "offset", chunk.offset,
                    "nextOffset", chunk.nextOffset,
                    "eof", eof
            ).toJSONString();
        };
        return new BuiltinActionRegistry.BuiltinActionDefinition(createActionKey("read"), info, invoker);
    }

    /**
     * 用于取消指定后台命令的 Builtin MetaAction
     *
     * @return 内建 MetaAction 定义数据，参数为进程 id，返回值为是否成功取消
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildCommandCancelDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Command Session");
        tags.add("Command Cancel");
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of("id", param("required", "string", "Command executionId returned by builtin::command::start.")),
                "Purpose: terminate an existing background command session. Inputs: id from command::start. Returns: JSON with ok and executionId. Use when: the user explicitly asks to stop a command, or correction determines the running command is wrong, stuck, unsafe, or no longer needed. Notes: this has process side effects and should not be used as a status check.",
                tags,
                Set.of(),
                Set.of(createActionKey("overview")),
                false,
                JSONObject.of(
                        "ok", "Whether the command has been cancelled.",
                        "executionId", "Command execution session id."
                )
        );
        Function<Map<String, Object>, String> invoker = params -> {
            cleanupExpiredHandles();
            CommandHandle handle = requireHandle(BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "id"));
            flushHandleBuffers(handle);
            if (handle.process.isAlive()) {
                handle.process.destroy();
                waitProcessExit(handle.process, 200);
                if (handle.process.isAlive()) {
                    handle.process.destroyForcibly();
                    waitProcessExit(handle.process, 200);
                }
            }
            if (!handle.process.isAlive()) {
                try {
                    handle.exitCode = handle.process.exitValue();
                } catch (IllegalThreadStateException ignored) {
                }
                if (handle.exitAt == null) {
                    handle.exitAt = Instant.now();
                }
            }
            return JSONObject.of(
                    "ok", !handle.process.isAlive(),
                    "executionId", handle.executionId
            ).toJSONString();
        };
        return new BuiltinActionRegistry.BuiltinActionDefinition(createActionKey("cancel"), info, invoker);
    }

    /**
     * 用于列出全量后台进程的 Builtin MetaAction
     *
     * @return 内建 MetaAction 定义数据，无参数，返回值为后台进程集合 JSON
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildCommandOverviewDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Command Session");
        tags.add("Command Overview");
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(),
                "Purpose: list known background command sessions. Inputs: none. Returns: JSON with result array of session overviews containing executionId, desc, and exitCode. Use when: an id is missing or correction needs to discover active command sessions before inspect/read/cancel. Notes: this does not start, stop, or read a command.",
                tags,
                Set.of(createActionKey("start")),
                Set.of(createActionKey("inspect"), createActionKey("read"), createActionKey("cancel")),
                false,
                JSONObject.of(
                        "result", "Array of command session overview items.",
                        "result[].executionId", "Command execution session id for each overview item.",
                        "result[].desc", "Command session description for each overview item.",
                        "result[].exitCode", "Process exit code for each overview item. Null when still running."
                )
        );
        Function<Map<String, Object>, String> invoker = params -> {
            cleanupExpiredHandles();
            commandHandles.values().forEach(this::flushHandleBuffers);
            List<JSONObject> items = commandHandles.values().stream()
                    .sorted(Comparator.comparing(handle -> handle.startAt))
                    .map(handle -> JSONObject.of(
                            "executionId", handle.executionId,
                            "desc", handle.desc,
                            "exitCode", handle.exitCode
                    ))
                    .toList();
            return JSONObject.of("result", items).toJSONString();
        };
        return new BuiltinActionRegistry.BuiltinActionDefinition(createActionKey("overview"), info, invoker);
    }

    @Override
    public String createActionKey(String actionName) {
        return COMMAND_LOCATION + "::" + actionName;
    }

    private void monitorProcess(CommandHandle handle) {
        Thread.startVirtualThread(() -> {
            try {
                handle.exitCode = handle.process.waitFor();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                flushHandleBuffers(handle);
                handle.exitAt = Instant.now();
            }
        });
    }

    private void startOutputFlusher(CommandHandle handle) {
        Thread.startVirtualThread(() -> {
            try {
                while (handle.process.isAlive()) {
                    flushHandleBuffers(handle);
                    Thread.sleep(OUTPUT_FLUSH_INTERVAL.toMillis());
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                flushHandleBuffers(handle);
            }
        });
    }

    private void flushHandleBuffers(CommandHandle handle) {
        flushStreamBuffer(handle.stdoutSourceBuffer, handle.stdoutBuffer, handle.stdoutLogPath);
        flushStreamBuffer(handle.stderrSourceBuffer, handle.stderrBuffer, handle.stderrLogPath);
    }

    private void flushStreamBuffer(StringBuilder sourceBuffer, StringBuilder tailBuffer, Path logPath) {
        String chunk;
        synchronized (sourceBuffer) {
            if (sourceBuffer.isEmpty()) {
                return;
            }
            chunk = sourceBuffer.toString();
            sourceBuffer.setLength(0);
        }
        try {
            Files.writeString(logPath, chunk, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("写入命令输出日志失败: " + logPath, e);
        }
        synchronized (tailBuffer) {
            tailBuffer.append(chunk);
            trimTailBuffer(tailBuffer);
        }
    }

    private void trimTailBuffer(StringBuilder buffer) {
        if (buffer.length() <= COMMAND_SESSION_TAIL_LIMIT) {
            return;
        }
        buffer.delete(0, buffer.length() - COMMAND_SESSION_TAIL_LIMIT);
    }

    private void cleanupExpiredHandles() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, CommandHandle>> iterator = commandHandles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CommandHandle> entry = iterator.next();
            CommandHandle handle = entry.getValue();
            if (!isExpired(handle, now)) {
                continue;
            }
            flushHandleBuffers(handle);
            cleanupLogFiles(handle);
            iterator.remove();
        }
    }

    private boolean isExpired(CommandHandle handle, Instant now) {
        Instant exitTime = handle.exitAt;
        return exitTime != null && !exitTime.plus(COMMAND_SESSION_TTL).isAfter(now);
    }

    private Path createSessionLogPath(String executionId, String fileName) {
        try {
            Files.createDirectories(COMMAND_SESSION_LOG_DIR);
            Path file = COMMAND_SESSION_LOG_DIR.resolve(executionId + "-" + fileName);
            Files.deleteIfExists(file);
            Files.createFile(file);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("创建命令会话日志文件失败", e);
        }
    }

    private long streamLength(Path logPath, StringBuilder sourceBuffer) {
        long fileLength;
        try {
            fileLength = Files.exists(logPath) ? Files.size(logPath) : 0L;
        } catch (IOException e) {
            throw new IllegalStateException("读取命令会话日志长度失败", e);
        }
        synchronized (sourceBuffer) {
            return fileLength + sourceBuffer.length();
        }
    }

    private StreamChunk readChunk(Path logPath, int offset, int limit) {
        if (!Files.exists(logPath)) {
            return new StreamChunk("", 0, 0, false, 0);
        }
        try (RandomAccessFile raf = new RandomAccessFile(logPath.toFile(), "r")) {
            int totalLength = (int) raf.length();
            int safeOffset = Math.min(offset, totalLength);
            int nextOffset = Math.min(safeOffset + limit, totalLength);
            byte[] bytes = new byte[nextOffset - safeOffset];
            raf.seek(safeOffset);
            raf.readFully(bytes);
            String content = new String(bytes, StandardCharsets.UTF_8);
            return new StreamChunk(content, safeOffset, nextOffset, nextOffset < totalLength, totalLength);
        } catch (IOException e) {
            throw new IllegalStateException("读取命令会话日志失败", e);
        }
    }

    private void cleanupLogFiles(CommandHandle handle) {
        try {
            Files.deleteIfExists(handle.stdoutLogPath);
            Files.deleteIfExists(handle.stderrLogPath);
        } catch (IOException ignored) {
        }
    }

    private WrappedLaunchSpec wrapCommands(List<String> commands) {
        return ExecutionPolicyRegistry.INSTANCE.prepare(commands);
    }

    private CommandHandle requireHandle(String id) {
        CommandHandle handle = commandHandles.get(id);
        if (handle == null) {
            throw new IllegalArgumentException("未找到对应命令会话: " + id);
        }
        return handle;
    }

    private List<String> requireCommandArguments(Map<String, Object> params) {
        List<Map.Entry<String, Object>> unexpectedArgs = params.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(COMMAND_ARG_PREFIX))
                .filter(entry -> !COMMAND_ARG_PREFIX.equals(entry.getKey()) && !isNumberedCommandArg(entry.getKey()))
                .toList();
        if (!unexpectedArgs.isEmpty()) {
            throw new IllegalArgumentException("非法命令参数: " + unexpectedArgs.getFirst().getKey());
        }

        List<String> commands = new ArrayList<>();
        commands.add(BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, COMMAND_ARG_PREFIX));
        params.entrySet().stream()
                .filter(entry -> isNumberedCommandArg(entry.getKey()))
                .sorted(Comparator.comparingInt(entry -> commandArgIndex(entry.getKey())))
                .forEach(entry -> commands.add(BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, entry.getKey())));
        return commands;
    }

    private boolean isNumberedCommandArg(String key) {
        if (!key.startsWith(COMMAND_ARG_PREFIX) || COMMAND_ARG_PREFIX.equals(key)) {
            return false;
        }
        String suffix = key.substring(COMMAND_ARG_PREFIX.length()).trim();
        if (suffix.isEmpty()) {
            return false;
        }
        return suffix.chars().allMatch(Character::isDigit);
    }

    private int commandArgIndex(String key) {
        return Integer.parseInt(key.substring(COMMAND_ARG_PREFIX.length()).trim());
    }

    private String bufferSnapshot(StringBuilder buffer) {
        synchronized (buffer) {
            return buffer.toString();
        }
    }

    private String summarizeBuffer(StringBuilder buffer) {
        String snapshot = bufferSnapshot(buffer);
        if (snapshot.isBlank()) {
            return "";
        }
        List<String> lines = snapshot.lines().toList();
        if (lines.size() <= SUMMARY_MAX_LINES * 2) {
            return trimSummary(snapshot);
        }

        List<String> head = lines.subList(0, SUMMARY_MAX_LINES);
        List<String> tail = lines.subList(Math.max(lines.size() - SUMMARY_MAX_LINES, SUMMARY_MAX_LINES), lines.size());
        String summary = String.join("\n", head)
                + "\n...\n"
                + String.join("\n", tail);
        return trimSummary(summary);
    }

    private String trimSummary(String content) {
        if (content.length() <= SUMMARY_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, SUMMARY_MAX_LENGTH);
    }

    private void waitProcessExit(Process process, long millis) {
        try {
            process.waitFor(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private record StreamChunk(String content, int offset, int nextOffset, boolean truncated, int totalLength) {
    }

    @AllArgsConstructor
    private static class CommandHandle {
        private String executionId;
        private String desc;
        private List<String> commands;
        private Instant startAt;

        private Process process;

        private StringBuilder stdoutSourceBuffer;
        private StringBuilder stderrSourceBuffer;

        /**
         * stdout/stderr 摘要 tail
         */
        private StringBuilder stdoutBuffer;
        private StringBuilder stderrBuffer;

        private Path stdoutLogPath;
        private Path stderrLogPath;

        /**
         * 退出码：进程未结束时可为 null
         */
        private volatile Integer exitCode;
        private volatile Instant exitAt;

        boolean isRunning() {
            return exitCode == null && process.isAlive();
        }
    }
}
