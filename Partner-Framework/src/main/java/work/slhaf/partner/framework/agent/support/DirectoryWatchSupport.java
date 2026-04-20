package work.slhaf.partner.framework.agent.support;

import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.*;

@Slf4j
public class DirectoryWatchSupport implements Closeable {

    private final Context ctx;
    private final Map<WatchEvent.Kind<?>, EventHandler> handlers = new HashMap<>();
    private final ExecutorService executor;
    private final int watchDepth;
    private final InitLoader initLoader;

    public DirectoryWatchSupport(Context ctx, ExecutorService executor, int watchDepth, InitLoader initLoader) {
        if (watchDepth < -1) {
            throw new IllegalArgumentException("watchDepth must be -1 or greater: " + watchDepth);
        }
        this.ctx = ctx;
        this.executor = executor;
        this.watchDepth = watchDepth;
        this.initLoader = initLoader;
    }

    public DirectoryWatchSupport onCreate(EventHandler handler) {
        ctx.kinds().add(ENTRY_CREATE);
        handlers.put(ENTRY_CREATE, handler);
        return this;
    }

    public DirectoryWatchSupport onModify(EventHandler handler) {
        ctx.kinds().add(ENTRY_MODIFY);
        handlers.put(ENTRY_MODIFY, handler);
        return this;
    }

    public DirectoryWatchSupport onDelete(EventHandler handler) {
        ctx.kinds().add(ENTRY_DELETE);
        handlers.put(ENTRY_DELETE, handler);
        return this;
    }

    public DirectoryWatchSupport onOverflow(EventHandler handler) {
        ctx.kinds().add(OVERFLOW);
        handlers.put(OVERFLOW, handler);
        return this;
    }

    public void start() {
        registerPath();
        if (initLoader != null) {
            initLoader.load();
        }
        executor.execute(buildWatchTask());
    }

    public Context context() {
        return ctx;
    }

    public boolean isWatching(Path dir) {
        return ctx.watchKeys().values().stream().anyMatch(dir::equals);
    }

    public void registerDirectory(Path dir) throws IOException {
        registerDirectoryTree(dir);
    }

    private void registerDirectoryInternal(Path dir) throws IOException {
        if (!java.nio.file.Files.isDirectory(dir) || isWatching(dir)) {
            return;
        }
        WatchEvent.Kind<?>[] kindsArray = ctx.kinds().toArray(WatchEvent.Kind[]::new);
        WatchKey key = dir.register(ctx.watchService(), kindsArray);
        ctx.watchKeys().put(key, dir);
    }

    private void registerPath() {
        try {
            registerDirectoryTree(ctx.root());
        } catch (IOException e) {
            log.error("监听目录注册失败: ", e);
        }
    }

    private void registerDirectoryTree(Path dir) throws IOException {
        if (!Files.isDirectory(dir) || !isWithinDepth(dir)) {
            return;
        }

        registerDirectoryInternal(dir);
        if (!shouldTraverseChildren(dir)) {
            return;
        }

        try (Stream<Path> walk = Files.list(dir).filter(Files::isDirectory)) {
            for (Path child : walk.toList()) {
                registerDirectoryTree(child);
            }
        }
    }

    private boolean isWithinDepth(Path dir) {
        if (watchDepth == -1) {
            return true;
        }
        return depthOf(dir) <= watchDepth;
    }

    private boolean shouldTraverseChildren(Path dir) {
        return watchDepth == -1 || depthOf(dir) < watchDepth;
    }

    private int depthOf(Path dir) {
        Path normalizedRoot = ctx.root().toAbsolutePath().normalize();
        Path normalizedDir = dir.toAbsolutePath().normalize();
        if (normalizedDir.equals(normalizedRoot)) {
            return 0;
        }
        if (!normalizedDir.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Directory is outside watched root: " + dir);
        }
        return normalizedRoot.relativize(normalizedDir).getNameCount();
    }

    private Runnable buildWatchTask() {
        return () -> {
            String rootStr = ctx.root().toString();
            log.info("行动程序目录监听器已启动，监听目录: {}", rootStr);
            while (true) {
                WatchKey key = null;
                try {
                    key = ctx.watchService().take();
                    List<WatchEvent<?>> events = key.pollEvents();
                    for (WatchEvent<?> event : events) {
                        WatchEvent.Kind<?> kind = event.kind();
                        Object context = event.context();
                        log.debug("文件目录监听事件: {} - {} - {}", rootStr, kind.name(), context);
                        Path thisDir = (Path) key.watchable();
                        Path resolvedContext = context instanceof Path path ? thisDir.resolve(path) : null;
                        if (kind == ENTRY_CREATE && resolvedContext != null && Files.isDirectory(resolvedContext)) {
                            try {
                                registerDirectoryTree(resolvedContext);
                            } catch (IOException e) {
                                log.error("监听目录注册失败: {}", resolvedContext, e);
                            }
                        }
                        EventHandler handler = handlers.get(kind);
                        if (handler == null) {
                            continue;
                        }
                        try {
                            handler.handle(thisDir, resolvedContext);
                        } catch (Exception e) {
                            log.error("监听事件处理失败: dir={}, kind={}, context={}", thisDir, kind.name(), resolvedContext, e);
                        }
                    }
                } catch (InterruptedException e) {
                    log.info("监听线程被中断，准备退出...");
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    log.info("WatchService 已关闭，监听线程退出。");
                    break;
                } finally {
                    if (key != null) {
                        boolean valid = key.reset();
                        if (!valid) {
                            log.info("WatchKey 已失效，停止监听该目录: {}", key.watchable());
                            ctx.watchKeys().remove(key);
                            if (key.watchable().equals(ctx.root())) {
                                try {
                                    Files.createDirectories(ctx.root());
                                    registerPath();
                                    if (initLoader != null) {
                                        initLoader.load();
                                    }
                                } catch (IOException e) {
                                    log.error("重建根目录并重新注册监听失败: {}", ctx.root(), e);
                                }
                            }
                        }
                    }
                }
            }
        };
    }

    @Override
    public void close() throws IOException {
        ctx.watchService().close();
        ctx.watchKeys().clear();
    }

    public interface EventHandler {
        void handle(Path thisDir, Path context);
    }

    public interface InitLoader {
        void load();
    }

    public record Context(Path root, WatchService watchService, Map<WatchKey, Path> watchKeys,
                          Set<WatchEvent.Kind<?>> kinds) {
        public Context(Path root) throws IOException {
            this(root, FileSystems.getDefault().newWatchService(), new HashMap<>(), new LinkedHashSet<>());
        }
    }
}
