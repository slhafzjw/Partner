package work.slhaf.partner.runtime.interaction.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class SpecializedPartnerInputData extends PartnerInputData {
    protected Map<String, String> payload;
}
