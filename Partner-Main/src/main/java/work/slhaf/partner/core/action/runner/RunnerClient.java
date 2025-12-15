package work.slhaf.partner.core.action.runner;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.core.action.entity.GeneratedData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaAction.Result;
import work.slhaf.partner.core.action.entity.MetaAction.ResultStatus;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
public abstract class RunnerClient {

    protected final Map<String, MetaActionInfo> existedMetaActions;
    protected final ExecutorService executor;

    /**
     * ActionCore 将注入虚拟线程池
     */
    public RunnerClient(Map<String, MetaActionInfo> existedMetaActions, ExecutorService executor) {
        this.existedMetaActions = existedMetaActions;
        this.executor = executor;
    }

    /**
     * 执行行动程序
     */
    public void run(MetaAction metaAction) {
        // 获取已存在行动列表
        Result result = metaAction.getResult();
        if (!result.getStatus().equals(ResultStatus.WAITING)) {
            return;
        }
        RunnerResponse response = doRun(metaAction);
        result.setData(response.getData());
        result.setStatus(response.isOk() ? ResultStatus.SUCCESS : ResultStatus.FAILED);
    }

    //TODO 将执行划分为 MCP、OriginalScript两种类型，SCRIPT、PLUGIN、MCP的分类不再必要
    protected abstract RunnerResponse doRun(MetaAction metaAction);

    /**
     * 将临时行动程序放入等待队列，根据其是否需要持久序列化，监听其执行状态，执行成功则持久序列化
     *
     * @throws IOException
     */
    public Path getPathAndSerialize(MetaAction tempAction, GeneratedData generatedData) throws IOException {
        String code = generatedData.getCode();
        String codeType = generatedData.getCodeType();

        Path path = doBuildTempPath(tempAction, codeType);
        tempAction.setPath(path);
        doSerialize(tempAction, code, codeType);
        if (generatedData.isSerialize()) {
            waitingSerialize(tempAction, code, codeType, generatedData.getResponseSchema());
        }
        return path;
    }

    private void waitingSerialize(MetaAction tempAction, String code, String codeType, JSONObject jsonObject) {
        executor.execute(() -> {
            Result result = tempAction.getResult();
            while (true) {
                switch (result.getStatus()) {
                    case ResultStatus.WAITING -> {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    case ResultStatus.FAILED -> {
                        break;
                    }
                    case ResultStatus.SUCCESS -> {
                        tempAction.resetPath();
                        try {
                            doSerialize(tempAction, code, codeType);
                        } catch (IOException e) {
                            log.error("行动程序序列化出错: {}", tempAction.getKey(), e);
                        }
                    }
                }
            }
        });

    }

    protected abstract Path doBuildTempPath(MetaAction tempAction, String codeType);

    protected abstract void doSerialize(MetaAction tempAction, String code, String codeType) throws IOException;

    /**
     * 列出执行环境下的系统依赖情况
     */
    public abstract JSONObject listSysDependencies();

    @Data
    protected static class RunnerResponse {
        private boolean ok;
        private String data;
    }
}