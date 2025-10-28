package work.slhaf.partner.runtime.interaction.data;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentInputData;

@EqualsAndHashCode(callSuper = true)
@Data
public class PartnerInputData extends AgentInputData {
    protected String userNickName;
    protected String platform;
    protected boolean single;
}
