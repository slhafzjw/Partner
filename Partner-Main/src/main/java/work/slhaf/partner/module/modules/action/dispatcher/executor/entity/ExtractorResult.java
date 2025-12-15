package work.slhaf.partner.module.modules.action.dispatcher.executor.entity;

import lombok.Data;

import java.util.Map;

@Data
public class ExtractorResult {
    private boolean ok;
    private Map<String, String> params;
}
