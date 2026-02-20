package work.slhaf.partner.core.perceive;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.core.perceive.pojo.User;

@Capability(value = "perceive")
public interface PerceiveCapability {
    User getUser(String userInfo, String client);

    User getUser(String id);

    User addUser(String userInfo, String platform, String userNickName);

    void updateUser(User user);
}
