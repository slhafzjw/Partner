package work.slhaf.agent.core.cognation.submodule.perceive;

import work.slhaf.agent.core.cognation.submodule.perceive.pojo.User;
import work.slhaf.agent.module.modules.perceive.updater.pojo.PerceiveChatResult;

public interface PerceiveCapability {
    User getUser(String userInfo, String client);
    User getUser(String id);
    User addUser(String userInfo, String platform, String userNickName);
    void updateUser(PerceiveChatResult perceiveChatResult, String userId);
}
