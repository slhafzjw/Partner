package work.slhaf.partner.core.action;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.common.vector.VectorClient;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.cache.ActionCacheData;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustMetaData;
import work.slhaf.partner.core.action.exception.ActionInitFailedException;
import work.slhaf.partner.core.action.exception.ActionLoadFailedException;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static work.slhaf.partner.common.Constant.Path.ACTION_PROGRAM;

@SuppressWarnings("FieldMayBeFinal")
@CapabilityCore(value = "action")
@Slf4j
public class ActionCore extends PartnerCore<ActionCore> {

    /**
     * 对应本次交互即将执行或将要放置在行动池的预备任务，因此将以本次交互的uuid为键，其起到的作用相当于暂时的模块上下文
     */
    private HashMap<String, List<ActionData>> preparedActions = new HashMap<>();

    /**
     * 待确认任务，以userId区分不同用户，因为需要跨请求确认
     */
    private HashMap<String, List<ActionData>> pendingActions = new HashMap<>();

    /**
     * 语义缓存与行为倾向映射
     */
    private List<ActionCacheData> actionCache = new ArrayList<>();

    private final Lock cacheLock = new ReentrantLock();

    private final ExecutorService platformExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final LinkedHashMap<String, MetaActionInfo> existedMetaActions = new LinkedHashMap<>();

    public ActionCore() throws IOException, ClassNotFoundException {
        new ActionWatchService().launch();
    }

    @CapabilityMethod
    public synchronized void putPendingActions(String userId, ActionData actionData) {
        pendingActions.computeIfAbsent(userId, k -> {
            List<ActionData> temp = new ArrayList<>();
            temp.add(actionData);
            return temp;
        });
    }

    @CapabilityMethod
    public synchronized List<ActionData> popPendingAction(String userId) {
        List<ActionData> infos = pendingActions.get(userId);
        pendingActions.remove(userId);
        return infos;
    }

    @CapabilityMethod
    public synchronized void putPreparedAction(String uuid, ActionData actionData) {
        preparedActions.computeIfAbsent(uuid, k -> {
            List<ActionData> temp = new ArrayList<>();
            temp.add(actionData);
            return temp;
        });
    }

    @CapabilityMethod
    public synchronized List<ActionData> popPreparedAction(String userId) {
        List<ActionData> infos = preparedActions.get(userId);
        preparedActions.remove(userId);
        return infos;
    }

    @CapabilityMethod
    public List<ActionData> listPreparedAction(String userId) {
        return preparedActions.get(userId);
    }

    @CapabilityMethod
    public List<ActionData> listPendingAction(String userId) {
        return pendingActions.get(userId);
    }

    /**
     * 计算输入内容的语义向量，根据与{@link ActionCacheData#getInputVector()}的相似度挑取缓存，后续将根据评估结果来更新计数
     *
     * @param input 本次输入内容
     * @return 命中的行为倾向集合
     */
    @CapabilityMethod
    public List<String> selectTendencyCache(String input) {
        if (!VectorClient.status) {
            return null;
        }
        VectorClient vectorClient = VectorClient.INSTANCE;
        //计算本次输入的向量
        float[] vector = vectorClient.compute(input);
        if (vector == null) return null;
        //与现有缓存比对，将匹配到的收集并返回
        return actionCache.parallelStream()
                .filter(ActionCacheData::isActivated)
                .filter(data -> {
                    double compared = vectorClient.compare(vector, data.getInputVector());
                    return compared > data.getThreshold();
                })
                .map(ActionCacheData::getTendency)
                .collect(Collectors.toList());
    }

    @CapabilityMethod
    public void updateTendencyCache(CacheAdjustData data) {
        VectorClient vectorClient = VectorClient.INSTANCE;
        List<CacheAdjustMetaData> list = data.getMetaDataList();
        String input = data.getInput();
        float[] inputVector = vectorClient.compute(input);

        List<CacheAdjustMetaData> matchAndPassed = new ArrayList<>();
        List<CacheAdjustMetaData> matchNotPassed = new ArrayList<>();
        List<CacheAdjustMetaData> notMatchPassed = new ArrayList<>();

        for (CacheAdjustMetaData metaData : list) {
            if (metaData.isHit() && metaData.isPassed()) {
                matchAndPassed.add(metaData);
            } else if (metaData.isHit()) {
                matchNotPassed.add(metaData);
            } else if (!metaData.isPassed()) {
                notMatchPassed.add(metaData);
            }
        }

        platformExecutor.execute(() -> adjustMatchAndPassed(matchAndPassed, inputVector, input, vectorClient));
        platformExecutor.execute(() -> adjustMatchNotPassed(matchNotPassed, vectorClient));
        platformExecutor.execute(() -> adjustNotMatchPassed(notMatchPassed, inputVector, input, vectorClient));
    }

