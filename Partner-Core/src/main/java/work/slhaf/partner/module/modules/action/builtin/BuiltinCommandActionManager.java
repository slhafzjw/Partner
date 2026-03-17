package work.slhaf.partner.module.modules.action.builtin;

import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

class BuiltinCommandActionManager {

    private ConcurrentHashMap<String, CommandHandle> commandHandles = new ConcurrentHashMap<>();

    /**
     * 用于直接执行的 Builtin MetaAction
     *
     * @return 内建 MetaAction 定义数据，参数为常规命令列表，返回值为该命令的响应内容
     */
    BuiltinActionRegistry.BuiltinActionDefinition buildCommandExecuteDefinition() {
        return null;
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

        private Future<?> stdoutReaderTask;
        private Future<?> stderrReaderTask;

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
