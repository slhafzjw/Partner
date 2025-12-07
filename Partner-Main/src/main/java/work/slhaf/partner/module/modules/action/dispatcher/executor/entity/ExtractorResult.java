package work.slhaf.partner.module.modules.action.dispatcher.executor.entity;

import java.util.Map;

import lombok.Data;

@Data
public class ExtractorResult {
    private boolean ok;
    private Map<String, String> params;
}
