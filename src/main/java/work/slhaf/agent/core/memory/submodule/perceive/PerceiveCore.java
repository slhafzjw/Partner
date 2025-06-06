package work.slhaf.agent.core.memory.submodule.perceive;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.serialize.PersistableObject;
import work.slhaf.agent.core.memory.submodule.perceive.pojo.User;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class PerceiveCore extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户列表
     */
    private List<User> users = new ArrayList<>();

}
