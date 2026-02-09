package work.slhaf.partner.module.modules.action.interventor.entity;

import lombok.Data;

import java.util.List;

@Data
public class MetaIntervention {
    /**
     * 干预数据类型
     */
    private InterventionType type;
    /**
     * 干预数据对应的行动链序列
     */
    private int order;
    /**
     * 干预数据所需的行动key列表
     */
    private List<String> actions;
}