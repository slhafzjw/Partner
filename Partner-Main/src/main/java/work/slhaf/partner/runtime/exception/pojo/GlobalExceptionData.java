package work.slhaf.partner.runtime.exception.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.common.entity.PersistableObject;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.io.Serial;
import java.util.HashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class GlobalExceptionData extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String exceptionMessage;

    protected HashMap<String, PartnerRunningFlowContext> context = PartnerRunningFlowContext.getInstance();
    protected Long exceptionTime = System.currentTimeMillis();
}
