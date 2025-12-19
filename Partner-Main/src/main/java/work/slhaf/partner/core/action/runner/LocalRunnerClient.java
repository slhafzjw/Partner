package work.slhaf.partner.core.action.runner;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.core.action.entity.McpData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.MetaActionType;
import work.slhaf.partner.core.action.exception.ActionInitFailedException;
import work.slhaf.partner.core.action.exception.ActionLoadFailedException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static work.slhaf.partner.common.Constant.Path.ACTION_PROGRAM;
import static work.slhaf.partner.common.Constant.Path.TMP_ACTION_DIR_LOCAL;

@Slf4j
public class LocalRunnerClient extends RunnerClient {

    public LocalRunnerClient(Map<String, MetaActionInfo> existedMetaActions, ExecutorService executor, @Nullable String actionWatchPath) {
        super(existedMetaActions, executor);
        ActionWatchService watchService = new ActionWatchService(actionWatchPath);
        watchService.launch();
    }

    @Override
    protected RunnerResponse doRun(MetaAction metaAction) {
        RunnerResponse response;
        try {
            response = switch (metaAction.getType()) {
                case MetaActionType.MCP -> doRunWithMcp(metaAction);
                case MetaActionType.ORIGIN -> doRunWithOrigin(metaAction);
            };
        } catch (Exception e) {
            response = new RunnerResponse();
            response.setOk(false);
            response.setData(e.getLocalizedMessage());
        }
        return response;
    }

    private RunnerResponse doRunWithOrigin(MetaAction metaAction) {
        RunnerResponse response = new RunnerResponse();
        File file = new File(metaAction.getLocation());
        String ext = FileUtil.getSuffix(file);
        if (ext == null || ext.isEmpty()) {
            response.setOk(false);
            response.setData("未知文件类型");
            return response;
        }
        String[] commands = buildCommands(ext, metaAction.getParams(), file.getAbsolutePath());
        SystemExecResult execResult = exec(commands);
        response.setOk(execResult.isOk());
        response.setData(execResult.getTotal());
        return response;
    }

    //TODO 后续需在加载时、或者通过配置文件获取可用命令并注册匹配
    private String[] buildCommands(String ext, Map<String, Object> params, String absolutePath) {
        String command = switch (ext) {
            case "py" -> "python";
            case "sh" -> "bash";
            default -> null;
        };
        if (command == null) {
            return null;
        }
        String[] commands = new String[params.size() + 2];
        commands[0] = command;
        commands[1] = absolutePath;
        AtomicInteger paramCount = new AtomicInteger(2);
        params.forEach((param, value) -> {
            commands[paramCount.getAndIncrement()] = "--" + param + "=" + value.toString();
        });
        return commands;
    }

    private RunnerResponse doRunWithMcp(MetaAction metaAction) {
        RunnerResponse response = new RunnerResponse();
        McpSyncClient mcpClient = mcpClients.get(metaAction.getLocation());
        McpSchema.CallToolRequest callToolRequest = McpSchema.CallToolRequest.builder()
                .name(metaAction.getName())
                .arguments(metaAction.getParams())
                .build();
        McpSchema.CallToolResult callToolResult = mcpClient.callTool(callToolRequest);
        response.setOk(callToolResult.isError());
        response.setData(callToolResult.structuredContent().toString());
        return response;
    }

    @Override
    public String buildTmpPath(MetaAction tempAction, String codeType) {
        return Path.of(TMP_ACTION_DIR_LOCAL, System.currentTimeMillis() + "-" + tempAction.getKey() + codeType).toString();
    }

    @Override
    public void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException {
        Path path = Path.of(tempAction.getLocation());
        File file = path.toFile();
        file.createNewFile();
        Files.writeString(path, code);
    }

    @Override
    public void persistSerialize(MetaActionInfo metaActionInfo, McpData mcpData) {
        throw new UnsupportedOperationException("Unimplemented method 'doPersistSerialize'");
    }

    @Override
    public JSONObject listSysDependencies() {
        // 先只列出系统/环境的 Python 依赖
        // TODO 在 AgentConfigManager 内配置启用的脚本语言及对应的扩展名
        // 这里的逻辑后续需要替换为“根据 AgentConfigManager 读取到的脚本语言启用情况，遍历并列出当前系统环境依赖”
        // 还需要将返回值调整为相应的数据类
        // 后续还需要将不同语言的处理逻辑分散到不同方法内，这里为了验证，先写死在当前方法
        JSONObject sysDependencies = new JSONObject();
        sysDependencies.put("language", "Python");
        JSONArray dependencies = sysDependencies.putArray("dependencies");
        SystemExecResult pyResult = exec("pip", "list", "--format=freeze");
        System.out.println(pyResult);
        if (pyResult.isOk()) {
            List<String> resultList = pyResult.getResultList();
            for (String result : resultList) {
                JSONObject element = dependencies.addObject();
                String[] split = result.split("==");
                element.put("name", split[0]);
                element.put("version", split[1]);
            }
        } else {
            JSONObject element = dependencies.addObject();
            element.put("error", pyResult.getTotal());
        }
        return sysDependencies;
    }

