package work.slhaf.agent.module.modules.perceive.updater.relation_extractor;

import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.modules.perceive.updater.pojo.PerceiveChatResult;

public class RelationExtractor extends Model {

    private static volatile RelationExtractor relationExtractor;

    public static RelationExtractor getInstance() {
        if (relationExtractor == null) {
            synchronized (RelationExtractor.class) {
                if (relationExtractor == null) {
                    relationExtractor = new RelationExtractor();
                }
            }
        }
        return relationExtractor;
    }

    //TODO 完善关系提取与相应提示词
    public PerceiveChatResult execute(){
        return null;
    }

    @Override
    protected String modelKey() {
        return "relation_extractor";
    }
}
