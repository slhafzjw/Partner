package work.slhaf.agent.common.exception_handler.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.pojo.PersistableObject;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.session.SessionManager;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
public class GlobalExceptionData extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String exceptionMessage;

    protected InteractionContext context;
    protected SessionManager sessionManager;
    protected MemoryManager memoryManager;
    protected Long exceptionTime;
}
