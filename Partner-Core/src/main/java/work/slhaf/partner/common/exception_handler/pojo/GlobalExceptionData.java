package work.slhaf.partner.common.exception_handler.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.common.serialize.PersistableObject;
import work.slhaf.partner.core.cognation.CognationManager;
import work.slhaf.partner.core.interaction.data.context.InteractionContext;
import work.slhaf.partner.core.session.SessionManager;

import java.io.Serial;
import java.util.HashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class GlobalExceptionData extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String exceptionMessage;

    protected HashMap<String, InteractionContext> context;
    protected SessionManager sessionManager;
    protected CognationManager cognationManager;
    protected Long exceptionTime;
}
