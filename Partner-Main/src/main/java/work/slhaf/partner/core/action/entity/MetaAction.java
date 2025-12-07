package work.slhaf.partner.core.action.entity;

import static work.slhaf.partner.common.Constant.Path.ACTION_PROGRAM;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import lombok.Data;

/**
 * 行动链中的单一元素，封装了调用外部行动程序的必要信息与结果容器，可被{@link work.slhaf.partner.core.action.ActionCapability}执行
 */
@Data
public class MetaAction implements Comparable<MetaAction> {

    /**
     * 行动key，用于标识与定位行动程序
     */
    private String key;
    /**
     * 行动程序可接受的参数，由调用处设置
     */
    private Map<String, String> params;
    /**
     * 行动结果，包括执行状态和相应内容(执行结果或者错误信息)
     */
    private final Result result = new Result();
    /**
     * 执行顺序，升序排列
     */
    private int order;
    /**
     * 是否IO密集，用于决定使用何种线程池
     */
    private boolean io;
    /**
     * 行动程序类型，可分为PLUGIN(jar文件)、SCRIPT(Python程序)、MCP(MCP服务)
     * .
     */
    private MetaActionType type;

    private Path path;

    public Path checkAndGetPath() {
        path = switch (type) {
            case PLUGIN -> Path.of(ACTION_PROGRAM, key, "action.jar");
            case SCRIPT -> Path.of(ACTION_PROGRAM, key, "action.py");
            case MCP -> Path.of(ACTION_PROGRAM, key, "action.json");
        };
        File action = path.toFile();
        if (!action.exists()) {
            result.setStatus(ResultStatus.FAILED);
            result.setData("Action file not found: " + action.getAbsolutePath());
        }
        return path;
    }

    @Override
    public int compareTo(@NotNull MetaAction metaAction) {
        return this.order - metaAction.order;
    }

    @Data
    public static class Result {
        private ResultStatus status = ResultStatus.WAITING;
        private String data = null;
    }

    public enum ResultStatus {
        SUCCESS,
        FAILED,
        WAITING
    }

}
