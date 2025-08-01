package work.slhaf.partner.core.cognation.submodule.perceive;

import work.slhaf.partner.api.factory.capability.annotation.Capability;
import work.slhaf.partner.core.cognation.submodule.perceive.pojo.User;

@Capability(value = "perceive")
public interface PerceiveCapability {
    User getUser(String userInfo, String client);
    User getUser(String id);
    User addUser(String userInfo, String platform, String userNickName);
    void updateUser(User user);
}
