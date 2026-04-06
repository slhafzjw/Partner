package work.slhaf.partner.runtime.exception.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.framework.agent.common.entity.PersistableObject;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
public class GlobalExceptionData extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;
    protected Long exceptionTime = System.currentTimeMillis();
    private String exceptionMessage;
}
