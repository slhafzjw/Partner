package work.slhaf.partner.module.modules.action.dispatcher.executor.entity;

import lombok.Data;

import java.util.Map;

@Data
public class GeneratorInput {
    private String actionName;
    private Map<String, Object> params;
    private String description;
    private Map<String, String> paramsDescription;
}
