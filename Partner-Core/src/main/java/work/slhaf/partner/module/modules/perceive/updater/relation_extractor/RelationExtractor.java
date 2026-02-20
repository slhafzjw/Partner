package work.slhaf.partner.module.modules.perceive.updater.relation_extractor;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.module.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.core.perceive.pojo.User;
import work.slhaf.partner.module.modules.perceive.updater.relation_extractor.entity.RelationExtractInput;
import work.slhaf.partner.module.modules.perceive.updater.relation_extractor.entity.RelationExtractResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class RelationExtractor extends AbstractAgentModule.Sub<PartnerRunningFlowContext, RelationExtractResult> implements ActivateModel {
    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;
    private List<Message> tempMessages;

    @Override
    public RelationExtractResult execute(PartnerRunningFlowContext context) {
        tempMessages = new ArrayList<>(cognationCapability.getChatMessages());
        String userId = context.getUserId();
        RelationExtractInput input = getRelationInput(userId);
        RelationExtractResult relationExtractResult = getRelationResult(input);
        User user = getTempUser(context, relationExtractResult);
        perceiveCapability.updateUser(user);
        return relationExtractResult;
    }

    private User getTempUser(PartnerRunningFlowContext context, RelationExtractResult relationExtractResult) {
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
        HashMap<String, String> map = new HashMap<>();
        User user = perceiveCapability.getUser(userId);
        map.put("[用户昵称] <用户的昵称信息>", user.getNickName());
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
    public boolean withBasicPrompt() {
        return true;
    }
}
