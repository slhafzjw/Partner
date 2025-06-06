package work.slhaf.agent.core.interaction.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InteractionOutputData {
    private String content;
    private String userInfo;
}
