package work.slhaf.partner.core.action;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.ActionInitFailedException;
import work.slhaf.partner.core.action.exception.ActionLoadFailedException;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static work.slhaf.partner.common.Constant.Path.ACTION_PROGRAM;

// TODO 后续需迁移至 SandboxRunner，作为容器内的监听逻辑
@SuppressWarnings("unchecked")
@Slf4j
class ActionWatchService {

    private final HashMap<Path, WatchKey> registeredPaths = new HashMap<>();
    private final LinkedHashMap<String, MetaActionInfo> existedMetaActions;
    private final ExecutorService virtualExecutor;

    public ActionWatchService(LinkedHashMap<String, MetaActionInfo> existedMetaActions, ExecutorService virtualExecutor) {
        this.existedMetaActions = existedMetaActions;
        this.virtualExecutor = virtualExecutor;
    }

    public void launch() {
        Path path = Path.of(ACTION_PROGRAM);
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
            virtualExecutor.execute(registerWatchTask(path, watchService));
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
                        WatchEvent<Path> event = (WatchEvent<Path>) e;
                        WatchEvent.Kind<Path> kind = event.kind();
                        Path context = event.context();
                        log.info("行动程序目录变更事件: {} - {}", kind.name(), context.toString());
                        Path thisDir = (Path) key.watchable();
                        //根据事件发生的目录进行分流，分为父目录事件和子程序事件
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
            if (!complete) return;
            try {
                MetaActionInfo newActionInfo = new MetaActionInfo(thisDir.toFile());
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
            if (nameWithoutExt.equals("action")) existedAction = true;
            else if (nameWithoutExt.equals("desc")) existedDesc = true;
        }
        return existedAction && existedDesc;
    }

    private void handleParentDirEvent(WatchEvent.Kind<Path> kind, Path thisDir, Path context, WatchService watchService) {
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
            public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName().startsWith(".")) return FileVisitResult.CONTINUE;
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
                MetaActionInfo actionInfo = new MetaActionInfo(f);
                existedMetaActions.put(f.getName(), actionInfo);
                log.info("行动程序[{}]已加载", actionInfo.getKey());
            } catch (ActionLoadFailedException e) {
                log.warn("行动程序未加载: {}", e.getMessage());
            }
        }

    }
}