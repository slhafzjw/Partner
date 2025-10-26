package work.slhaf.partner.core.action.entity;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.common.Constant;

import java.io.File;
import java.nio.file.Path;

import static work.slhaf.partner.common.Constant.Path.ACTION_PROGRAM;

/**
 * 行动链中的单一元素，实现{@link Runnable}接口，封装了调用外部行动程序的必要信息，可被执行
 */
@Data
public class MetaAction implements Comparable<MetaAction>, Runnable {

    /**
     * 行动key，用于标识与定位行动程序
     */
    private String key;
    /**
     * 行动程序可接受的参数，由调用处设置
     */
    private String[] params;
    /**
     * 行动结果，包括执行状态和相应内容(执行结果或者错误信息)
     */
    private Result result;
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
     */
    private MetaActionType type;

    @Override
    public int compareTo(@NotNull MetaAction metaAction) {
        return this.order - metaAction.order;
    }

    @Override
    public void run() {
        File action = loadFromFile();
        if (!action.exists()) {
            result = new Result();
            result.setSuccess(false);
            result.setData("Action file not found: " + action.getAbsolutePath());
        }
        try {
            switch (type) {
                case PLUGIN -> executePlugin(action);
                case MCP -> executeMcp(action);
                case SCRIPT -> executeScript(action);
            }
        } catch (Exception e) {
            result = new Result();
            result.setSuccess(false);
            result.setData(e.getMessage());
        }
    }

    private File loadFromFile() {
        return switch (type) {
            case PLUGIN -> Path.of(Constant.Path.ACTION_PROGRAM, key, "action.jar").toFile();
            case SCRIPT -> Path.of(ACTION_PROGRAM, key, "action.py").toFile();
            case MCP -> Path.of(ACTION_PROGRAM, key, "action.json").toFile();
        };
    }

    private void executePlugin(File actionFile) {

    }

    private void executeMcp(File actionFile) {

    }

    private void executeScript(File actionFile) {

    }

    @Data
    public static class Result {
        private boolean success;
        private String data;
    }

}
