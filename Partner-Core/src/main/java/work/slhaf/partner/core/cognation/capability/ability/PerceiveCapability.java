package work.slhaf.partner.core.cognation.capability.ability;

import work.slhaf.partner.core.cognation.submodule.perceive.pojo.User;

public interface PerceiveCapability {
    User getUser(String userInfo, String client);
    User getUser(String id);
    User addUser(String userInfo, String platform, String userNickName);
    void updateUser(User user);
}
