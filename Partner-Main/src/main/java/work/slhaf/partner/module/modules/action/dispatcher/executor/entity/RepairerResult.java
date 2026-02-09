package work.slhaf.partner.module.modules.action.dispatcher.executor.entity;

import lombok.Data;

import java.util.List;

/**
 * 行动修复结果，包含行动状态和修复后的参数
 */
@Data
public class RepairerResult {

    private RepairerStatus status;
    private List<String> fixedData;

    public enum RepairerStatus {
        /**
         * 成功修复: 携带修复后参数; 此种情况对应 Repairer 通过某种方式获取到了完整的参数(调用额外的行动)
         */
        OK,
        /**
         * 发送了自对话请求干预行动，这类一般是补充信息或者提供行动指导，后续必须再步入修复进程，但需要设置层级
         */
        ACQUIRE,
        /**
         * 修复失败(简单修复、自对话通道均出现错误，正常情况不应该出现)
         */
        FAILED
    }
}
