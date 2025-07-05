package work.slhaf.agent.module.modules.perceive.updater;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.agent.core.cognation.CognationCapability;
import work.slhaf.agent.core.cognation.CognationManager;
import work.slhaf.agent.core.cognation.submodule.perceive.PerceiveCapability;
import work.slhaf.agent.core.cognation.submodule.perceive.pojo.User;
import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.core.interaction.module.InteractionModule;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;
import work.slhaf.agent.module.modules.perceive.updater.pojo.PerceiveChatInput;
import work.slhaf.agent.module.modules.perceive.updater.pojo.PerceiveChatResult;
import work.slhaf.agent.module.modules.perceive.updater.relation_extractor.RelationExtractor;
import work.slhaf.agent.module.modules.perceive.updater.static_extractor.StaticExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 感知更新，异步
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class PerceiveUpdater extends Model implements InteractionModule {

    private static volatile PerceiveUpdater perceiveUpdater;

    private PerceiveCapability perceiveCapability;
    private CognationCapability cognationCapability;
    private InteractionThreadPoolExecutor executor;
    private RelationExtractor relationExtractor;
    private StaticExtractor staticExtractor;

    private List<Message> tempMessages;

    public static PerceiveUpdater getInstance() throws IOException, ClassNotFoundException {
        if (perceiveUpdater == null) {
            synchronized (PerceiveUpdater.class) {
                if (perceiveUpdater == null) {
                    perceiveUpdater = new PerceiveUpdater();
                    perceiveUpdater.setPerceiveCapability(CognationManager.getInstance());
                    perceiveUpdater.setCognationCapability(CognationManager.getInstance());
                    perceiveUpdater.setExecutor(InteractionThreadPoolExecutor.getInstance());
                    perceiveUpdater.setRelationExtractor(RelationExtractor.getInstance());
                    perceiveUpdater.setStaticExtractor(StaticExtractor.getInstance());
                    setModel(perceiveUpdater, ModelConstant.Prompt.PERCEIVE, true);
                }
            }
        }
        return perceiveUpdater;
    }

    @Override
    public void execute(InteractionContext context) throws IOException, ClassNotFoundException {
        executor.execute(() -> {
            boolean trigger = context.getModuleContext().getExtraContext().getBoolean("perceive_updater");
            if (!trigger){
                return;
            }
            tempMessages = new ArrayList<>(cognationCapability.getChatMessages());
            String userId = context.getUserId();
            PerceiveChatInput input = getPerceiveInput(userId);
            PerceiveChatResult perceiveChatResult = getPerceiveResult(input);
            User user = getTempUser(context, perceiveChatResult);
            user.setStaticMemory(perceiveChatResult.getStaticMemory());
            perceiveCapability.updateUser(user);
        });
    }

    private static User getTempUser(InteractionContext context, PerceiveChatResult perceiveChatResult) {
        User user = new User();
        user.setUuid(context.getUserId());
        user.setRelation(perceiveChatResult.getRelation());
        user.setImpressions(perceiveChatResult.getImpressions());
        user.setAttitude(perceiveChatResult.getAttitude());
        return user;
    }

    private PerceiveChatResult getPerceiveResult(PerceiveChatInput input) {
        ChatResponse response = singleChat(JSONObject.toJSONString(input));
        return JSONObject.parseObject(response.getMessage(), PerceiveChatResult.class);
    }

    private PerceiveChatInput getPerceiveInput(String userId) {
        HashMap<String,String> map = new HashMap<>();
        User user = perceiveCapability.getUser(userId);
        map.put("[用户昵称] <用户的昵称信息>",user.getNickName());
        map.put("[关系] <你与用户的关系>", user.getRelation());
        map.put("[态度] <你对于用户的态度>", user.getAttitude().toString());
        map.put("[印象] <你对于用户的印象>", user.getImpressions().toString());
        map.put("[静态记忆] <你关于用户的静态记忆>", user.getStaticMemory().toString());
        PerceiveChatInput input = new PerceiveChatInput();
        input.setPrimaryUserPerceive(map);
        input.setChatMessages(tempMessages);
        return input;
    }

    @Override
    protected String modelKey() {
        return "perceive_updater";
    }
}
