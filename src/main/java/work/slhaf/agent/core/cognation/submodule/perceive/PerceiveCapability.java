package work.slhaf.agent.core.cognation.submodule.perceive;

import work.slhaf.agent.core.cognation.submodule.perceive.pojo.User;

public interface PerceiveCapability {
    User getUser(String userInfo, String client);
    User getUser(String id);
    User addUser(String userInfo, String platform, String userNickName);
}
