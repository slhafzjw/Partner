package work.slhaf.partner.module.modules.action.interventor.recognizer.entity;

import lombok.Data;
import work.slhaf.partner.core.action.entity.ExecutableAction;

import java.util.HashMap;
import java.util.Map;

@Data
public class RecognizerResult {

    private boolean ok;

    /**
     * <h4>将被干预的‘执行中行动’</h4>
     * key: 干预倾向
     * <br/>
     * value: 干预倾向将作用的行动数据
     */
    private Map<String, ExecutableAction> executingInterventions = new HashMap<>();

    /**
     * <h4>将被干预的‘等待中行动’</h4>
     * key: 干预倾向
     * <br/>
     * value: 干预倾向将作用的行动数据
     */
    private Map<String, ExecutableAction> preparedInterventions = new HashMap<>();
}
