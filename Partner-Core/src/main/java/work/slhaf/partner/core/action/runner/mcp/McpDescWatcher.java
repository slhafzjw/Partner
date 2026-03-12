package work.slhaf.partner.core.action.runner.mcp;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.core.action.runner.support.DirectoryWatchSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

@Slf4j
public class McpDescWatcher implements AutoCloseable {

    private final Path root;
    private final McpMetaRegistry mcpMetaRegistry;
    private final DirectoryWatchSupport watchSupport;

    public McpDescWatcher(Path root, McpMetaRegistry mcpMetaRegistry, ExecutorService executor) throws IOException {
        this.root = root;
        this.mcpMetaRegistry = mcpMetaRegistry;
        this.watchSupport = new DirectoryWatchSupport(new DirectoryWatchSupport.Context(root), executor, true, () -> mcpMetaRegistry.loadDirectory(root))
                .onCreate(this::handleUpsert)
                .onModify(this::handleUpsert)
                .onDelete(this::handleDelete)
                .onOverflow((thisDir, context) -> mcpMetaRegistry.reconcile(root));
    }

    public void start() {
        watchSupport.start();
        log.info("DescMcp 文件监听注册完毕");
    }

    private void handleUpsert(Path thisDir, Path context) {
        if (context == null || Files.isDirectory(context) || !mcpMetaRegistry.isValidDescFile(context.getFileName().toString())) {
            return;
        }
        if (!mcpMetaRegistry.addOrUpdate(context)) {
            mcpMetaRegistry.remove(context);
        }
    }

    private void handleDelete(Path thisDir, Path context) {
        if (context == null || !mcpMetaRegistry.isValidDescFile(context.getFileName().toString())) {
            return;
        }
        mcpMetaRegistry.remove(context);
    }

    @Override
    public void close() throws Exception {
        watchSupport.close();
    }
}
