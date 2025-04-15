package work.slhaf.agent.modules.memory.pojo;

import lombok.Data;

import java.util.List;

@Data
public class User {
    private String uuid;
    private List<String> info;
    private String nickName;
}
