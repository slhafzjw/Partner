package work.slhaf.partner.module.modules.action.builtin;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.runner.execution.CommandExecutionService;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static work.slhaf.partner.core.action.ActionCore.BUILTIN_LOCATION;

class BuiltinCommandActionProvider implements BuiltinActionProvider {

    private static final String COMMAND_LOCATION = BUILTIN_LOCATION + "::" + "command";
    private static final String COMMAND_ARG_PREFIX = "arg";
    private static final int DEFAULT_READ_LIMIT = 4096;
    private static final int SUMMARY_MAX_LINES = 5;
    private static final int SUMMARY_MAX_LENGTH = 2048;

    private final Set<String> basicTags = Set.of("Builtin MetaAction", "System Command Tool");

    private final ConcurrentHashMap<String, CommandHandle> commandHandles = new ConcurrentHashMap<>();
    private final CommandExecutionService commandExecutionService = CommandExecutionService.INSTANCE;

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
                Map.of("arg / argN", "Command arguments. Use arg for first argument, arg1/arg2... for remaining arguments."),
                "Execute any allowed system commands and get result instantly, the number of arguments is not limited.",
                tags,
                Set.of(),
                Set.of(createActionKey("inspect")),
                false,
                JSONObject.of("result", "Command execution result text.")
        );
        Function<Map<String, Object>, String> invoker = params -> {
            List<String> commands = requireCommandArguments(params);
            CommandExecutionService.Result result = commandExecutionService.exec(commands);
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
                Map.of(
                        "desc", "Command session description.",
                        "arg / argN", "Command arguments. Use arg for first argument, arg1/arg2... for remaining arguments."
                ),
                "Start a background command session and return execution id.",
                tags,
                Set.of(),
                Set.of(createActionKey("inspect")),
                false,
                JSONObject.of("executionId", "Command execution session id.")
        );
        Function<Map<String, Object>, String> invoker = params -> {
            String desc = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "desc");
            List<String> commands = requireCommandArguments(params);
            CommandExecutionService.CommandSession session = commandExecutionService.createSessionTask(commands);
            String executionId = UUID.randomUUID().toString();
            CommandHandle handle = new CommandHandle(
                    executionId,
                    desc,
                    commands,
                    Instant.now(),
                    session.getProcess(),
                    session.getStdoutBuffer(),
                    session.getStderrBuffer(),
                    null,
                    null
            );
            commandHandles.put(executionId, handle);
            monitorProcess(handle);
            return JSONObject.of("executionId", executionId).toJSONString();
        };
        return new BuiltinActionRegistry.BuiltinActionDefinition(createActionKey("start"), info, invoker);
    }

    /**
     * 用于返回指定后台 Builtin MetaAction 的摘要内容
     *
     * @return 内建 MetaAction 定义数据，参数为进程 id，返回值为摘要内容(CommandInspectData)
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildCommandInspectDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Command Session");
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of("id", "Command session id."),
                "Inspect a background command session.",
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
            CommandHandle handle = requireHandle(BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "id"));
            CommandInspectData data = new CommandInspectData(
                    handle.executionId,
                    handle.desc,
                    handle.exitCode,
                    bufferLength(handle.stdoutBuffer),
                    bufferLength(handle.stderrBuffer),
                    summarizeBuffer(handle.stdoutBuffer),
                    summarizeBuffer(handle.stderrBuffer),
                    handle.startAt,
                    handle.exitAt
            );
            return toJson(data);
        };
        return new BuiltinActionRegistry.BuiltinActionDefinition(createActionKey("inspect"), info, invoker);
    }

    /**
     * 用于读取指定后台 Builtin MetaAction 的输出内容
     *
     * @return 内建 MetaAction 定义数据，参数为进程 id 与读取流(stdout/stderr)，返回值为读取内容(CommandReadData)
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildCommandReadDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Command Session");
        tags.add("Command Read");
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(
                        "id", "Command execution session id.",
                        "stream", "Target stream, stdout or stderr. Default stdout.",
                        "offset", "Read start offset. Default 0.",
                        "limit", "Read max length. Default 4096."
                ),
                "Read output from a background command session.",
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
            CommandHandle handle = requireHandle(BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "id"));
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

            StringBuilder buffer = "stderr".equals(stream) ? handle.stderrBuffer : handle.stdoutBuffer;
            String snapshot = bufferSnapshot(buffer);
            int safeOffset = Math.min(offset, snapshot.length());
            int nextOffset = Math.min(safeOffset + limit, snapshot.length());
            String content = snapshot.substring(safeOffset, nextOffset);
            boolean truncated = nextOffset < snapshot.length();
            boolean eof = !handle.isRunning() && nextOffset >= snapshot.length();

            CommandReadData data = new CommandReadData(
                    handle.executionId,
                    handle.desc,
                    stream,
                    content,
                    truncated,
                    safeOffset,
                    nextOffset,
                    eof
            );
            return toJson(data);
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
                Map.of("id", "Command session id."),
                "Cancel a background command session.",
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
            CommandHandle handle = requireHandle(BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "id"));
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
     * @return 内建 MetaAction 定义数据，无参数，返回值为后台进程集合(CommandOverviewItem)
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildCommandOverviewDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Command Session");
        tags.add("Command Overview");
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(),
                "List all background command sessions.",
                tags,
                Set.of(createActionKey("start")),
                Set.of(createActionKey("inspect"), createActionKey("read"), createActionKey("cancel")),
                false,
                JSONObject.of(
                        "result", "Array of command session overview items.",
                        "result.executionId", "Command execution session id for each overview item.",
                        "result.desc", "Command session description for each overview item.",
                        "result.exitCode", "Process exit code for each overview item. Null when still running."
                )
        );
        Function<Map<String, Object>, String> invoker = params -> {
            List<JSONObject> items = commandHandles.values().stream()
                    .sorted(Comparator.comparing(handle -> handle.startAt))
                    .map(handle -> {
                        CommandOverviewItem item = new CommandOverviewItem(
                                handle.executionId,
                                handle.desc,
                                handle.exitCode
                        );
                        JSONObject json = new JSONObject();
                        json.put("executionId", item.executionId);
                        json.put("desc", item.desc);
                        json.put("exitCode", item.exitCode);
                        return json;
                    })
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
                handle.exitAt = Instant.now();
            }
        });
    }

    private CommandHandle requireHandle(String id) {
        CommandHandle handle = commandHandles.get(id);
        if (handle == null) {
            throw new IllegalArgumentException("未找到对应命令会话: " + id);
        }
        return handle;
    }

    private List<String> requireCommandArguments(Map<String, Object> params) {
        List<Map.Entry<String, Object>> entries = params.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(COMMAND_ARG_PREFIX))
                .sorted(Comparator.comparingInt(entry -> commandArgIndex(entry.getKey())))
                .toList();
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("缺少命令参数");
        }
        List<String> commands = new ArrayList<>();
        for (Map.Entry<String, Object> entry : entries) {
            commands.add(BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, entry.getKey()));
        }
        return commands;
    }

    private int commandArgIndex(String key) {
        String suffix = key.substring(COMMAND_ARG_PREFIX.length()).trim();
        if (suffix.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private int bufferLength(StringBuilder buffer) {
        synchronized (buffer) {
            return buffer.length();
        }
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

    private String toJson(CommandInspectData data) {
        JSONObject result = new JSONObject();
        result.put("executionId", data.executionId);
        result.put("desc", data.desc);
        result.put("exitCode", data.exitCode);
        result.put("stdoutSize", data.stdoutSize);
        result.put("stderrSize", data.stderrSize);
        result.put("stdoutSummary", data.stdoutSummary);
        result.put("stderrSummary", data.stderrSummary);
        result.put("startAt", data.startAt);
        result.put("endAt", data.endAt);
        return result.toJSONString();
    }

    private String toJson(CommandReadData data) {
        JSONObject result = new JSONObject();
        result.put("executionId", data.executionId);
        result.put("desc", data.desc);
        result.put("stream", data.stream);
        result.put("content", data.content);
        result.put("contentTruncated", data.contentTruncated);
        result.put("offset", data.offset);
        result.put("nextOffset", data.nextOffset);
        result.put("eof", data.eof);
        return result.toJSONString();
    }

    @AllArgsConstructor
    private static class CommandHandle {
        private String executionId;
        private String desc;
        private List<String> commands;
        private Instant startAt;

        private Process process;

        /**
         * stdout 输出内容
         */
        private StringBuilder stdoutBuffer;
        /**
         * stderr 输出内容
         */
        private StringBuilder stderrBuffer;

        /**
         * 退出码：进程未结束时可为 null
         */
        private volatile Integer exitCode;
        private volatile Instant exitAt;

        boolean isRunning() {
            return exitCode == null && process.isAlive();
        }
    }

    @AllArgsConstructor
    private static class CommandInspectData {
        private String executionId;
        private String desc;

        private Integer exitCode;

        private int stdoutSize;
        private int stderrSize;

        /**
         * stdout 摘要，v1 暂取头五行与最后五行，中间省略，最多 2k 字符; 如果内容过短则全取
         */
        private String stdoutSummary;
        /**
         * stderr 摘要，v1 暂取头五行与最后五行，中间省略，最多 2k 字符; 如果内容过短则全取
         */
        private String stderrSummary;

        private Instant startAt;
        private Instant endAt;
    }

    @AllArgsConstructor
    private static class CommandReadData {
        private String executionId;
        private String desc;

        private String stream;
        /**
         * 本次从指定 stream 中读取到的内容
         */
        private String content;
        private boolean contentTruncated;

        /**
         * 本次读取起点
         */
        private int offset;
        /**
         * 下次读取起点
         */
        private int nextOffset;
        /**
         * 读取是否已达末尾（进程退出、stream 及缓冲区均读取完毕)
         */
        private boolean eof;
    }

    @AllArgsConstructor
    private static class CommandOverviewItem {
        private String executionId;
        private String desc;
        private Integer exitCode;
    }
}
