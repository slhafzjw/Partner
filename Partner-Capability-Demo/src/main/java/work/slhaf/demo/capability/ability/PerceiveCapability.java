package work.slhaf.demo.capability.ability;

import work.slhaf.demo.capability.interfaces.Capability;

@Capability(value = "perceive")
public interface PerceiveCapability {
    String getUser(String id);
    String addUser(String userInfo, String platform, String userNickName);
    void updateUser(String user);
}
