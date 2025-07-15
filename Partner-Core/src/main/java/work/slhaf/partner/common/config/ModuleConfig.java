package work.slhaf.partner.common.config;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ModuleConfig {
    private String className;
    private String type;
    private String path;

    public static class Constant {
        public static final String INTERNAL = "internal";
        public static final String EXTERNAL = "external";
    }
}
