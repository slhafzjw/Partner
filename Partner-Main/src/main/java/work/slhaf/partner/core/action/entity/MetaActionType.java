package work.slhaf.partner.core.action.entity;

public enum MetaActionType {
    /**
     * 将调用的 MCP 工具，可包括远程、本地任意服务
     */
    MCP,
    /**
     * 适用于‘临时生成’的行动程序，在生成后根据序列化选项及执行情况，进行持久化
     */
    ORIGIN
}
