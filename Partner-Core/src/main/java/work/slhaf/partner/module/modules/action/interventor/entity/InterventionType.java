package work.slhaf.partner.module.modules.action.interventor.entity;

public enum InterventionType {
    /**
     * 追加行动: 追加至指定行动链序列之后才执行
     */
    APPEND, 

    /**
     * 插入行动: 指定行动链序列执行过程中即时新增并执行
     */
    INSERT, 

    /**
     * 重建行动: 重建指定行动链序列之后的所有行动内容
     */
    REBUILD, 

    /**
     * 删除行动: 删除指定行动链序列上的指定行动单元
     */
    DELETE,

    /**
     * 取消行动链: 中断并取消指定行动链的执行
     */
    CANCEL
}
