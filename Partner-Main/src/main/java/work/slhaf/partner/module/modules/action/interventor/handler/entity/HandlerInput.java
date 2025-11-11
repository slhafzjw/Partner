package work.slhaf.partner.module.modules.action.interventor.handler.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import lombok.Data;
import work.slhaf.partner.core.action.ActionCore.PhaserRecord;

@Data
public class HandlerInput {
    
    private List<HandlerInputData> data = new ArrayList<>();

    @Data
    public static class HandlerInputData{
        private String tendency;
        private String description;
        private InterventionType type;
        private LinkedHashMap<Integer,String> actions;
        private PhaserRecord record;
    }
}
