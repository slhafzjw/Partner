package work.slhaf.partner.core.memory.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import work.slhaf.partner.api.common.entity.PersistableObject;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SliceRef extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String unitId;
    private String sliceId;
}