    private SystemExecResult exec(String... command) {
        SystemExecResult result = new SystemExecResult();
        List<String> output = new ArrayList<>();
        List<String> error = new ArrayList<>();

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(false)  // 分开读
                    .start();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        output.add(line);
                    }
                } catch (Exception ignored) {
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
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
            result.setTotal(String.join("\n",
                    output.isEmpty() ? error : output));

        } catch (Exception e) {
            result.setOk(false);
            result.setTotal(e.getMessage());
        }

        return result;
    }

    @Data
    private static class SystemExecResult {
        private boolean ok;
        private String total;
        private List<String> resultList;
    }

    private class ActionWatchService {

        private final HashMap<Path, WatchKey> registeredPaths = new HashMap<>();
        private final String actionWatchPath;

        private ActionWatchService(String actionWatchPath) {
            this.actionWatchPath = actionWatchPath;
        }

        private void launch() {
            Path path = Path.of(actionWatchPath != null ? actionWatchPath : ACTION_PROGRAM);
            scanActions(path.toFile());
            launchActionDirectoryWatcher(path);
        }

        private void launchActionDirectoryWatcher(Path path) {
            WatchService watchService;
            try {
                watchService = FileSystems.getDefault().newWatchService();
                setupShutdownHook(watchService);
                registerParentToWatch(path, watchService);
                registerSubToWatch(path, watchService);
                executor.execute(registerWatchTask(path, watchService));
            } catch (IOException e) {
                throw new ActionInitFailedException("行动程序目录监听器启动失败", e);
            }
        }

        private void setupShutdownHook(WatchService watchService) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    watchService.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        private Runnable registerWatchTask(Path path, WatchService watchService) {
            return () -> {
                log.info("行动程序目录监听器已启动");
                while (true) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                        List<WatchEvent<?>> events = key.pollEvents();
                        for (WatchEvent<?> e : events) {
                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> event = (WatchEvent<Path>) e;
                            WatchEvent.Kind<Path> kind = event.kind();
                            Path context = event.context();
                            log.info("行动程序目录变更事件: {} - {}", kind.name(), context.toString());
                            Path thisDir = (Path) key.watchable();
                            // 根据事件发生的目录进行分流，分为父目录事件和子程序事件
                            if (thisDir.equals(path)) {
                                handleParentDirEvent(kind, thisDir, context, watchService);
                            } else {
                                handleSubDirEvent(kind, thisDir);
                            }
                        }
                    } catch (InterruptedException e) {
                        log.info("监听线程被中断，准备退出...");
                        Thread.currentThread().interrupt(); // 恢复中断标志
                        break;
                    } catch (ClosedWatchServiceException e) {
                        log.info("WatchService 已关闭，监听线程退出。");
                        break;
                    }
                }
            };
        }

        private void handleSubDirEvent(WatchEvent.Kind<Path> kind, Path thisDir) {
            // path为触发本次行动的文件的路径(当前位于某个action目录下)
            // 先判定发生的目录前缀是否匹配(action、desc)，否则忽略
            if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                // CREATE、MODIFY 事件将触发一次检测，看当前thisDir中action和desc是否都具备，如果通过则尝试加载(put)。
                boolean complete = checkComplete(thisDir);
                if (!complete)
                    return;
                try {
                    MetaActionInfo newActionInfo = new MetaActionInfo();
                    existedMetaActions.put(thisDir.toString(), newActionInfo);
                } catch (ActionLoadFailedException e) {
                    log.warn("行动信息重新加载失败，触发行为: {}", kind.name());
                }
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                // DELETE 事件将会把该 MetaActionInfo 从记录中移除
                existedMetaActions.remove(thisDir.toString());
            }
        }

        private boolean checkComplete(Path thisDir) {
            File[] files = thisDir.toFile().listFiles();
            if (files == null) {
                log.error("当前目录无法访问: [{}]", thisDir);
                return false;
            }
            boolean existedAction = false;
            boolean existedDesc = false;
            for (File file : files) {
                String fileName = file.getName();
                String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
                if (nameWithoutExt.equals("action"))
                    existedAction = true;
                else if (nameWithoutExt.equals("desc"))
                    existedDesc = true;
            }
            return existedAction && existedDesc;
        }

        private void handleParentDirEvent(WatchEvent.Kind<Path> kind, Path thisDir, Path context,
                                          WatchService watchService) {
            Path path = Path.of(thisDir.toString(), context.toString());
            // MODIFY 事件不进行处理
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                try {
                    path.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                } catch (IOException e) {
                    log.error("新增行动程序目录监听失败: {}", path, e);
                }
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                WatchKey remove = registeredPaths.remove(path);
                remove.cancel();
            }
        }

        private void registerSubToWatch(Path path, WatchService watchService) throws IOException {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs)
                        throws IOException {
                    if (dir.getFileName().startsWith("."))
                        return FileVisitResult.CONTINUE;
                    WatchKey key = dir.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                    registeredPaths.put(dir, key);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        private void registerParentToWatch(Path path, WatchService watchService) throws IOException {
            WatchKey key = path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            registeredPaths.put(path, key);
        }

        private void scanActions(File file) {
            if (!file.exists() || file.isFile()) {
                throw new ActionInitFailedException("未找到行动程序目录: " + file.getAbsolutePath());
            }
            File[] files = file.listFiles();
            if (files == null) {
                throw new ActionInitFailedException("目录无法访问: " + file.getAbsolutePath());
            }
            for (File f : files) {
                try {
                    MetaActionInfo actionInfo = new MetaActionInfo();
                    existedMetaActions.put(f.getName(), actionInfo);
                    log.info("行动程序[{}]已加载", actionInfo.getKey());
                } catch (ActionLoadFailedException e) {
                    log.warn("行动程序加载失败", e);
                }
            }

        }

    }

}
