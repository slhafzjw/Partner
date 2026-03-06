package work.slhaf.partner.module.modules.action.executor.entity;

import lombok.Data;

import java.util.Map;

@Data
public class ExtractorResult {
    private boolean ok;
    private Map<String, Object> params;
}