    /**
     * 命中缓存且评估通过时
     *
     * @param matchAndPassed 该类型的带调整缓存信息列表
     * @param inputVector    本次输入内容的语义向量
     * @param vectorClient   向量客户端
     */
    private void adjustMatchAndPassed(List<CacheAdjustMetaData> matchAndPassed, float[] inputVector, String
            input, VectorClient vectorClient) {
        matchAndPassed.forEach(adjustData -> {
            //获取原始缓存条目
            String tendency = adjustData.getTendency();
            ActionCacheData primaryCacheData = selectCacheData(tendency);
            if (primaryCacheData == null) {
                return;
            }
            primaryCacheData.updateAfterMatchAndPassed(inputVector, vectorClient, input);
        });
    }

    /**
     * 针对命中缓存、但评估未通过的条目与输入进行处理
     *
     * @param matchNotPassed 该类型的带调整缓存信息列表
     * @param vectorClient   向量客户端
     */
    private void adjustMatchNotPassed(List<CacheAdjustMetaData> matchNotPassed, VectorClient vectorClient) {
        List<ActionCacheData> toRemove = new ArrayList<>();
        matchNotPassed.forEach(adjustData -> {
            //获取原始缓存条目
            String tendency = adjustData.getTendency();
            ActionCacheData primaryCacheData = selectCacheData(tendency);
            if (primaryCacheData == null) {
                return;
            }
            boolean remove = primaryCacheData.updateAfterMatchNotPassed(vectorClient);
            if (remove) {
                toRemove.add(primaryCacheData);
            }

        });
        cacheLock.lock();
        actionCache.removeAll(toRemove);
        cacheLock.unlock();
    }

    /**
     * 针对未命中但评估通过的缓存做出调整:
     * <ol>
     *     <h3>如果存在缓存条目</h3>
     *     <li>
     *         若已生效，但此时未匹配到则说明尚未生效或者阈值、向量{@link ActionCacheData#getInputVector()}存在问题，调低阈值，同时带权移动平均
     *     </li>
     *     <li>
     *         若未生效，则只增加计数并带权移动平均
     *     </li>
     * </ol>
     * 如果不存在缓存条目，则新增并填充字段
     *
     * @param notMatchPassed 该类型的带调整缓存信息列表
     * @param inputVector    本次输入内容的语义向量
     * @param input          本次输入内容
     * @param vectorClient   向量客户端
     */
    private void adjustNotMatchPassed(List<CacheAdjustMetaData> notMatchPassed, float[] inputVector, String
            input, VectorClient vectorClient) {
        notMatchPassed.forEach(adjustData -> {
            //获取原始缓存条目
            String tendency = adjustData.getTendency();
            ActionCacheData primaryCacheData = selectCacheData(tendency);
            float[] tendencyVector = vectorClient.compute(tendency);
            if (primaryCacheData == null) {
                actionCache.add(new ActionCacheData(tendency, tendencyVector, inputVector, input));
                return;
            }
            primaryCacheData.updateAfterNotMatchPassed(input, inputVector, tendencyVector, vectorClient);
        });
    }

    private ActionCacheData selectCacheData(String tendency) {
        for (ActionCacheData actionCacheData : actionCache) {
            if (actionCacheData.getTendency().equals(tendency)) {
                return actionCacheData;
            }
        }
        log.warn("[{}] 未找到行为倾向[{}]对应的缓存条目，可能是代码逻辑存在错误", getCoreKey(), tendency);
        return null;
    }

    @Override
    protected String getCoreKey() {
        return "action-core";
    }

    @SuppressWarnings("unchecked")
    private class ActionWatchService {

        private HashMap<Path, WatchKey> registeredPaths = new HashMap<>();

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
                log.info("[{}] 行动程序目录监听器已启动", getCoreKey());
                while (true) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                        List<WatchEvent<?>> events = key.pollEvents();
                        for (WatchEvent<?> e : events) {
                            WatchEvent<Path> event = (WatchEvent<Path>) e;
                            WatchEvent.Kind<Path> kind = event.kind();
                            Path context = event.context();
                            log.info("[{}] 行动程序目录变更事件: {} - {}", getCoreKey(), kind.name(), context.toString());
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
                    log.warn("[{}] 行动信息重新加载失败，触发行为: {}", getCoreKey(), kind.name());
                }
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                // DELETE 事件将会把该 MetaActionInfo 从记录中移除
                existedMetaActions.remove(thisDir.toString());
            }
        }

        private boolean checkComplete(Path thisDir) {
            File[] files = thisDir.toFile().listFiles();
            if (files == null) {
                log.error("[{}]当前目录无法访问: [{}]", getCoreKey(), thisDir);
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
                    log.error("[{}] 新增行动程序目录监听失败: {}", getCoreKey(), path, e);
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
                    log.info("[{}] 行动程序[{}]已加载", getCoreKey(), actionInfo.getKey());
                } catch (ActionLoadFailedException e) {
                    log.warn("[{}] 行动程序未加载: {}", getCoreKey(), e.getMessage());
                }
            }

        }
    }
}
