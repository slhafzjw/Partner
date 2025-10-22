package work.slhaf.partner.core.action.entity;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class MetaAction implements Comparable<MetaAction> {
    //行动key
    private String key;
    //行动参数
    private String[] params;
    //行动回应
    private String response;
    //执行顺序，升序排列
    private int order;
    private boolean io;

    @Override
    public int compareTo(@NotNull MetaAction metaAction) {
        return this.order - metaAction.order;
    }

    private void execute() {

    }
}
