package work.slhaf.agent.common.exception_handler.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.serialize.PersistableObject;
import work.slhaf.agent.core.cognation.CognationManager;
import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.core.session.SessionManager;

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
