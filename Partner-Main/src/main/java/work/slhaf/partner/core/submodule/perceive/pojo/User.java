package work.slhaf.partner.core.submodule.perceive.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.common.entity.PersistableObject;

import java.io.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class User extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String uuid;
    private String nickName;
    private HashMap<String/*platform*/, String> info = new HashMap<>();

    private String relation = Constant.Relation.STRANGER;
    //    private HashMap<LocalDate, String> events = new HashMap<>();
    private List<String> impressions = new ArrayList<>();
    private List<String> attitude = new ArrayList<>();
    private LinkedHashMap<LocalDate,String> relationChange = new LinkedHashMap<>();
    private HashMap<String,String> staticMemory = new HashMap<>();

    public void addInfo(String platform, String userInfo) {
        this.info.put(platform, userInfo);
    }

    public void updateRelationChange(String changeReason){
        relationChange.put(LocalDate.now(),changeReason);
    }
    public void updateRelationChange(LocalDate date, String changeReason){
        relationChange.put(date,changeReason);
    }
    public void updateRelationChange(LinkedHashMap<LocalDate,String> tempRelationChange){
        relationChange.putAll(tempRelationChange);
    }

    public static class Constant {
        public static class Relation {
            public static final String STRANGER = "陌生";
        }
    }
}
