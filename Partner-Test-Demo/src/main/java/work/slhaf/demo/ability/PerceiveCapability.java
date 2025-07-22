package work.slhaf.demo.ability;


import work.slhaf.partner.api.capability.annotation.Capability;

@Capability(value = "perceive")
public interface PerceiveCapability {
    String getUser(String id);
    String addUser(String userInfo, String platform, String userNickName);
    void updateUser(String user);
}
