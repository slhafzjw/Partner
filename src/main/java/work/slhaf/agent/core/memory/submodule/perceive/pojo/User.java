package work.slhaf.agent.core.memory.submodule.perceive.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.serialize.PersistableObject;

import java.io.Serial;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class User extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String uuid;
    private List<String> info;
    private String nickName;
}
