package work.slhaf.partner.module.memory.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivationProfile {
    private Float activationWeight;
    private Float diffusionWeight;
    private Float contextIndependenceWeight;
}
