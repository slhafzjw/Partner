package work.slhaf.partner.module.modules.action.builtin;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.runner.execution.CommandExecutionService;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static work.slhaf.partner.core.action.ActionCore.BUILTIN_LOCATION;

class BuiltinCommandActionManager {

    private static final String COMMAND_LOCATION = BUILTIN_LOCATION + "::" + "command";

    private final Set<String> basicTags = Set.of("Builtin MetaAction", "System Command Tool");

    private ConcurrentHashMap<String, CommandHandle> commandHandles = new ConcurrentHashMap<>();
    private CommandExecutionService commandExecutionService = CommandExecutionService.INSTANCE;

    /**
     * 用于直接执行的 Builtin MetaAction
     *
     * @return 内建 MetaAction 定义数据，参数为常规命令列表，返回值为该命令的响应内容
     */
    BuiltinActionRegistry.BuiltinActionDefinition buildCommandExecuteDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Command Execution");
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of("Command Arguments", "Command Arguments"),
                "Execute any allowed system commands and get result instantly, the number of arguments is not limited.",
                tags,
                Set.of(),
                Set.of(createActionKey("inspect")),
                false,
                JSONObject.of("result", "Command execution result.")
        );
        Function<Map<String, Object>, String> invoker = params -> {
            List<String> commands = params.keySet().stream()
                    .map(paramKey -> BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, paramKey))
                    .toList();
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
    BuiltinActionRegistry.BuiltinActionDefinition buildCommandStartDefinition() {
        return null;
    }

    /**
     * 用于返回指定后台 Builtin MetaAction 的摘要内容
     *
     * @return 内建 MetaAction 定义数据，参数为进程 id，返回值为摘要内容(CommandInspectData)
     */
    BuiltinActionRegistry.BuiltinActionDefinition buildCommandInspectDefinition() {
        return null;
    }

    /**
     * 用于读取指定后台 Builtin MetaAction 的输出内容
     *
     * @return 内建 MetaAction 定义数据，参数为进程 id 与读取流(stdout/stderr)，返回值为读取内容(CommandReadData)
     */
    BuiltinActionRegistry.BuiltinActionDefinition buildCommandReadDefinition() {
        return null;
    }

    /**
     * 用于取消指定后台命令的 Builtin MetaAction
     *
     * @return 内建 MetaAction 定义数据，参数为进程 id，返回值为是否成功取消
     */
    BuiltinActionRegistry.BuiltinActionDefinition buildCommandCancelDefinition() {
        return null;
    }

    /**
     * 用于列出全量后台进程的 Builtin MetaAction
     *
     * @return 内建 MetaAction 定义数据，无参数，返回值为后台进程集合(CommandOverviewItem)
     */
    BuiltinActionRegistry.BuiltinActionDefinition buildCommandOverviewDefinition() {
        return null;
    }

    private String createActionKey(String actionName) {
        return COMMAND_LOCATION + "::" + actionName;
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
