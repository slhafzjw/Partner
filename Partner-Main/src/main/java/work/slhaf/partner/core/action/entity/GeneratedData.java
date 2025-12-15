package work.slhaf.partner.core.action.entity;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.List;

@Data
public class GeneratedData {
    private List<String> dependencies;
    private String code;
    private String codeType;
    private boolean serialize;
    private JSONObject responseSchema;
}
