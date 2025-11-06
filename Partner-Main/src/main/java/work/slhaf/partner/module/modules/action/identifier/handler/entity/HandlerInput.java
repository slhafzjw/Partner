package work.slhaf.partner.module.modules.action.identifier.handler.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import lombok.Data;

@Data
public class HandlerInput {
    
    private List<HandlerInputData> data = new ArrayList<>();

    @Data
    public static class HandlerInputData{
        private String tendency;
        private String description;
        private InterventionType type;
        private LinkedHashMap<Integer,String> actions;
    }
}
