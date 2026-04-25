package work.slhaf.partner.module.memory.selector;

import lombok.AllArgsConstructor;
import lombok.Data;
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class MemoryInputEntry {
    private LocalDateTime receivedDateTime;
    private List<RunningFlowContext.InputEntry> inputs;
}
