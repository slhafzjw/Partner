package work.slhaf.partner.module.modules.action.interventor.recognizer.entity;

import lombok.Data;
import work.slhaf.partner.core.action.ActionCore;

import java.util.HashMap;
import java.util.Map;

@Data
public class RecognizerResult {

    private boolean ok;

    /**
     * key: 干预倾向
     * <br/>
     * value: 干预倾向将作用的 phaser 记录
     */
    private Map<String, ActionCore.PhaserRecord> interventions = new HashMap<>();

}
