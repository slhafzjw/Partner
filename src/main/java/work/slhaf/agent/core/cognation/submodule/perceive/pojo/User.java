package work.slhaf.agent.core.cognation.submodule.perceive.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.serialize.PersistableObject;

import java.io.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class User extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String uuid;
    private String nickName;
    private HashMap<String/*platform*/,String> info = new HashMap<>();

    private String relation;
    private HashMap<LocalDate, String> events = new HashMap<>();
    private List<String> impressions = new ArrayList<>();
    private List<String> attitude = new ArrayList<>();
    private List<String> staticMemory = new ArrayList<>();

    public void addInfo(String platform,String userInfo) {
        this.info.put(platform, userInfo);
    }

    public static class Constant {
        public static class Relation {
            public static final String STRANGER = "stranger";
        }
    }
}
