package work.slhaf.partner.module.modules.perceive.updater.relation_extractor;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.common.chat.pojo.ChatResponse;
import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.api.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.flow.abstracts.AgentInteractionSubModule;
import work.slhaf.partner.core.cognation.cognation.CognationCapability;
import work.slhaf.partner.core.cognation.submodule.perceive.PerceiveCapability;
import work.slhaf.partner.core.cognation.submodule.perceive.pojo.User;
import work.slhaf.partner.core.interaction.data.context.InteractionContext;
import work.slhaf.partner.module.common.model.ModelConstant;
import work.slhaf.partner.module.modules.perceive.updater.relation_extractor.pojo.RelationExtractInput;
import work.slhaf.partner.module.modules.perceive.updater.relation_extractor.pojo.RelationExtractResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class RelationExtractor extends AgentInteractionSubModule<InteractionContext, RelationExtractResult> implements ActivateModel {

    private static volatile RelationExtractor relationExtractor;

    private CognationCapability cognationCapability;
    private PerceiveCapability perceiveCapability;

    private List<Message> tempMessages;

    private RelationExtractor(){
        modelSettings();
    }
    public static RelationExtractor getInstance() throws IOException, ClassNotFoundException {
        if (relationExtractor == null) {
            synchronized (RelationExtractor.class) {
                if (relationExtractor == null) {
                    relationExtractor = new RelationExtractor();
                }
            }
        }
        return relationExtractor;
    }

    @Override
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
        map.put("[静态记忆] <你对该用户的事实性记忆>", user.getStaticMemory().toString());
        RelationExtractInput input = new RelationExtractInput();
        input.setPrimaryUserPerceive(map);
        input.setChatMessages(tempMessages);
        return input;
    }

    @Override
    public String modelKey() {
        return "relation_extractor";
    }

    @Override
    public boolean withAwareness() {
        return true;
    }

    @Override
    public String promptModule() {
        return ModelConstant.Prompt.PERCEIVE;
    }
}
