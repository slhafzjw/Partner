package work.slhaf.agent.module.modules.perceive.updater.relation_extractor;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.core.cognation.CognationCapability;
import work.slhaf.agent.core.cognation.CognationManager;
import work.slhaf.agent.core.cognation.submodule.perceive.PerceiveCapability;
import work.slhaf.agent.core.cognation.submodule.perceive.pojo.User;
import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;
import work.slhaf.agent.module.modules.perceive.updater.relation_extractor.pojo.RelationExtractInput;
import work.slhaf.agent.module.modules.perceive.updater.relation_extractor.pojo.RelationExtractResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
public class RelationExtractor extends Model {

    private static volatile RelationExtractor relationExtractor;

    private CognationCapability cognationCapability;
    private PerceiveCapability perceiveCapability;

    private List<Message> tempMessages;


    public static RelationExtractor getInstance() throws IOException, ClassNotFoundException {
        if (relationExtractor == null) {
            synchronized (RelationExtractor.class) {
                if (relationExtractor == null) {
                    relationExtractor = new RelationExtractor();
                    relationExtractor.setCognationCapability(CognationManager.getInstance());
                    relationExtractor.setPerceiveCapability(CognationManager.getInstance());
                    setModel(relationExtractor, ModelConstant.Prompt.PERCEIVE, true);
                }
            }
        }
        return relationExtractor;
    }

    //TODO 完善关系提取与相应提示词
    public RelationExtractResult execute(InteractionContext context){
        tempMessages = new ArrayList<>(cognationCapability.getChatMessages());
        String userId = context.getUserId();
        RelationExtractInput input = getRelationInput(userId);
        RelationExtractResult relationExtractResult = getRelationResult(input);
        User user = getTempUser(context, relationExtractResult);
        perceiveCapability.updateUser(user);
        return relationExtractResult;
    }


    private User getTempUser(InteractionContext context, RelationExtractResult relationExtractResult) {
        User user = new User();
        user.setUuid(context.getUserId());
        user.setRelation(relationExtractResult.getRelation());
        user.setImpressions(relationExtractResult.getImpressions());
        user.setAttitude(relationExtractResult.getAttitude());
        return user;
    }

    private RelationExtractResult getRelationResult(RelationExtractInput input) {
        ChatResponse response = singleChat(JSONObject.toJSONString(input));
        return JSONObject.parseObject(response.getMessage(), RelationExtractResult.class);
    }

    private RelationExtractInput getRelationInput(String userId) {
        HashMap<String,String> map = new HashMap<>();
        User user = perceiveCapability.getUser(userId);
        map.put("[用户昵称] <用户的昵称信息>",user.getNickName());
        map.put("[关系] <你与用户的关系>", user.getRelation());
        map.put("[态度] <你对于用户的态度>", user.getAttitude().toString());
        map.put("[印象] <你对于用户的印象>", user.getImpressions().toString());
        map.put("[静态记忆] <你对该用户的静态记忆>", user.getStaticMemory().toString());
        RelationExtractInput input = new RelationExtractInput();
        input.setPrimaryUserPerceive(map);
        input.setChatMessages(tempMessages);
        return input;
    }

    @Override
    protected String modelKey() {
        return "relation_extractor";
    }
}
