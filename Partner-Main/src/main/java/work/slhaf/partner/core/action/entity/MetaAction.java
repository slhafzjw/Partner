package work.slhaf.partner.core.action.entity;

import lombok.Data;

import java.util.Map;

/**
 * 行动链中的单一元素，封装了调用外部行动程序的必要信息与结果容器，可被{@link work.slhaf.partner.core.action.ActionCapability}执行
 */
@Data
public class MetaAction {

    /**
     * 行动name，用于标识行动程序
     */
    private String name;
    /**
     * 行动程序可接受的参数，由调用处设置
     */
    private Map<String, String> params;
    /**
     * 行动结果，包括执行状态和相应内容(执行结果或者错误信息)
     */
    private Result result = new Result();
    /**
     * 是否IO密集，用于决定使用何种线程池
     */
    private boolean io;
    /**
     * 行动程序类型，可分为 MCP、ORIGIN 两种，前者对应读取到的 MCP Tool、后者对应生成的临时行动程序
     */
    private MetaActionType type;

    /**
     * 当类型为 MCP 时，该字段对应相应 MCP Client 注册时生成的 id;
     * 当类型为 ORIGIN 时，该字段对应相应的磁盘路径字符串
     */
    private String location;

    /**
     * actionKey 将由 location+name 共同定位
     *
     * @return actionKey
     */
    public String getKey() {
        return location + "::" + name;
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
