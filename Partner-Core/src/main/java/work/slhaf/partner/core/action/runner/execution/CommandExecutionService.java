package work.slhaf.partner.core.action.runner.execution;

import lombok.Data;

import java.util.List;
import java.util.Map;

public interface CommandExecutionService {

    String[] buildCommands(String ext, Map<String, Object> params, String absolutePath);

    Result exec(String... command);

    @Data
    class Result {
        private boolean ok;
        private String total;
        private List<String> resultList;
    }
}
